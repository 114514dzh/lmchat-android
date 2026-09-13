package im.lilmouse.chat;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class Db extends SQLiteOpenHelper {
    private static Db I;
    public static synchronized Db get(Context c) {
        if (I == null) I = new Db(c.getApplicationContext());
        return I;
    }
    private Db(Context c) { super(c, "lm.db", null, 7); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE kv(k TEXT PRIMARY KEY, v BLOB)");
        db.execSQL("CREATE TABLE convs(id TEXT PRIMARY KEY, type INTEGER, name TEXT, members TEXT, last TEXT, ts INTEGER, owner TEXT, admins TEXT)");
        db.execSQL("CREATE TABLE msgs(id INTEGER PRIMARY KEY AUTOINCREMENT, conv TEXT, out INTEGER, ty TEXT, body TEXT, local TEXT, ts INTEGER, read INTEGER DEFAULT 1, sender TEXT DEFAULT '', msgid TEXT DEFAULT '', qb TEXT DEFAULT '')");
        db.execSQL("CREATE INDEX idx_msgs ON msgs(conv, id)");
        db.execSQL("CREATE TABLE contacts(id TEXT PRIMARY KEY, name TEXT)");
        db.execSQL("CREATE TABLE drafts(conv TEXT PRIMARY KEY, text TEXT, ts INTEGER)");
    }
    /** 每次打开数据库都执行(弥补升级遗漏/异常中断导致表缺失) */
    @Override public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        try { db.execSQL("CREATE TABLE IF NOT EXISTS drafts(conv TEXT PRIMARY KEY, text TEXT, ts INTEGER)"); } catch (Throwable t) {}
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) db.execSQL("ALTER TABLE msgs ADD COLUMN read INTEGER DEFAULT 1");
        if (oldV < 3) db.execSQL("ALTER TABLE msgs ADD COLUMN sender TEXT DEFAULT ''");
        if (oldV < 4) db.execSQL("ALTER TABLE msgs ADD COLUMN msgid TEXT DEFAULT ''");
        if (oldV < 5) {
            db.execSQL("ALTER TABLE convs ADD COLUMN owner TEXT");
            db.execSQL("ALTER TABLE convs ADD COLUMN admins TEXT");
        }
        if (oldV < 6) db.execSQL("ALTER TABLE msgs ADD COLUMN qb TEXT DEFAULT ''");
        if (oldV < 7) {
            db.execSQL("ALTER TABLE msgs ADD COLUMN mime TEXT DEFAULT ''");
            db.execSQL("CREATE TABLE IF NOT EXISTS drafts(conv TEXT PRIMARY KEY, text TEXT, ts INTEGER)");
        }
        // 兜底：确保草稿表存在(升级遗漏等)
        db.execSQL("CREATE TABLE IF NOT EXISTS drafts(conv TEXT PRIMARY KEY, text TEXT, ts INTEGER)");
    }

    private SQLiteDatabase w() { return getWritableDatabase(); }


    /** 一键已读：只标记全部已读, 绝不再删行(曾因误删致聊天记录丢失) */
    public synchronized int repairAll() {
        ContentValues cv = new ContentValues();
        cv.put("read", 1);
        try { return w().update("msgs", cv, "out=0", null); } catch (Exception e) { return 0; }
    }

    public synchronized void reset(Context c) {
        I.close();
        I = null;
        c.deleteDatabase("lm.db");
    }

    // ---------- kv ----------
    public synchronized byte[] kvGet(String k) {
        Cursor c = w().rawQuery("SELECT v FROM kv WHERE k=?", new String[]{k});
        byte[] r = null;
        if (c.moveToFirst() && !c.isNull(0)) r = c.getBlob(0);
        c.close();
        return r;
    }
    public synchronized void kvSet(String k, byte[] v) {
        ContentValues cv = new ContentValues();
        cv.put("k", k);
        cv.put("v", v);
        w().insertWithOnConflict("kv", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }
    public synchronized void kvDel(String k) { w().delete("kv", "k=?", new String[]{k}); }
    public synchronized String kvGetStr(String k) {
        byte[] b = kvGet(k);
        return b == null ? null : new String(b);
    }
    public synchronized void kvSetStr(String k, String v) { kvSet(k, v.getBytes()); }

    // ---------- contacts ----------
    public synchronized void contactSet(String id, String name) {
        ContentValues cv = new ContentValues();
        cv.put("id", id);
        cv.put("name", name);
        w().insertWithOnConflict("contacts", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }
    public synchronized String contactName(String id) {
        Cursor c = w().rawQuery("SELECT name FROM contacts WHERE id=?", new String[]{id});
        String r = null;
        if (c.moveToFirst()) r = c.getString(0);
        c.close();
        return r;
    }

    // ---------- conversations ----------
    public static class Conv { public String id; public int type; public String name; public String members; public String last; public long ts; public int unread; public String owner; public String admins; }
    public static class Msg { public long id; public String conv; public boolean out; public String ty; public String body; public String local; public long ts; public String sender; public String msgid; public String qb; public String mime; }

    public synchronized void convEnsure(String id, int type, String name, String members) {
        ContentValues cv = new ContentValues();
        cv.put("id", id);
        cv.put("type", type);
        cv.put("name", name);
        cv.put("members", members);
        w().insertWithOnConflict("convs", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }
    public synchronized void convRole(String id, String owner, String admins) {
        ContentValues cv = new ContentValues();
        if (owner != null) cv.put("owner", owner);
        if (admins != null) cv.put("admins", admins);
        if (cv.size() > 0) w().update("convs", cv, "id=?", new String[]{id});
    }
    public synchronized void convDelete(String id) {
        w().delete("msgs", "conv=?", new String[]{id});
        w().delete("convs", "id=?", new String[]{id});
    }
    public synchronized void convMeta(String id, String name, String members) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("members", members);
        w().update("convs", cv, "id=?", new String[]{id});
    }
    public synchronized Conv convGet(String id) {
        Cursor c = w().rawQuery("SELECT id,type,name,members,last,ts,owner,admins FROM convs WHERE id=?", new String[]{id});
        Conv r = null;
        if (c.moveToFirst()) r = row2conv(c);
        c.close();
        return r;
    }
    public synchronized List<Conv> convList() {
        List<Conv> out = new ArrayList<Conv>();
        Cursor c = w().rawQuery(
            "SELECT c.id,c.type,c.name,c.members,c.last,c.ts,c.owner,c.admins," +
            "(SELECT COUNT(*) FROM msgs m WHERE m.conv=c.id AND m.out=0 AND m.read=0) " +
            "FROM convs c ORDER BY c.ts DESC", null);
        while (c.moveToNext()) {
            Conv v = row2conv(c);
            v.unread = c.getInt(8); // 列序:id0 type1 name2 members3 last4 ts5 owner6 admins7 unread8
            out.add(v);
        }
        c.close();
        return out;
    }
    private Conv row2conv(Cursor c) {
        Conv v = new Conv();
        v.id = c.getString(0);
        v.type = c.getInt(1);
        v.name = c.getString(2);
        v.members = c.getString(3);
        v.last = c.getString(4);
        v.ts = c.getLong(5);
        if (c.getColumnCount() > 6) { v.owner = c.getString(6); v.admins = c.getString(7); }
        return v;
    }

    // ---------- messages ----------
    public synchronized long msgAdd(String conv, boolean out, String ty, String body, String local, long ts, String sender, String msgid) {
        // 幂等：同 mid 已在库则不再插入（WS推送与轮询可能并发处理同一条）
        if (msgid != null && msgid.length() > 0) {
            Cursor c = w().rawQuery("SELECT id FROM msgs WHERE conv=? AND msgid=? LIMIT 1",
                    new String[]{conv, msgid});
            if (c.moveToFirst()) {
                long eid = c.getLong(0);
                c.close();
                ContentValues c2 = new ContentValues();
                c2.put("last", "img".equals(ty) ? "[图片]" : body);
                c2.put("ts", ts);
                w().update("convs", c2, "id=?", new String[]{conv});
                return eid;
            }
            c.close();
        }
        ContentValues cv = new ContentValues();
        cv.put("conv", conv);
        cv.put("out", out ? 1 : 0);
        cv.put("ty", ty);
        cv.put("body", body == null ? "" : body);
        cv.put("local", local);
        cv.put("ts", ts);
        cv.put("read", out ? 1 : 0);
        cv.put("sender", sender);
        cv.put("msgid", msgid == null ? "" : msgid);
        long id = w().insert("msgs", null, cv);
        cv.clear();
        cv.put("last", "img".equals(ty) ? "[图片]" : body);
        cv.put("ts", ts);
        w().update("convs", cv, "id=?", new String[]{conv});
        return id;
    }

    /** lastEnv 单调递增：并发下只允许前进，防止倒退丢消息 */
    public synchronized void bumpLast(String sid) {
        long id;
        try { id = Long.parseLong(sid); } catch (Exception e) { return; }
        String cur = kvGetStr("lastEnv");
        if (cur == null) { kvSetStr("lastEnv", sid); return; }
        try { if (Long.parseLong(cur) < id) kvSetStr("lastEnv", sid); } catch (Exception e) { kvSetStr("lastEnv", sid); }
    }
    public synchronized void setImgMime(String conv, String mid, String mime) {
        if (mid == null || mid.length() == 0) return;
        ContentValues cv = new ContentValues(); cv.put("mime", mime == null ? "" : mime);
        w().update("msgs", cv, "conv=? AND msgid=?", new String[]{conv, mid});
    }
    public synchronized void draftSet(String conv, String text) {
        ContentValues cv = new ContentValues();
        cv.put("conv", conv); cv.put("text", text == null ? "" : text); cv.put("ts", System.currentTimeMillis());
        w().insertWithOnConflict("drafts", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }
    public synchronized String draftGet(String conv) {
        Cursor c = w().rawQuery("SELECT text FROM drafts WHERE conv=?", new String[]{conv});
        String r = null; if (c.moveToFirst()) r = c.getString(0); c.close(); return r;
    }
    public synchronized void draftDel(String conv) { w().delete("drafts", "conv=?", new String[]{conv}); }
    public synchronized int allRead() {
        ContentValues cv = new ContentValues(); cv.put("read", 1);
        return w().update("msgs", cv, "out=0", null);
    }

    public synchronized void setQuoteByMid(String conv, String mid, String qb) {
        if (mid == null || mid.length() == 0) return;
        ContentValues cv = new ContentValues();
        cv.put("qb", qb == null ? "" : qb);
        w().update("msgs", cv, "conv=? AND msgid=?", new String[]{conv, mid});
    }

    public synchronized boolean hasMid(String conv, String mid) {
        if (mid == null || mid.length() == 0) return false;
        Cursor c = w().rawQuery("SELECT 1 FROM msgs WHERE conv=? AND msgid=? LIMIT 1", new String[]{conv, mid});
        boolean r = c.moveToFirst(); c.close(); return r;
    }

    public synchronized void markRead(String conv) {
        ContentValues cv = new ContentValues();
        cv.put("read", 1);
        // 3.2.3+: 移除危险的自愈删除(曾把同ts/同内容消息误删导致"聊天记录没了")
        w().update("msgs", cv, "conv=? AND out=0", new String[]{conv});
    }
    public synchronized List<Object[]> search(String q) {
        // [convName, convId, type, body, ty, ts]
        List<Object[]> out = new ArrayList<Object[]>();
        Cursor c = w().rawQuery(
            "SELECT m.body,m.ty,m.ts,m.conv,c.name,c.type FROM msgs m JOIN convs c ON c.id=m.conv " +
            "WHERE m.body LIKE ? AND m.ty!='recall' ORDER BY m.ts DESC LIMIT 100",
            new String[]{"%" + q + "%"});
        while (c.moveToNext()) {
            out.add(new Object[]{c.getString(0), c.getString(1), c.getLong(2), c.getString(3), c.getString(4), c.getInt(5)});
        }
        c.close();
        return out;
    }
    public synchronized void recallByMid(String conv, String mid, String notice) {
        ContentValues cv = new ContentValues();
        cv.put("ty", "recall");
        cv.put("body", notice);
        w().update("msgs", cv, "conv=? AND msgid=?", new String[]{conv, mid});
    }
    public synchronized List<Msg> msgList(String conv) {
        List<Msg> out = new ArrayList<Msg>();
        Cursor c = w().rawQuery("SELECT id,conv,out,ty,body,local,ts,sender,msgid,qb FROM msgs WHERE conv=? ORDER BY id ASC", new String[]{conv});
        while (c.moveToNext()) {
            Msg m = new Msg();
            m.id = c.getLong(0);
            m.conv = c.getString(1);
            m.out = c.getInt(2) == 1;
            m.ty = c.getString(3);
            m.body = c.getString(4);
            m.local = c.getString(5);
            m.ts = c.getLong(6);
            m.sender = c.getString(7);
            m.msgid = c.getString(8);
            m.qb = c.getString(9);
            out.add(m);
        }
        c.close();
        return out;
    }
}
