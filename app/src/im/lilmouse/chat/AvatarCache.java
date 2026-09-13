package im.lilmouse.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AvatarCache {
    private static final HashMap<String, Bitmap> MEM = new HashMap<String, Bitmap>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    public interface Cb { void on(Bitmap b); }

    private static File file(Context c, String acc) {
        File dir = new File(c.getFilesDir(), "avatars");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, acc + ".jpg");
    }

    public static Bitmap cached(Context c, String acc) {
        if (acc == null || acc.length() == 0) return null;
        Bitmap m = MEM.get(acc);
        if (m != null) return m;
        File f = file(c, acc);
        if (f.exists()) {
            Bitmap b = UI.circle(UI.decodeFile(f.getAbsolutePath(), 128));
            if (b != null) MEM.put(acc, b);
            return b;
        }
        return null;
    }

    /** 拉取并解密对方头像；cb 回主线程 */
    public static void get(final Context c, final String acc, final Cb cb) {
        final Bitmap hit = cached(c, acc);
        if (hit != null) { cb.on(hit); return; }
        P.POOL.execute(new Runnable() { public void run() {
            try {
                String prof = Api.get(P.server(c) + "/v1/directory?ids=" + acc, P.auth(c));
                JsonArray ps = Json.parse(prof).asObject().get("profiles").asArray();
                if (ps.size() == 0) { post(cb, null); return; }
                String field = ps.get(0).asObject().getString("avatar", "");
                Bitmap b = fetchAndStore(c, acc, field);
                post(cb, b);
            } catch (Exception e) { post(cb, null); }
        }});
    }

    private static Bitmap fetchAndStore(Context c, String acc, String field) throws Exception {
        if (field == null || field.length() < 34) return null;
        int dash = field.indexOf('-');
        if (dash <= 0) return null;
        String bid = field.substring(0, dash);
        byte[] key = P.unb64(field.substring(dash + 1));
        byte[] raw = Api.getBlob(P.server(c), P.auth(c), bid);
        if (raw.length < 13) return null;
        byte[] iv = P.slice(raw, 0, 12);
        byte[] ct = P.slice(raw, 12, raw.length - 12);
        Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
        ci.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] img = ci.doFinal(ct);
        FileOutputStream fo = new FileOutputStream(file(c, acc));
        fo.write(img);
        fo.close();
        Bitmap b = UI.circle(UI.decodeFile(file(c, acc).getAbsolutePath(), 128));
        if (b != null) MEM.put(acc, b);
        return b;
    }

    private static void post(final Cb cb, final Bitmap b) {
        MAIN.post(new Runnable() { public void run() { cb.on(b); } });
    }

    /** 加密上传头像，返回 profile 的 avatar 字段 (<bid>-<key>) */
    public static String upload(Context c, byte[] img) throws Exception {
        byte[] key = new byte[16];
        byte[] iv = new byte[12];
        java.security.SecureRandom r = new java.security.SecureRandom();
        r.nextBytes(key);
        r.nextBytes(iv);
        Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
        ci.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ct = ci.doFinal(img);
        byte[] payload = P.concat(iv, ct);
        String bid = Api.putBlob(P.server(c), P.auth(c), payload);
        return bid + "-" + P.b64(key);
    }

    public static void clearMemory(String acc) { MEM.remove(acc); }

    public static void invalidate(Context c, String acc) {
        MEM.remove(acc);
        File f = file(c, acc);
        if (f.exists()) f.delete();
    }

    /** 服务器 blob 30 天 TTL：定期重传自己的头像保活 */
    public static void keepAlive(final Context c) {
        final String field = P.sp(c).getString("myAvatarField", "");
        if (field.length() == 0) return;
        long last = P.sp(c).getLong("avUpTs", 0);
        if (System.currentTimeMillis() - last < 7L * 24 * 3600 * 1000) return;
        File f = file(c, P.acct(c));
        if (!f.exists()) return;
        P.POOL.execute(new Runnable() { public void run() {
            try {
                byte[] img = readAll(f);
                String nf = upload(c, img);
                saveProfileField(c, nf);
            } catch (Throwable t) {}
        }});
    }

    public static void saveProfileField(Context c, String field) throws Exception {
        P.sp(c).edit().putString("myAvatarField", field).putLong("avUpTs", System.currentTimeMillis()).commit();
        com.eclipsesource.json.JsonObject o = new com.eclipsesource.json.JsonObject();
        o.add("displayName", P.name(c));
        o.add("avatar", field);
        Api.post(P.server(c) + "/v1/profile", P.auth(c), o.toString());
    }

    private static byte[] readAll(File f) throws Exception {
        java.io.FileInputStream is = new java.io.FileInputStream(f);
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return bo.toByteArray();
    }
}
