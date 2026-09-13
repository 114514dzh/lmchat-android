package im.lilmouse.chat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 前台拉取：打开 App 即触发一次纯 HTTP 拉取+解密+入库，
 * 不依赖常驻服务/WebSocket 存活（解决能发不能收）。
 */
public class Sync {
    public static void on(Context c) {
        P.POOL.execute(new Runnable() { public void run() {
            try { pull(c); } catch (Throwable t) {}
        }});
    }

    private static void pull(Context c) throws Exception {
        Db db = Db.get(c);
        String last = db.kvGetStr("lastEnv");
        long after = last == null ? 0 : Long.parseLong(last);
        List<Long> acked = new ArrayList<Long>();
        while (true) {
            List<Api.Env> envs = Api.fetch(P.server(c), P.auth(c), after);
            if (envs.isEmpty()) break;
            for (Api.Env e : envs) {
                if (proc(c, db, e.id, e.payload)) acked.add(e.id);
                after = e.id;
            }
            if (envs.size() < 100) break;
        }
        if (!acked.isEmpty()) Api.ack(P.server(c), P.auth(c), acked);
    }

    /** A3: 唯一接收处理器入口(前台Sync与常驻服务共用) */
    public static boolean process(Context c, long id, String payload) {
        return proc(c, Db.get(c), id, payload);
    }
    public static void pullAll(Context c) { try { pull(c); } catch (Throwable t) {} }

    private static boolean proc(Context c, Db db, long id, String payloadB64) {
        try {
            byte[] raw = P.unb64(payloadB64);
            if (raw.length < 34) return false;
            String sender = new String(P.slice(raw, 0, 32), "UTF-8");
            int t = raw[32] & 0xff;
            byte[] msg = P.slice(raw, 33, raw.length - 33);

            if (t == Crypto.ENV_GROUP) {
                byte[] pt = Crypto.groupOpen(c, sender, msg);
                JsonObject o = Json.parse(new String(pt, "UTF-8")).asObject();
                String gty = o.getString("ty", "");
                if (!"gmsg".equals(gty) && !"img".equals(gty) && !"recall".equals(gty) && !"gmeta".equals(gty)) return false;
                String gid = o.getString("gid", "");
                String gname = o.getString("gname", "");
                String members = membersOf(o);
                Db.Conv exG = db.convGet(gid);
                if (exG == null) db.convEnsure(gid, 1, gname, members);
                else if ((exG.name == null || !exG.name.equals(gname))
                        || (exG.members == null ? members.length() > 0 : !exG.members.equals(members)))
                    db.convMeta(gid, gname, members);
                if ("gmeta".equals(gty)) {
                    G.handleGmeta(c, o);
                    db.bumpLast(String.valueOf(id));
                    Chat.poke();
                    return true;
                }
                if ("recall".equals(gty)) {
                    db.recallByMid(gid, o.getString("mid", ""), "对方撤回了一条消息");
                    db.bumpLast(String.valueOf(id));
                    Chat.poke();
                    return true;
                }
                if ("img".equals(gty)) {
                    String im = o.getString("mid", "");
                    if (!im.isEmpty() && db.hasMid(gid, im)) {
                        db.bumpLast(String.valueOf(id));
                        Chat.poke();
                        return true;
                    }
                    byte[] key = P.unb64(o.getString("k", ""));
                    byte[] iv = P.unb64(o.getString("iv", ""));
                    byte[] ct = Api.getBlob(P.server(c), P.auth(c), o.getString("bid", ""));
                    byte[] img = Crypto.aesDec(key, iv, ct);
                    File dir = new File(c.getFilesDir(), "imgs");
                    if (!dir.exists()) dir.mkdirs();
                    File f = new File(dir, System.currentTimeMillis() + "_" + o.getString("n", "img"));
                    FileOutputStream fo = new FileOutputStream(f);
                    fo.write(img);
                    fo.close();
                    db.msgAdd(gid, false, "img", "[图片]", f.getAbsolutePath(),
                            System.currentTimeMillis(), sender, o.getString("mid", ""));
                    {String _q=quoteOf(o); if(_q.length()>0) db.setQuoteByMid(gid, o.getString("mid",""), _q);}
                    notify(c, gid, gname, "[图片]");
                    db.bumpLast(String.valueOf(id));
                    Chat.poke();
                    return true;
                }
                db.msgAdd(gid, false, "txt", o.getString("b", ""), null,
                        System.currentTimeMillis(), sender, o.getString("mid", ""));
                {String _q=quoteOf(o); if(_q.length()>0) db.setQuoteByMid(gid, o.getString("mid",""), _q);}
                notify(c, gid, gname, o.getString("b", ""));
                db.bumpLast(String.valueOf(id));
                Chat.poke();
                return true;
            }

            byte[] pt = Crypto.open1v1(c, sender, msg);
            JsonObject o = Json.parse(new String(pt, "UTF-8")).asObject();
            String ty = o.getString("ty", "");
            String disp = db.contactName(sender);
            if (disp == null || disp.length() == 0) disp = sender.substring(0, 8);

            if ("recall".equals(ty)) {
                db.recallByMid(sender, o.getString("mid", ""), "对方撤回了一条消息");
                db.bumpLast(String.valueOf(id));
                Chat.poke();
                return true;
            }
            if ("greq".equals(ty)) {
                G.handleGreq(c, o);
                db.bumpLast(String.valueOf(id));
                Chat.poke();
                return true;
            }
            if ("gkick".equals(ty)) {
                G.handleGkick(c, o);
                db.bumpLast(String.valueOf(id));
                Chat.poke();
                return true;
            }
            if ("gdist".equals(ty)) {
                String gid = o.getString("gid", "");
                Crypto.groupProcess(c, sender, P.unb64(o.getString("dist", "")));
                db.convEnsure(gid, 1, o.getString("gname", ""), membersOf(o));
                String gowner = o.getString("owner", "");
                if (gowner.length() > 0) db.convRole(gid, gowner, null);
                db.bumpLast(String.valueOf(id));
                Chat.poke();
                return true;
            }

            db.convEnsure(sender, 0, disp, sender);
            if ("img".equals(ty)) {
                byte[] key = P.unb64(o.getString("k", ""));
                byte[] iv = P.unb64(o.getString("iv", ""));
                byte[] ct = Api.getBlob(P.server(c), P.auth(c), o.getString("bid", ""));
                byte[] img = Crypto.aesDec(key, iv, ct);
                File dir = new File(c.getFilesDir(), "imgs");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, System.currentTimeMillis() + "_" + o.getString("n", "img"));
                FileOutputStream fo = new FileOutputStream(f);
                fo.write(img);
                fo.close();
                db.msgAdd(sender, false, "img", "[图片]", f.getAbsolutePath(),
                        System.currentTimeMillis(), sender, o.getString("mid", ""));
                {String _q=quoteOf(o); if(_q.length()>0) db.setQuoteByMid(sender, o.getString("mid",""), _q);}
                notify(c, sender, disp, "[图片]");
            } else {
                db.msgAdd(sender, false, "txt", o.getString("b", ""), null,
                        System.currentTimeMillis(), sender, o.getString("mid", ""));
                {String _q=quoteOf(o); if(_q.length()>0) db.setQuoteByMid(sender, o.getString("mid",""), _q);}
                notify(c, sender, disp, o.getString("b", ""));
            }
            db.bumpLast(String.valueOf(id));
            Chat.poke();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String quoteOf(JsonObject o) {
        try { JsonValue r = o.get("reply"); if (r == null || !r.isObject()) return "";
            return r.asObject().getString("b", ""); } catch (Throwable t) { return ""; }
    }

    private static String membersOf(JsonObject o) {
        JsonValue v = o.get("members");
        JsonArray a = v == null ? new JsonArray() : v.asArray();
        StringBuilder sb = new StringBuilder();
        for (JsonValue x : a) {
            if (sb.length() > 0) sb.append(",");
            sb.append(x.asString());
        }
        return sb.toString();
    }

    @SuppressWarnings("deprecation")
    private static void notify(Context c, String conv, String title, String body) {
        Chat.poke();
        if (conv.equals(Chat.openConv)) return;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(c, MsgService.CH_MSG)
                    : new Notification.Builder(c);
            Intent it = new Intent(c, Chat.class);
            it.putExtra("conv", conv);
            PendingIntent pi = PendingIntent.getActivity(c, conv.hashCode(), it, PendingIntent.FLAG_IMMUTABLE);
            b.setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle(title).setContentText(body).setContentIntent(pi).setAutoCancel(true);
            nm.notify(conv.hashCode(), b.build());
        } catch (Throwable t) {}
    }
}
