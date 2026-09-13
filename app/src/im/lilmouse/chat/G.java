package im.lilmouse.chat;

import android.content.Context;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 群目录 & 入群/群管理协议：
 *   publish 群到目录 → 搜到 → reqJoin(1v1群主) → 群主 handleGreq 广播 gmeta(add)
 *   老成员各自把新 dist 发给新人；群主/管理员可踢人(全员轮换密钥)、设管理员、改名、开关目录。
 *   gmeta/成员名册统一携带 owner；members 字段为"规范名册"(含群主与本人)。
 */
public class G {

    private static JsonArray arr(String csv) {
        JsonArray a = new JsonArray();
        for (String m : csv.split(",")) { String t = m.trim(); if (t.length() == 32) a.add(t); }
        return a;
    }
    private static List<String> listOf(String csv) {
        List<String> out = new ArrayList<String>();
        for (String m : csv.split(",")) { String t = m.trim(); if (t.length() == 32) out.add(t); }
        return out;
    }
    private static String csvOf(List<String> xs) {
        StringBuilder sb = new StringBuilder();
        for (String x : xs) { if (sb.length() > 0) sb.append(","); sb.append(x); }
        return sb.toString();
    }
    private static List<String> except(Context c, String roster, String... excl) {
        String self = P.acct(c);
        List<String> out = new ArrayList<String>();
        outer:
        for (String m : listOf(roster)) {
            for (String e : excl) if (m.equals(e)) continue outer;
            if (!m.equals(self)) out.add(m);
        }
        return out;
    }

    public static String rosterFull(Context c, String gid) {
        Db db = Db.get(c);
        String self = P.acct(c);
        StringBuilder sb = new StringBuilder(self);
        Db.Conv cv = db.convGet(gid);
        if (cv != null && cv.members != null) for (String m : cv.members.split(",")) {
            String t = m.trim();
            if (t.length() == 32 && !t.equals(self)) { if (sb.indexOf(t) < 0) { sb.append(",").append(t); } }
        }
        return sb.toString();
    }

    // ---------- 目录 ----------
    public static void publish(Context c, String gid, String name) throws Exception {
        JsonObject o = new JsonObject();
        o.add("gid", gid).add("name", name);
        Api.post(P.server(c) + "/v1/groups/reg", P.auth(c), o.toString());
    }
    public static void unpublish(Context c, String gid) throws Exception {
        JsonObject o = new JsonObject();
        o.add("gid", gid);
        Api.post(P.server(c) + "/v1/groups/del", P.auth(c), o.toString());
    }
    public static List<String[]> search(Context c, String q) throws Exception {
        List<String[]> out = new ArrayList<String[]>();
        String s = Api.get(P.server(c) + "/v1/groups/search?q=" + java.net.URLEncoder.encode(q, "UTF-8"), P.auth(c));
        JsonArray arr = Json.parse(s).asObject().get("groups").asArray();
        for (int i = 0; i < arr.size(); i++) {
            JsonObject g = arr.get(i).asObject();
            out.add(new String[]{g.getString("gid", ""), g.getString("name", ""), g.getString("owner", "")});
        }
        return out;
    }

    // ---------- 入群 ----------
    public static void reqJoin(Context c, String gid, String gname, String owner) throws Exception {
        Crypto.ensureSession(c, owner);
        JsonObject o = new JsonObject();
        o.add("ty", "greq").add("gid", gid).add("gname", gname).add("myid", P.acct(c)).add("myname", P.name(c));
        byte[] env = Crypto.seal(c, owner, o.toString().getBytes("UTF-8"));
        List<String> ds = new ArrayList<String>();
        ds.add(owner); List<String> ps = new ArrayList<String>(); ps.add(P.b64(env));
        Api.send(P.server(c), P.auth(c), ds, ps);
    }

    /** 1v1 把(新)dist 发给某成员；gdist 携带 owner 便于成员记群主 */
    private static void sendDistTo(Context c, String gid, String target, String roster, byte[] dist) throws Exception {
        Db.Conv cv = Db.get(c).convGet(gid);
        JsonObject gd = new JsonObject();
        gd.add("ty", "gdist").add("gid", gid).add("gname", cv == null ? "" : cv.name);
        if (cv != null && cv.owner != null) gd.add("owner", cv.owner);
        gd.add("members", arr(roster)).add("dist", P.b64(dist));
        byte[] env = Crypto.seal(c, target, gd.toString().getBytes("UTF-8"));
        List<String> ds = new ArrayList<String>(); ds.add(target);
        List<String> ps = new ArrayList<String>(); ps.add(P.b64(env));
        Api.send(P.server(c), P.auth(c), ds, ps);
    }

    /** 把 gmeta 用群钥加密广播给指定接收者(自己、被排除者不会收到) */
    private static void broadcastGmeta(Context c, String gid, JsonObject g, List<String> recipients) throws Exception {
        if (recipients.isEmpty()) return;
        byte[] sealed = Crypto.groupSeal(c, gid, g.toString().getBytes("UTF-8"));
        byte[] me = P.acct(c).getBytes("UTF-8");
        byte[] envB = new byte[33 + sealed.length];
        System.arraycopy(me, 0, envB, 0, 32);
        envB[32] = (byte) Crypto.ENV_GROUP;
        System.arraycopy(sealed, 0, envB, 33, sealed.length);
        String env = P.b64(envB);
        List<String> ps = new ArrayList<String>();
        for (String m : recipients) ps.add(env);
        Api.send(P.server(c), P.auth(c), recipients, ps);
    }

    // ---------- 群主收到加群申请 ----------
    public static void handleGreq(Context c, JsonObject o) {
        try {
            String gid = o.getString("gid", "");
            String joiner = o.getString("myid", "");
            String gname = o.getString("gname", "");
            if (gid.length() == 0 || joiner.length() == 0) return;
            Db db = Db.get(c);
            if (db.convGet(gid) == null) return;
            String roster = rosterFull(c, gid);
            if (!roster.contains(joiner)) roster = roster + "," + joiner;
            db.convMeta(gid, gname, csvOf(except(c, roster)));   // 除自己外
            JsonObject g = new JsonObject();
            g.add("ty", "gmeta").add("gid", gid).add("gname", gname).add("op", "add").add("target", joiner);
            g.add("members", arr(roster)).add("ts", System.currentTimeMillis());
            broadcastGmeta(c, gid, g, except(c, roster, joiner));
            // G1: 群主轮换自己的链, 分发给【全员】(老成员也要能解, 不只新人)
            distributeSelf(c, gid, roster);
        } catch (Throwable t) {}
    }

    // ---------- 成员处理 gmeta ----------
    public static void handleGmeta(Context c, JsonObject o) {
        try {
            String gid = o.getString("gid", "");
            Db db = Db.get(c);
            Db.Conv cv = db.convGet(gid);
            if (cv == null) return;
            String gname = o.getString("gname", cv.name);
            String roster = rosterOf(o.getString("members", ""));
            String self = P.acct(c);
            String owner = o.getString("owner", "");
            db.convMeta(gid, gname, csvOf(except(c, roster)));
            if (owner.length() > 0) db.convRole(gid, owner, null);
            String op = o.getString("op", "");
            if ("add".equals(op)) {
                String target = o.getString("target", "");
                if (target.length() == 0) return;
                // G1: 轮换后分发给全体成员(含自己外的所有人)
                distributeSelf(c, gid, roster);
            } else if ("remove".equals(op)) {
                String target = o.getString("target", "");
                if (target.equals(self)) { db.convDelete(gid); return; }
                // 轮换自己的密钥并分发给所有留下的成员(让被踢者失去可读性)
                byte[] dist = Crypto.groupCreate(c, gid).serialize();
                for (String m : except(c, roster)) sendDistTo(c, gid, m, roster, dist);
            } else if ("role".equals(op)) {
                String target = o.getString("target", "");
                boolean admin = o.getBoolean("admin", false);
                if (target.length() == 0 || target.equals(self)) return;
                List<String> adm = new ArrayList<String>();
                if (cv.admins != null) adm = listOf(cv.admins);
                if (admin) { if (!adm.contains(target)) adm.add(target); }
                else adm.remove(target);
                db.convRole(gid, owner.length() > 0 ? owner : cv.owner, csvOf(adm));
            } else if ("rename".equals(op)) {
                String nn = o.getString("name", "");
                if (nn.length() > 0) db.convMeta(gid, nn, null == null ? cv.members : cv.members);
            }
        } catch (Throwable t) {}
    }

    /** 被踢通知(1v1)：删除本机该群 */
    public static void handleGkick(Context c, JsonObject o) {
        try { Db.get(c).convDelete(o.getString("gid", "")); } catch (Throwable t) {}
    }

    // ---------- 群主/管理员动作 ----------
    public static boolean isOwner(Context c, String gid) {
        Db.Conv cv = Db.get(c).convGet(gid);
        return cv != null && P.acct(c).equals(cv.owner);
    }
    public static boolean isAdmin(Context c, String gid) {
        Db.Conv cv = Db.get(c).convGet(gid);
        if (cv == null || cv.admins == null) return false;
        for (String m : cv.admins.split(",")) if (P.acct(c).equals(m.trim())) return true;
        return false;
    }

    /** 踢人：群主或管理员；被踢者若为群主/管理员则仅群主可踢 */
    public static void kickMember(Context c, String gid, String target) {
        try {
            Db.Conv cv = Db.get(c).convGet(gid);
            if (cv == null) return;
            String owner = cv.owner;
            String self = P.acct(c);
            boolean actorOwner = self.equals(owner);
            boolean actorAdmin = isAdmin(c, gid);
            boolean targetIsAdmin = cv.admins != null && contains(cv.admins, target);
            if (!actorOwner && !actorAdmin) return;
            if (!actorOwner && (target.equals(owner) || targetIsAdmin)) return;
            String roster = rosterFull(c, gid);
            if (!roster.contains(target)) return;
            String newRoster = removeAcct(roster, target);
            // 本机更新
            db(c).convMeta(gid, cv.name, csvOf(except(c, newRoster)));
            List<String> remain = except(c, newRoster); // 其他留下成员
            // 用旧链广播 remove(此时留下者仍能解)
            JsonObject g = new JsonObject();
            g.add("ty", "gmeta").add("gid", gid).add("gname", cv.name).add("op", "remove").add("target", target);
            g.add("members", arr(newRoster)).add("owner", owner).add("ts", System.currentTimeMillis());
            broadcastGmeta(c, gid, g, remain);
            // 轮换自己密钥分发给留下的成员
            byte[] dist = Crypto.groupCreate(c, gid).serialize();
            for (String m : remain) sendDistTo(c, gid, m, newRoster, dist);
            // 通知被踢者删除本地会话
            Crypto.ensureSession(c, target);
            JsonObject k = new JsonObject();
            k.add("ty", "gkick").add("gid", gid).add("gname", cv.name);
            byte[] env = Crypto.seal(c, target, k.toString().getBytes("UTF-8"));
            List<String> ds = new ArrayList<String>(); ds.add(target);
            List<String> ps = new ArrayList<String>(); ps.add(P.b64(env));
            Api.send(P.server(c), P.auth(c), ds, ps);
        } catch (Throwable t) {}
    }

    /** 设置/撤销管理员：仅群主 */
    public static void promote(Context c, String gid, String target, boolean admin) {
        try {
            if (!isOwner(c, gid)) return;
            Db.Conv cv = Db.get(c).convGet(gid);
            if (cv == null) return;
            List<String> adm = new ArrayList<String>();
            if (cv.admins != null) adm = listOf(cv.admins);
            if (admin) { if (!adm.contains(target)) adm.add(target); }
            else adm.remove(target);
            String admins = csvOf(adm);
            db(c).convRole(gid, cv.owner, admins);
            String roster = rosterFull(c, gid);
            JsonObject g = new JsonObject();
            g.add("ty", "gmeta").add("gid", gid).add("gname", cv.name).add("op", "role").add("target", target);
            g.add("admin", admin).add("owner", cv.owner).add("members", arr(roster)).add("ts", System.currentTimeMillis());
            broadcastGmeta(c, gid, g, except(c, roster));
        } catch (Throwable t) {}
    }

    /** 改群名：仅群主；自动同步目录 */
    public static void rename(Context c, String gid, String newName) {
        try {
            if (!isOwner(c, gid)) return;
            Db.Conv cv = Db.get(c).convGet(gid);
            if (cv == null) return;
            db(c).convMeta(gid, newName, cv.members);
            try { publish(c, gid, newName); } catch (Throwable t) {}
            String roster = rosterFull(c, gid);
            JsonObject g = new JsonObject();
            g.add("ty", "gmeta").add("gid", gid).add("gname", newName).add("op", "rename").add("name", newName);
            g.add("owner", cv.owner).add("members", arr(roster)).add("ts", System.currentTimeMillis());
            broadcastGmeta(c, gid, g, except(c, roster));
        } catch (Throwable t) {}
    }

    /** 轮换自己的群链并分发给除自己外的全体在册成员 */
    private static void distributeSelf(Context c, String gid, String roster) {
        try {
            byte[] dist = Crypto.groupCreate(c, gid).serialize();
            for (String m : except(c, roster)) sendDistTo(c, gid, m, roster, dist);
        } catch (Throwable t) {}
    }

    private static Db db(Context c) { return Db.get(c); }
    private static boolean contains(String csv, String acct) {
        for (String m : csv.split(",")) if (m.trim().equals(acct)) return true;
        return false;
    }
    private static String removeAcct(String csv, String acct) {
        StringBuilder sb = new StringBuilder();
        for (String m : csv.split(",")) { String t = m.trim(); if (t.length() == 32 && !t.equals(acct)) { if (sb.length() > 0) sb.append(","); sb.append(t); } }
        return sb.toString();
    }
    private static String rosterOf(String csv) {
        StringBuilder sb = new StringBuilder();
        for (String m : csv.split(",")) { String t = m.trim(); if (t.length() == 32) { if (sb.length() > 0) sb.append(","); sb.append(t); } }
        return sb.toString();
    }
}
