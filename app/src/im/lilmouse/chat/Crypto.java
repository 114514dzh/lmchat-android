package im.lilmouse.chat;

import android.content.Context;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.InvalidKeyIdException;
import org.signal.libsignal.protocol.SessionBuilder;
import org.signal.libsignal.protocol.SessionCipher;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.UsePqRatchet;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.groups.GroupCipher;
import org.signal.libsignal.protocol.groups.GroupSessionBuilder;
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord;
import org.signal.libsignal.protocol.message.CiphertextMessage;
import org.signal.libsignal.protocol.message.PreKeySignalMessage;
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage;
import org.signal.libsignal.protocol.message.SignalMessage;
import org.signal.libsignal.protocol.state.IdentityKeyStore;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.signal.libsignal.protocol.state.KyberPreKeyStore;
import org.signal.libsignal.protocol.state.PreKeyBundle;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.PreKeyStore;
import org.signal.libsignal.protocol.state.SessionRecord;
import org.signal.libsignal.protocol.state.SessionStore;
import org.signal.libsignal.protocol.state.SignalProtocolStore;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyStore;
import org.signal.libsignal.protocol.util.KeyHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Crypto {
    public static final int DEV = 1;
    public static final int ENV_PAIR = 1;
    public static final int ENV_PREKEY = 2;
    public static final int ENV_GROUP = 3;

    public static SignalProtocolAddress addr(String id) { return new SignalProtocolAddress(id, DEV); }

    // 签名预密钥 blob 打包：0xA3|spkSig(64)|spkPub(33)|kyberSig(64)|kyberPub
    private static byte[] packSpk(byte[] spkSig, byte[] spkPub, byte[] ksig, byte[] kpub) {
        return P.concat(new byte[]{(byte) 0xA3}, P.concat(spkSig, P.concat(spkPub, P.concat(ksig, kpub))));
    }

    /** 老账号(≤0.6)启动时补发带 kyber 的新签名预密钥 */
    public static void migratePrekeys(Context c) throws Exception {
        Db db = Db.get(c);
        if (!P.hasAccount(c) || db.kvGet("kyber:1") != null) return;
        IdentityKeyPair ikp = new IdentityKeyPair(db.kvGet("idpair"));
        ECKeyPair spkKeys = ECKeyPair.generate();
        byte[] sig = ikp.getPrivateKey().calculateSignature(spkKeys.getPublicKey().serialize());
        int id = 2;
        db.kvSet("spk:" + id, new SignedPreKeyRecord(id, System.currentTimeMillis(), spkKeys, sig).serialize());
        org.signal.libsignal.protocol.kem.KEMKeyPair kk = org.signal.libsignal.protocol.kem.KEMKeyPair.generate(
                org.signal.libsignal.protocol.kem.KEMKeyType.KYBER_1024);
        byte[] kpub = kk.getPublicKey().serialize();
        byte[] ksig = ikp.getPrivateKey().calculateSignature(kpub);
        db.kvSet("kyber:1", new KyberPreKeyRecord(1, System.currentTimeMillis(), kk, ksig).serialize());
        JsonObject o = new JsonObject();
        JsonObject s2 = new JsonObject();
        s2.add("keyId", id);
        s2.add("blob", P.b64(packSpk(sig, spkKeys.getPublicKey().serialize(), ksig, kpub)));
        o.add("signedPrekey", s2);
        Api.post(P.server(c) + "/v1/prekeys", P.auth(c), o.toString());
    }

    // ================= SQLite-backed SignalProtocolStore =================
    public static class Store implements SignalProtocolStore {
        private final Db db;
        public Store(Db db) { this.db = db; }

        public IdentityKeyPair getIdentityKeyPair() { return new IdentityKeyPair(db.kvGet("idpair")); }
        public int getLocalRegistrationId() { return Integer.parseInt(db.kvGetStr("regid")); }

        public IdentityKeyStore.IdentityChange saveIdentity(SignalProtocolAddress a, IdentityKey k) {
            byte[] old = db.kvGet("identity:" + a.getName());
            db.kvSet("identity:" + a.getName(), k.serialize());
            try { if (old != null && new IdentityKey(old).equals(k)) return IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED; } catch (Exception e) {}
            return IdentityKeyStore.IdentityChange.REPLACED_EXISTING;
        }
        public boolean isTrustedIdentity(SignalProtocolAddress a, IdentityKey k, IdentityKeyStore.Direction d) {
            byte[] old = db.kvGet("identity:" + a.getName());
            if (old == null) return true; // TOFU
            try { return new IdentityKey(old).equals(k); } catch (Exception e) { return false; }
        }
        public IdentityKey getIdentity(SignalProtocolAddress a) {
            byte[] b = db.kvGet("identity:" + a.getName());
            if (b == null) return null;
            try { return new IdentityKey(b); } catch (Exception e) { return null; }
        }

        public PreKeyRecord loadPreKey(int id) throws InvalidKeyIdException {
            byte[] b = db.kvGet("prekey:" + id);
            if (b == null) throw new InvalidKeyIdException("no prekey " + id);
            try { return new PreKeyRecord(b); } catch (Exception e) { throw new InvalidKeyIdException(e.toString()); }
        }
        public void storePreKey(int id, PreKeyRecord r) { db.kvSet("prekey:" + id, r.serialize()); }
        public boolean containsPreKey(int id) { return db.kvGet("prekey:" + id) != null; }
        public void removePreKey(int id) { db.kvDel("prekey:" + id); }

        public SignedPreKeyRecord loadSignedPreKey(int id) throws InvalidKeyIdException {
            byte[] b = db.kvGet("spk:" + id);
            if (b == null) throw new InvalidKeyIdException("no spk " + id);
            try { return new SignedPreKeyRecord(b); } catch (Exception e) { throw new InvalidKeyIdException(e.toString()); }
        }
        public void storeSignedPreKey(int id, SignedPreKeyRecord r) { db.kvSet("spk:" + id, r.serialize()); }
        public boolean containsSignedPreKey(int id) { return db.kvGet("spk:" + id) != null; }
        public void removeSignedPreKey(int id) { db.kvDel("spk:" + id); }
        public java.util.List<org.signal.libsignal.protocol.state.SignedPreKeyRecord> loadSignedPreKeys() { return new java.util.ArrayList<org.signal.libsignal.protocol.state.SignedPreKeyRecord>(); }

        public SessionRecord loadSession(SignalProtocolAddress a) {
            byte[] b = db.kvGet("session:" + a.getName());
            if (b == null) return new SessionRecord();
            try { return new SessionRecord(b); } catch (Exception e) { return new SessionRecord(); }
        }
        public List<SessionRecord> loadExistingSessions(List<SignalProtocolAddress> addrs) {
            List<SessionRecord> out = new ArrayList<SessionRecord>();
            for (SignalProtocolAddress a : addrs) if (containsSession(a)) out.add(loadSession(a));
            return out;
        }
        public List<Integer> getSubDeviceSessions(String name) { return new ArrayList<Integer>(); }
        public void storeSession(SignalProtocolAddress a, SessionRecord r) { db.kvSet("session:" + a.getName(), r.serialize()); }
        public boolean containsSession(SignalProtocolAddress a) { return db.kvGet("session:" + a.getName()) != null; }
        public void deleteSession(SignalProtocolAddress a) { db.kvDel("session:" + a.getName()); }
        public void deleteAllSessions(String name) { db.kvDel("session:" + name); }

        public void storeSenderKey(SignalProtocolAddress a, UUID g, SenderKeyRecord r) {
            db.kvSet("sk:" + a.getName() + ":" + g.toString(), r.serialize());
        }
        public SenderKeyRecord loadSenderKey(SignalProtocolAddress a, UUID g) {
            byte[] b = db.kvGet("sk:" + a.getName() + ":" + g.toString());
            if (b == null) return null;
            try { return new SenderKeyRecord(b); } catch (Exception e) { return null; }
        }

        public KyberPreKeyRecord loadKyberPreKey(int id) throws InvalidKeyIdException {
            byte[] b = db.kvGet("kyber:" + id);
            if (b == null) throw new InvalidKeyIdException("no kyber " + id);
            try { return new KyberPreKeyRecord(b); } catch (Exception e) { throw new InvalidKeyIdException(e.toString()); }
        }
        public List<KyberPreKeyRecord> loadKyberPreKeys() {
            List<KyberPreKeyRecord> out = new ArrayList<KyberPreKeyRecord>();
            for (int i = 0; i < 16; i++) {
                byte[] b = db.kvGet("kyber:" + i);
                if (b != null) { try { out.add(new KyberPreKeyRecord(b)); } catch (Exception e) {} }
            }
            return out;
        }
        public void storeKyberPreKey(int id, KyberPreKeyRecord r) { db.kvSet("kyber:" + id, r.serialize()); }
        public boolean containsKyberPreKey(int id) { return db.kvGet("kyber:" + id) != null; }
        public void markKyberPreKeyUsed(int id) {}
    }

    public static Store store(Context c) { return new Store(Db.get(c)); }

    // ================= account =================
    public static void createAccount(Context c, String server, String display) throws Exception {
        server = server.replaceAll("/+$", "");
        Db db = Db.get(c);
        IdentityKeyPair ikp = IdentityKeyPair.generate();
        int regId = KeyHelper.generateRegistrationId(false);
        db.kvSet("idpair", ikp.serialize());
        db.kvSetStr("regid", String.valueOf(regId));

        ECKeyPair spkKeys = ECKeyPair.generate();
        byte[] sig = ikp.getPrivateKey().calculateSignature(spkKeys.getPublicKey().serialize());
        SignedPreKeyRecord spk = new SignedPreKeyRecord(1, System.currentTimeMillis(), spkKeys, sig);
        db.kvSet("spk:1", spk.serialize());

        org.signal.libsignal.protocol.kem.KEMKeyPair kk = org.signal.libsignal.protocol.kem.KEMKeyPair.generate(
                org.signal.libsignal.protocol.kem.KEMKeyType.KYBER_1024);
        byte[] kpub = kk.getPublicKey().serialize();
        byte[] ksig = ikp.getPrivateKey().calculateSignature(kpub);
        db.kvSet("kyber:1", new KyberPreKeyRecord(1, System.currentTimeMillis(), kk, ksig).serialize());
        byte[] spkBlob = packSpk(sig, spkKeys.getPublicKey().serialize(), ksig, kpub);

        JsonArray ot = new JsonArray();
        for (int i = 1; i <= 100; i++) {
            PreKeyRecord pk = new PreKeyRecord(i, ECKeyPair.generate());
            db.kvSet("prekey:" + i, pk.serialize());
            JsonObject k = new JsonObject();
            k.add("keyId", i);
            k.add("blob", P.b64(pk.getKeyPair().getPublicKey().serialize()));
            ot.add(k);
        }
        String acct = P.randHex(32);
        String token = P.randHex(64);
        Api.register(server, acct, token, display,
                P.b64(ikp.getPublicKey().serialize()), P.b64(spkBlob), ot);
        P.sp(c).edit()
                .putString("acct", acct)
                .putString("token", token)
                .putString("server", server)
                .putString("name", display)
                .commit();
    }

    // ================= 1v1 session =================
    public static void ensureSession(Context c, String peer) throws Exception {
        Store st = store(c);
        SignalProtocolAddress a = addr(peer);
        if (st.containsSession(a)) return;
        JsonObject b = Json.parse(Api.prekeys(P.server(c), P.auth(c), peer)).asObject();
        IdentityKey idk = new IdentityKey(P.unb64(b.getString("identityKey", "")));
        JsonObject s = b.get("signedPrekey").asObject();
        int spkId = s.getInt("keyId", 1);
        byte[] sblob = P.unb64(s.getString("blob", ""));
        ECPublicKey spkPub;
        byte[] sig = null;
        byte[] kyberSig = new byte[0];
        org.signal.libsignal.protocol.kem.KEMPublicKey kyberPub = null;
        int kyberId = 0;
        if (sblob.length > 0 && (sblob[0] & 0xff) == 0xA3) {
            int p = 1;
            sig = P.slice(sblob, p, 64); p += 64;
            spkPub = new ECPublicKey(P.slice(sblob, p, 33)); p += 33;
            kyberSig = P.slice(sblob, p, 64); p += 64;
            kyberPub = new org.signal.libsignal.protocol.kem.KEMPublicKey(P.slice(sblob, p, sblob.length - p));
            kyberId = 1;
        } else {
            try {
                SignedPreKeyRecord spkr = new SignedPreKeyRecord(sblob); // v1/v2 整record
                spkPub = spkr.getKeyPair().getPublicKey();
                sig = spkr.getSignature();
            } catch (Exception oldFmt) {
                if (sblob.length > 65) {                              // v0.3-0.6 打包(无kyber)
                    sig = P.slice(sblob, 0, 64);
                    spkPub = new ECPublicKey(P.slice(sblob, 64, sblob.length - 64));
                } else throw oldFmt;
            }
        }
        if (kyberPub == null)
            throw new Exception("对方账号缺少新版预密钥，请让对方打开一次最新版 App（自动补发）后再添加");
        int otId = 0;
        ECPublicKey otKey = null;
        JsonValue otv = b.get("oneTimePrekey");
        if (otv != null && otv.isObject()) {
            byte[] oblob = P.unb64(otv.asObject().getString("blob", ""));
            try {
                PreKeyRecord pkr = new PreKeyRecord(oblob);
                otId = pkr.getId();
                otKey = pkr.getKeyPair().getPublicKey();
            } catch (Exception oldFmt) {
                otKey = new ECPublicKey(oblob);
                otId = otv.asObject().getInt("keyId", 0);
            }
        }
        PreKeyBundle bundle = new PreKeyBundle(0, DEV, otId, otKey,
                spkId, spkPub, sig, idk, kyberId, kyberPub, kyberSig);
        new SessionBuilder(st, a).process(bundle, UsePqRatchet.NO);
    }

    /** envelope = 32B 发送者hex + 1B 类型 + 密文 */
    public static byte[] seal(Context c, String peer, byte[] pt) throws Exception {
        ensureSession(c, peer);
        CiphertextMessage m = new SessionCipher(store(c), addr(peer)).encrypt(pt);
        byte type = (byte) (m.getType() == CiphertextMessage.PREKEY_TYPE ? ENV_PREKEY : ENV_PAIR);
        byte[] body = m.serialize();
        byte[] me = P.acct(c).getBytes("UTF-8");
        byte[] env = new byte[33 + body.length];
        System.arraycopy(me, 0, env, 0, 32);
        env[32] = type;
        System.arraycopy(body, 0, env, 33, body.length);
        return env;
    }

    public static byte[] open1v1(Context c, String sender, byte[] msg) throws Exception {
        SessionCipher cipher = new SessionCipher(store(c), addr(sender));
        try {
            return cipher.decrypt(new PreKeySignalMessage(msg), UsePqRatchet.NO);
        } catch (Exception pre) {
            return cipher.decrypt(new SignalMessage(msg));
        }
    }

    // ================= group (sender key) =================
    public static SenderKeyDistributionMessage groupCreate(Context c, String gid) {
        return new GroupSessionBuilder(store(c)).create(addr(P.acct(c)), UUID.fromString(gid));
    }
    public static void groupProcess(Context c, String sender, byte[] dist) throws Exception {
        new GroupSessionBuilder(store(c)).process(addr(sender), new SenderKeyDistributionMessage(dist));
    }
    public static byte[] groupSeal(Context c, String gid, byte[] pt) throws Exception {
        return new GroupCipher(store(c), addr(P.acct(c))).encrypt(UUID.fromString(gid), pt).serialize();
    }
    public static byte[] groupOpen(Context c, String sender, byte[] msg) throws Exception {
        return new GroupCipher(store(c), addr(sender)).decrypt(msg);
    }

    // ================= AES-GCM (图片) =================
    public static byte[] aesEnc(byte[] key, byte[] iv, byte[] pt) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return cipher.doFinal(pt);
    }
    public static byte[] aesDec(byte[] key, byte[] iv, byte[] ct) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return cipher.doFinal(ct);
    }
}
