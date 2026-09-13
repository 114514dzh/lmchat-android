package im.lilmouse.chat;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.eclipsesource.json.JsonObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class Chat extends Activity {
    private static final android.util.LruCache<String, android.graphics.Bitmap> THUMBS =
            new android.util.LruCache<String, android.graphics.Bitmap>(48) {
                protected int sizeOf(String k, android.graphics.Bitmap b) { return b.getByteCount() / 1024; }
            };

    static android.graphics.Bitmap thumbOrCache(String path, int max) {
        String key = path + "#" + max;
        android.graphics.Bitmap b = THUMBS.get(key);
        if (b != null) return b;
        b = thumb(path, max);
        if (b != null) THUMBS.put(key, b);
        return b;
    }
    public static volatile String openConv = null;
    private static volatile Runnable onPoke = null;

    public static void poke() {
        Runnable r = onPoke;
        if (r != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }

    private String conv;
    private String convName;
    private int type;
    private ListView list;
    private MsgAdapter ad;
    private List<Db.Msg> data = new ArrayList<Db.Msg>();
    private static final int REQ_IMG = 41;
    private EditText inputBox;
    private final java.util.List<Object> rows = new java.util.ArrayList<Object>(); // String分隔 或 Db.Msg
    private static final int REQ_SHARE = 55;
    private android.widget.FrameLayout headAv;
    private String replyMid = null;
    private String replyText = null;
    private TextView quoteTv;
    private LinearLayout quoteBar;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        conv = getIntent().getStringExtra("conv");
        convName = getIntent().getStringExtra("name");
        type = getIntent().getIntExtra("type", 0);
        if (conv == null) { finish(); return; }
        buildUi();
        reload();
    }

    private int dp(int v) { return UI.dp(this, v); }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(UI.surface(this));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        // ---- 顶栏 ----
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackgroundColor(UI.surface(this));
        int hp = dp(8);
        head.setPadding(dp(6), hp, dp(12), hp);

        TextView back = UI.label(this, "←", UI.onSurface(this), 22, false);
        back.setGravity(Gravity.CENTER);
        back.setBackground(UI.rippleOnly());
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        head.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));

        String avId = conv;
        String avName = convName == null ? conv.substring(0, 8) : convName;
        headAv = new android.widget.FrameLayout(this);
        head.addView(headAv, new LinearLayout.LayoutParams(-2, -2));
        renderHeadAv();

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout msp = new LinearLayout(this);
        head.addView(msp, new LinearLayout.LayoutParams(dp(10), -2));
        TextView title = UI.medium(this, avName, UI.onSurface(this), 16.5f);
        title.setSingleLine(true);
        String sub = type == 1 ? "群聊" : conv;
        TextView st = UI.label(this, sub, UI.onSurfaceVariant(this), 11, false);
        st.setTypeface(Typeface.MONOSPACE);
        st.setSingleLine(true);
        mid.addView(title, new LinearLayout.LayoutParams(-2, -2));
        mid.addView(st, new LinearLayout.LayoutParams(-2, -2));
        head.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));
        if (type == 1) {
            mid.setBackground(UI.rippleOnly());
            mid.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showGroupManage(); } });
        }
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // ---- 消息列表 ----
        list = new ListView(this) {
            // 容器级接管横滑(QQ同思路): 拦截后自己平移/高亮/回弹, 不依赖行内分发
            private float mdx0 = 0, mdy0 = 0;
            private boolean swiping = false;
            private View swipeRow = null;
            private int swipePos = -1;
            private void endSwipe(float dx, boolean doReply) {
                final View r = swipeRow;
                if (r != null) {
                    r.animate().translationX(0f).setDuration(160).start();
                    r.postDelayed(new Runnable() { public void run() {
                        if (r != null) r.setBackgroundColor(0x00000000);
                    }}, 180);
                }
                if (doReply && dx < -dp(45)) swipeReplyFrom(swipePos);
                swiping = false; swipeRow = null; swipePos = -1;
            }
            public boolean onInterceptTouchEvent(android.view.MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        mdx0 = ev.getX(); mdy0 = ev.getY();
                        swiping = false; swipeRow = null; swipePos = -1;
                        break;
                    case android.view.MotionEvent.ACTION_MOVE:
                        float dx = Math.abs(ev.getX() - mdx0);
                        float dy = Math.abs(ev.getY() - mdy0);
                        if (!swiping && dx > dp(6) && dx > dy) {
                            swiping = true;
                            int p = pointToPosition((int) mdx0, (int) mdy0);
                            swipePos = p;
                            swipeRow = getChildAt(p - getFirstVisiblePosition());
                            return true; // 接管
                        }
                        break;
                }
                return super.onInterceptTouchEvent(ev);
            }
            public boolean onTouchEvent(android.view.MotionEvent ev) {
                if (swiping) {
                    switch (ev.getActionMasked()) {
                        case android.view.MotionEvent.ACTION_MOVE:
                            float tx = Math.max(ev.getX() - mdx0, -dp(130));
                            if (swipeRow != null) {
                                swipeRow.setTranslationX(tx);
                                swipeRow.setBackgroundColor(0x1A00AEEF);
                            }
                            break;
                        case android.view.MotionEvent.ACTION_UP:
                            endSwipe(ev.getX() - mdx0, true);
                            break;
                        case android.view.MotionEvent.ACTION_CANCEL:
                            endSwipe(ev.getX() - mdx0, false);
                            break;
                    }
                    return true;
                }
                return super.onTouchEvent(ev);
            }
        };
        list.setDivider(null);
        list.setBackgroundColor(UI.surface(this));
        list.setStackFromBottom(true);
        list.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        ad = new MsgAdapter();
        list.setAdapter(ad);
        col.addView(list, new LinearLayout.LayoutParams(-1, -1, 1));

        // 回复引用条
        quoteBar = new LinearLayout(this);
        quoteBar.setOrientation(LinearLayout.HORIZONTAL);
        quoteBar.setGravity(Gravity.CENTER_VERTICAL);
        quoteBar.setBackgroundColor(UI.surface(this));
        quoteBar.setPadding(dp(12), dp(4), dp(12), dp(4));
        quoteTv = UI.label(this, "", UI.textSub(this), 12, false);
        quoteTv.setSingleLine(true);
        quoteTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        quoteBar.addView(quoteTv, new LinearLayout.LayoutParams(0, -2, 1));
        TextView qx = UI.label(this, "✕", UI.textSub(this), 14, false);
        qx.setPadding(dp(4), 0, 0, 0);
        qx.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { clearReply(); } });
        quoteBar.addView(qx, new LinearLayout.LayoutParams(-2, -2));
        quoteBar.setVisibility(View.GONE);
        col.addView(quoteBar, new LinearLayout.LayoutParams(-1, -2));

        // ---- 输入条 ----
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(UI.surface(this));
        int bp = dp(10);
        bar.setPadding(bp + dp(4), bp - dp(2), bp + dp(4), bp);

        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setBackground(UI.round(this, UI.scHigh(this), UI.R_PILL));
        int fp = dp(6);
        field.setPadding(dp(14), fp, fp, fp);
        inputBox = new EditText(this);
        inputBox.setTextSize(15);
        inputBox.setHint("消息");
        inputBox.setTextColor(UI.textMain(this));
        inputBox.setHintTextColor(UI.textSub(this));
        inputBox.setBackground(null);
        inputBox.setMaxLines(4);
        field.addView(inputBox, new LinearLayout.LayoutParams(0, -2, 1));
        TextView imgBtn = UI.label(this, "图", UI.onSurfaceVariant(this), 15, false);
        imgBtn.setGravity(Gravity.CENTER);
        imgBtn.setBackground(UI.rippleOnly());
        imgBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_GET_CONTENT);
                it.setType("image/*");
                startActivityForResult(Intent.createChooser(it, "选图"), REQ_IMG);
            }
        });
        field.addView(imgBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));
        bar.addView(field, new LinearLayout.LayoutParams(0, -2, 1));

        TextView send = new TextView(this);
        send.setText("➤");
        send.setTextSize(16);
        send.setTextColor(UI.onPrimary(this));
        send.setGravity(Gravity.CENTER);
        send.setBackground(new RippleDrawable(ColorStateList.valueOf(UI.withAlpha(UI.onPrimary(this), 0x33)),
                UI.circle(UI.primary(this)), null));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(44), dp(44));
        slp.leftMargin = dp(8);
        send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String txt = inputBox.getText().toString().trim();
                if (txt.length() == 0) return;
                inputBox.setText("");
                sendText(txt);
            }
        });
        bar.addView(send, slp);
        col.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        setContentView(UI.wrap(this, root, UI.surface(this), UI.surface(this)));
    }

    private void renderHeadAv() {
        if (headAv == null || type != 0) return;
        final String peer = conv;
        android.graphics.Bitmap b = AvatarCache.cached(this, peer);
        headAv.removeAllViews();
        headAv.addView(UI.avatar(this, b, convName, peer, 38));
        if (b == null) {
            AvatarCache.get(this, peer, new AvatarCache.Cb() {
                public void on(android.graphics.Bitmap bm) {
                    if (bm == null || headAv == null) return;
                    headAv.removeAllViews();
                    headAv.addView(UI.avatar(Chat.this, bm, convName, peer, 38));
                }
            });
        }
    }

    private String selfAcct() { return P.acct(this); }

    /** 群管理面板：成员+角色；群主/管理员可操作 */
    private void showGroupManage() {
        try {
            final Db.Conv cv = Db.get(this).convGet(conv);
            if (cv == null) return;
            final boolean meOwner = cv.owner != null && cv.owner.equals(selfAcct());
            final boolean meAdmin = !meOwner && G.isAdmin(this, conv);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            int pad2 = dp(6);
            box.setPadding(dp(14), dp(8), dp(14), dp(8));

            // 群主操作区
            if (meOwner) {
                LinearLayout ops = new LinearLayout(this);
                ops.setOrientation(LinearLayout.HORIZONTAL);
                TextView rename = chip("重命名");
                rename.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { promptRename(cv.name); }
                });
                final TextView dir = chip((cv.name == null || cv.name.length() == 0) ? "目录?" : "目录：在");
                dir.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        final boolean on = dir.getText().toString().contains("在");
                        final String label = dir.getText().toString();
                        P.POOL.execute(new Runnable() { public void run() {
                            try {
                                if (label.contains("在")) G.unpublish(Chat.this, conv);
                                else G.publish(Chat.this, conv, cv.name == null ? "" : cv.name);
                                runOnUiThread(new Runnable() { public void run() {
                                    dir.setText(on ? "目录：否" : "目录：在");
                                    Toast.makeText(Chat.this, on ? "已从群目录移除" : "已发布到群目录", Toast.LENGTH_SHORT).show();
                                }});
                            } catch (Throwable t) { runOnUiThread(new Runnable(){public void run(){Toast.makeText(Chat.this,"操作失败",Toast.LENGTH_SHORT).show();}}); }
                        }});
                    }
                });
                ops.addView(rename, new LinearLayout.LayoutParams(-2, -2));
                ops.addView(dir, new LinearLayout.LayoutParams(-2, -2));
                box.addView(ops, new LinearLayout.LayoutParams(-1, -2));
                TextView hint = UI.label(this, "群主 · 点成员可设管理员/移出", UI.textSub(this), 11, false);
                hint.setPadding(0, dp(4), 0, dp(4));
                box.addView(hint, new LinearLayout.LayoutParams(-2, -2));
            } else if (meAdmin) {
                TextView hint = UI.label(this, "管理员 · 点成员可移出", UI.textSub(this), 11, false);
                hint.setPadding(0, 0, 0, dp(4));
                box.addView(hint, new LinearLayout.LayoutParams(-2, -2));
            }

            // 成员列表
            final java.util.ArrayList<String[]> members = new java.util.ArrayList<String[]>();
            String admins = cv.admins == null ? "" : cv.admins;
            String self = selfAcct();
            members.add(new String[]{self, P.name(this) + "（我）", cv.owner != null && cv.owner.equals(self) ? "群主" : (isAdminStr(admins, self) ? "管理员" : "")});
            if (cv.members != null) for (String m : cv.members.split(",")) {
                String id = m.trim();
                if (id.length() != 32 || id.equals(self)) continue;
                String role = "";
                if (cv.owner != null && cv.owner.equals(id)) role = "群主";
                else if (isAdminStr(admins, id)) role = "管理员";
                members.add(new String[]{id, null, role});
            }
            android.widget.ScrollView sc2 = new android.widget.ScrollView(this);
            LinearLayout rows = new LinearLayout(this);
            rows.setOrientation(LinearLayout.VERTICAL);
            for (final String[] mm : members) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(UI.rippleOnly());
                int p6 = dp(5);
                row.setPadding(p6, p6, p6, p6);
                String nm = mm[1];
                if (nm == null) { String cn = Db.get(this).contactName(mm[0]); nm = (cn == null || cn.length() == 0) ? mm[0].substring(0, 8) : cn; }
                row.addView(UI.avatar(this, nm, mm[0], 30), new LinearLayout.LayoutParams(-2, -2));
                LinearLayout mid2 = new LinearLayout(this);
                mid2.setOrientation(LinearLayout.VERTICAL);
                TextView n2 = UI.label(this, nm, UI.textMain(this), 14, true);
                String roleTxt = mm[2] == null ? "" : mm[2];
                if (roleTxt.length() > 0) roleTxt = " · " + roleTxt;
                TextView r2 = UI.label(this, roleTxt.length() == 0 ? mm[0].substring(0, 8) : mm[0].substring(0, 8) + roleTxt, UI.textSub(this), 10, false);
                mid2.addView(n2, new LinearLayout.LayoutParams(-2, -2));
                mid2.addView(r2, new LinearLayout.LayoutParams(-2, -2));
                LinearLayout.LayoutParams mlp2 = new LinearLayout.LayoutParams(0, -2, 1);
                mlp2.leftMargin = dp(8);
                row.addView(mid2, mlp2);
                final String mid0 = mm[0];
                final String nm2 = nm;
                final boolean isOwnerRow = mm[2] != null && mm[2].contains("群主");
                final boolean isAdminRow = mm[2] != null && mm[2].contains("管理员");
                row.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (mid0.equals(self)) return;
                        if (!meOwner && !meAdmin) { copyId(mid0); return; }
                        if (isOwnerRow) { copyId(mid0); return; }
                        if (meAdmin && isAdminRow) { copyId(mid0); return; }
                        android.widget.ListAdapter adapter = new android.widget.ArrayAdapter<String>(Chat.this,
                                android.R.layout.simple_list_item_1, actionsFor(meOwner, isAdminRow));
                        new android.app.AlertDialog.Builder(Chat.this).setTitle(nm2)
                            .setAdapter(adapter, new android.content.DialogInterface.OnClickListener() {
                                public void onClick(android.content.DialogInterface d, int which) {
                                    String act = actionsFor(meOwner, isAdminRow)[which];
                                    if (act.equals("移出群")) { G.kickMember(Chat.this, conv, mid0); Toast.makeText(Chat.this, "已移出", Toast.LENGTH_SHORT).show(); reload(); }
                                    else if (act.equals("设为管理员")) { G.promote(Chat.this, conv, mid0, true); Toast.makeText(Chat.this, "已设为管理员", Toast.LENGTH_SHORT).show(); }
                                    else if (act.equals("撤销管理员")) { G.promote(Chat.this, conv, mid0, false); Toast.makeText(Chat.this, "已撤销", Toast.LENGTH_SHORT).show(); }
                                    else copyId(mid0);
                                }
                            }).show();
                    }
                });
                rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            sc2.addView(rows);
            box.addView(sc2, new LinearLayout.LayoutParams(-1, -2));

            android.widget.ScrollView sc = new android.widget.ScrollView(this);
            sc.addView(box);
            new android.app.AlertDialog.Builder(this)
                    .setTitle(cv.name == null ? "群管理" : cv.name)
                    .setView(sc)
                    .setPositiveButton("关闭", null)
                    .show();
        } catch (Throwable t) {}
    }

    private String[] actionsFor(boolean meOwner, boolean isAdminRow) {
        if (meOwner) return new String[]{isAdminRow ? "撤销管理员" : "设为管理员", "移出群", "复制ID"};
        return new String[]{"移出群", "复制ID"};
    }
    private boolean isAdminStr(String admins, String acct) {
        if (admins == null) return false;
        for (String m : admins.split(",")) if (m.trim().equals(acct)) return true;
        return false;
    }
    private void copyId(String id) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("id", id));
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    }
    private void promptRename(final String oldName) {
        final EditText et = new EditText(this);
        et.setText(oldName == null ? "" : oldName);
        et.setTextSize(15);
        new android.app.AlertDialog.Builder(this).setTitle("重命名群")
            .setView(et)
            .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int w) {
                    final String nn = et.getText().toString().trim();
                    if (nn.length() == 0) return;
                    G.rename(Chat.this, conv, nn);
                    convName = nn;
                    Toast.makeText(Chat.this, "已重命名", Toast.LENGTH_SHORT).show();
                    runOnUiThread(new Runnable(){public void run(){ recreate(); }});
                }
            }).setNegativeButton("取消", null).show();
    }
    private TextView chip(String t) {
        TextView x = new TextView(this);
        x.setText(t);
        x.setTextSize(13);
        x.setGravity(Gravity.CENTER);
        x.setTextColor(UI.onPrimary(this));
        x.setBackground(UI.round(this, UI.accent(this), 16));
        int q = UI.dp(this, 8);
        x.setPadding(q, q, q, q);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = UI.dp(this, 8);
        x.setLayoutParams(lp);
        return x;
    }

    private Runnable refresh = new Runnable() { public void run() { reload(); } };

    @Override protected void onResume() {
        super.onResume();
        UI.syncTheme(this);
        openConv = conv;
        onPoke = refresh;
        reload();
        renderHeadAv();
        Sync.on(this);
        // 草稿恢复
        String d = Db.get(this).draftGet(conv);
        if (d != null && d.length() > 0 && inputBox != null) { inputBox.setText(d); inputBox.setSelection(d.length()); }
    }
    @Override protected void onPause() {
        super.onPause();
        if (conv.equals(openConv)) { openConv = null; onPoke = null; }
        if (inputBox != null) {
            String d = inputBox.getText().toString();
            if (d.trim().length() > 0) Db.get(this).draftSet(conv, d);
            else Db.get(this).draftDel(conv);
        }
    }

    private void attachReply(JsonObject o) {
        if (replyMid != null && !replyMid.isEmpty()) {
            JsonObject r = new JsonObject();
            r.add("mid", replyMid);
            r.add("b", replyText == null ? "" : (replyText.length() > 80 ? replyText.substring(0, 80) : replyText));
            o.add("reply", r);
        }
    }
    private void clearReply() { replyMid = null; replyText = null; if (quoteBar != null) quoteBar.setVisibility(View.GONE); }
    private void showQuote() {
        if (quoteBar != null && quoteTv != null) {
            quoteTv.setText("回复：" + (replyText == null ? "" : replyText));
            quoteBar.setVisibility(View.VISIBLE);
        }
    }
    private void swipeReplyFrom(int pos) {
        try { if (pos >= 0 && pos < data.size()) startReply(data.get(pos)); } catch (Throwable t) {}
    }
    private void startReply(final Db.Msg m) {
        if (m == null) return;
        replyMid = m.msgid;
        replyText = "img".equals(m.ty) ? "[图片]" : (m.body == null ? "" : m.body);
        showQuote();
    }

    private void reload() {
        Db.get(this).markRead(conv);
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.cancel(conv.hashCode());   // 打开即清该会话的通知(角标计数)
        } catch (Throwable t) {}

        data = Db.get(this).msgList(conv);
        ad.notifyDataSetChanged();
        list.post(new Runnable() { public void run() { list.setSelection(data.size() - 1); } });
    }

    // ---------------- sending ----------------

    private JsonObject txtJson(String body, String mid) {
        JsonObject o = new JsonObject();
        o.add("ty", "txt");
        o.add("b", body);
        o.add("ts", System.currentTimeMillis());
        o.add("mid", mid);
        return o;
    }
    private String newMid() { return java.util.UUID.randomUUID().toString(); }

    /** 规范花名册：全部成员(含自己) */
    private java.util.List<String> fullRoster() {
        java.util.List<String> res = new ArrayList<String>();
        String self = P.acct(this);
        res.add(self);
        Db.Conv cv = Db.get(this).convGet(conv);
        if (cv != null && cv.members != null) for (String m : cv.members.split(",")) {
            String mm = m.trim();
            if (mm.length() == 32 && !mm.equals(self)) res.add(mm);
        }
        return res;
    }

    /** 给消息 JSON 补群元信息 gid/gname/members */
    private void fillGroup(JsonObject o) {
        o.add("gid", conv);
        o.add("gname", convName == null ? "" : convName);
        com.eclipsesource.json.JsonArray ma = new com.eclipsesource.json.JsonArray();
        for (String m : fullRoster()) ma.add(m);
        o.add("members", ma);
    }

    private List<String> groupMembers() {
        Db.Conv cv = Db.get(this).convGet(conv);
        List<String> res = new ArrayList<String>();
        if (cv != null && cv.members != null) {
            for (String m : cv.members.split(",")) {
                String s = m.trim();
                if (s.length() == 32 && !s.equals(P.acct(this))) res.add(s);
            }
        }
        return res;
    }

    private void sendText(final String body) {
        final String mid = newMid();
        JsonObject payload;
        if (type == 1) {
            JsonObject g = new JsonObject();
            g.add("ty", "gmsg");
            g.add("b", body);
            g.add("ts", System.currentTimeMillis());
            g.add("mid", mid);
            fillGroup(g);
            payload = g;
        } else payload = txtJson(body, mid);
        attachReply(payload);
        final byte[] jb = payload.toString().getBytes();
        P.POOL.execute(new Runnable() {
            public void run() {
                try {
                    deliver(jb);
                    Db.get(Chat.this).msgAdd(conv, true, "txt", body, null,
                            System.currentTimeMillis(), P.acct(Chat.this), mid);
                    Db.get(Chat.this).draftDel(conv);
                    {String _r=replyMid,_t=replyText; if(_r!=null&&!_r.isEmpty()&&_t!=null) Db.get(Chat.this).setQuoteByMid(conv, mid, _t.length()>80?_t.substring(0,80):_t);}
                    clearReply();
                    runOnUiThread(new Runnable() { public void run() { reload(); } });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() { Toast.makeText(Chat.this, "发送失败: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
                    });
                }
            }
        });
    }

    /** 加密并投递（1v1 whisper / 群 senderkey，群无链先分发），不落库 */
    private void deliver(byte[] plaintext) throws Exception {
        List<String> dests = new ArrayList<String>();
        List<String> payloads = new ArrayList<String>();
        if (type == 1) {
            dests = groupMembers();
            byte[] sealed;
            try {
                sealed = Crypto.groupSeal(this, conv, plaintext);
            } catch (org.signal.libsignal.protocol.NoSessionException e) {
                sendGdist(dests);
                sealed = Crypto.groupSeal(this, conv, plaintext);
            }
            byte[] meB = P.acct(this).getBytes("UTF-8");
            byte[] envB = new byte[33 + sealed.length];
            System.arraycopy(meB, 0, envB, 0, 32);
            envB[32] = (byte) Crypto.ENV_GROUP;
            System.arraycopy(sealed, 0, envB, 33, sealed.length);
            String env = P.b64(envB);
            for (String m : dests) payloads.add(env);
        } else {
            dests.add(conv);
            payloads.add(P.b64(Crypto.seal(this, conv, plaintext)));
        }
        Api.send(P.server(this), P.auth(this), dests, payloads);
    }

    private void sendGdist(List<String> dests) throws Exception {
        JsonObject gd = new JsonObject();
        gd.add("ty", "gdist");
        gd.add("gid", conv);
        gd.add("gname", convName == null ? "" : convName);
        com.eclipsesource.json.JsonArray ma = new com.eclipsesource.json.JsonArray();
        for (String m : fullRoster()) ma.add(m);
        gd.add("members", ma);
        gd.add("dist", P.b64(Crypto.groupCreate(this, conv).serialize()));
        byte[] gdb = gd.toString().getBytes();
        List<String> dd = new ArrayList<String>();
        List<String> pp = new ArrayList<String>();
        for (String m : dests) {
            dd.add(m);
            pp.add(P.b64(Crypto.seal(this, m, gdb)));
        }
        Api.send(P.server(this), P.auth(this), dd, pp);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_IMG || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        P.POOL.execute(new Runnable() {
            public void run() {
                try {
                    File dir = new File(getFilesDir(), "imgs");
                    if (!dir.exists()) dir.mkdirs();
                    final File local = new File(dir, "s" + System.currentTimeMillis() + ".jpg");
                    InputStream is = getContentResolver().openInputStream(uri);
                    FileOutputStream fo = new FileOutputStream(local);
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = is.read(buf)) > 0) fo.write(buf, 0, n);
                    is.close();
                    fo.close();
                    byte[] img = readAll(local);

                    byte[] key = new byte[32];
                    byte[] iv = new byte[12];
                    new SecureRandom().nextBytes(key);
                    new SecureRandom().nextBytes(iv);
                    byte[] ct = Crypto.aesEnc(key, iv, img);
                    String bid = Api.putBlob(P.server(Chat.this), P.auth(Chat.this), ct);

                    String fname = local.getName();
                    try {
                        android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
                        if (c != null) {
                            int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                            if (c.moveToFirst() && idx >= 0) {
                                String dn = c.getString(idx);
                                if (dn != null && dn.length() > 0) fname = dn;
                            }
                            c.close();
                        }
                    } catch (Throwable t) {}

                    final String mid = newMid();
                    JsonObject o = new JsonObject();
                    o.add("ty", "img");
                    o.add("bid", bid);
                    o.add("k", P.b64(key));
                    o.add("iv", P.b64(iv));
                    o.add("n", fname);
                    o.add("ts", System.currentTimeMillis());
                    o.add("mid", mid);
                    if (type == 1) fillGroup(o);
                    attachReply(o);
                    deliver(o.toString().getBytes());
                    Db.get(Chat.this).msgAdd(conv, true, "img", "[图片]", local.getAbsolutePath(),
                            System.currentTimeMillis(), P.acct(Chat.this), mid);
                    {String _r=replyMid,_t=replyText; if(_r!=null&&!_r.isEmpty()&&_t!=null) Db.get(Chat.this).setQuoteByMid(conv, mid, _t.length()>80?_t.substring(0,80):_t);}
                    clearReply();
                    runOnUiThread(new Runnable() { public void run() { reload(); } });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() { Toast.makeText(Chat.this, "图片发送失败: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
                    });
                }
            }
        });
    }

    private void sendRecall(final Db.Msg m) {
        if (m.msgid == null || m.msgid.length() == 0) return;
        P.POOL.execute(new Runnable() {
            public void run() {
                try {
                    JsonObject o = new JsonObject();
                    o.add("ty", "recall");
                    o.add("mid", m.msgid);
                    if (type == 1) o.add("gid", conv);
                    deliver(o.toString().getBytes());
                    Db.get(Chat.this).recallByMid(conv, m.msgid, "你撤回了一条消息");
                    runOnUiThread(new Runnable() { public void run() { reload(); } });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() { public void run() { Toast.makeText(Chat.this, "撤回失败", Toast.LENGTH_SHORT).show(); } });
                }
            }
        });
    }

    private static byte[] readAll(File f) throws Exception {
        InputStream is = new java.io.FileInputStream(f);
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return bo.toByteArray();
    }

    // ---------------- UI ----------------

    private View quoteView(String qb) {
        LinearLayout q = new LinearLayout(this);
        q.setOrientation(LinearLayout.VERTICAL);
        q.setPadding(dp(6), dp(2), dp(6), dp(2));
        q.setBackground(UI.round(this, 0x14000000, 6));
        TextView who = UI.label(this, "回复", UI.textSub(this), 9, true);
        q.addView(who, new LinearLayout.LayoutParams(-2, -2));
        TextView body = UI.label(this, qb, UI.textSub(this), 12, false);
        body.setSingleLine(true);
        body.setEllipsize(android.text.TextUtils.TruncateAt.END);
        q.addView(body, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
        wl.bottomMargin = dp(3);
        q.setLayoutParams(wl);
        return q;
    }

    static Bitmap thumb(String path, int max) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        int s = 1;
        while (o.outWidth / (s * 2) >= max || o.outHeight / (s * 2) >= max) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        return BitmapFactory.decodeFile(path, o2);
    }

    private void saveImgToGallery(String path, String ext) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                        "LM_" + System.currentTimeMillis() + "." + (ext==null||ext.isEmpty()?"jpg":ext.replace("image/","")));
                cv.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                cv.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/LM Chat");
                android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                    java.io.FileInputStream in = new java.io.FileInputStream(path);
                    byte[] buf = new byte[16384]; int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    in.close(); os.close();
                    Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            // 旧版本: 保存到 Pictures
            java.io.File f = new java.io.File(path);
            java.io.File dir = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "LM Chat");
            if (!dir.exists()) dir.mkdirs();
            java.io.File out = new java.io.File(dir, "LM_" + System.currentTimeMillis() + ".jpg");
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            java.io.FileOutputStream fo = new java.io.FileOutputStream(out);
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
            in.close(); fo.close();
            Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) { Toast.makeText(this, "保存失败: " + t.getMessage(), Toast.LENGTH_SHORT).show(); }
    }
    private void full(String path) {
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(thumbOrCache(path, 2048));
        final Dialog fd = d;
        iv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { fd.dismiss(); } });
        d.setContentView(iv);
        d.show();
    }

    private class MsgAdapter extends BaseAdapter {
        public int getCount() { return data.size(); }
        public Object getItem(int p) { return data.get(p); }
        public long getItemId(int p) { return data.get(p).id; }
        public View getView(int p, View cv, ViewGroup pg) {
            final Db.Msg m = data.get(p);
            boolean out = m.out;

            if ("recall".equals(m.ty)) {
                LinearLayout row0 = new LinearLayout(Chat.this);
                row0.setGravity(Gravity.CENTER);
                row0.setPadding(dp(10), dp(3), dp(10), dp(3));
                row0.addView(UI.label(Chat.this, m.body, UI.textSub(Chat.this), 12, false),
                        new LinearLayout.LayoutParams(-2, -2));
                return row0;
            }

            LinearLayout row = new LinearLayout(Chat.this);
            row.setPadding(dp(16), dp(3), dp(16), dp(3));
            LinearLayout outer = new LinearLayout(Chat.this);
            LinearLayout bubble = new LinearLayout(Chat.this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setBackground(UI.bubbleShape(Chat.this,
                    out ? UI.outBubble(Chat.this) : UI.inBubble(Chat.this), out));
            int pad = dp(10);
            int vpad = dp(7);
            bubble.setPadding(pad, vpad, pad, vpad);
            if (m.qb != null && m.qb.length() > 0) {
                bubble.addView(quoteView(m.qb), new LinearLayout.LayoutParams(-1, -2));
            }
            int txtColor = out ? UI.outBubbleText(Chat.this) : UI.inBubbleText(Chat.this);

            if (type == 1 && !out && m.sender != null && m.sender.length() > 0) {
                String sn = Db.get(Chat.this).contactName(m.sender);
                if (sn == null || sn.length() == 0) sn = m.sender.substring(0, 8);
                TextView snv = UI.label(Chat.this, sn, UI.hashColor(m.sender), 12, true);
                snv.setSingleLine(true);
                snv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                snv.setMaxWidth(dp(220));
                // 点昵称 = @TA(插入到输入框)
                final String atWho = sn;
                snv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                    if (inputBox != null) {
                        String cur = inputBox.getText().toString();
                        String ins = "@" + atWho + " ";
                        inputBox.setText(cur + ins);
                        inputBox.setSelection(inputBox.getText().length());
                        inputBox.requestFocus();
                    }
                }});
                bubble.addView(snv, new LinearLayout.LayoutParams(-2, -2));
            }

            if ("img".equals(m.ty)) {
                if (m.local != null && new File(m.local).exists()) {
                    ImageView iv = new ImageView(Chat.this);
                    final String tp = m.local;
                    final String mime = (m.mime == null || m.mime.isEmpty()) ? "jpg" : m.mime;
                    iv.setTag(tp);
                    android.graphics.Bitmap hit = THUMBS.get(tp + "#400");
                    iv.setImageBitmap(hit);
                    iv.setAdjustViewBounds(true);
                    iv.setMaxWidth(dp(220));
                    if (hit == null) {
                        P.POOL.execute(new Runnable() {
                            public void run() {
                                final android.graphics.Bitmap b = thumb(tp, 400);
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        if (tp.equals(iv.getTag())) {
                                            if (b != null) { THUMBS.put(tp + "#400", b); iv.setImageBitmap(b); }
                                        }
                                    }
                                });
                            }
                        });
                    }
                    iv.setOnLongClickListener(new View.OnLongClickListener() {
                        public boolean onLongClick(View v) {
                            saveImgToGallery(tp, mime);
                            return true;
                        }
                    });
                    iv.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) { full(m.local); }
                    });
                    bubble.addView(iv, new LinearLayout.LayoutParams(-2, -2));
                } else {
                    TextView t = UI.label(Chat.this, out ? "[图片] 已发送" : "[图片]", txtColor, 15, false);
                    bubble.addView(t, new LinearLayout.LayoutParams(-2, -2));
                }
            } else {
                TextView t = UI.label(Chat.this, m.body, txtColor, 15, false);
                t.setMaxWidth(dp(260));
                bubble.addView(t, new LinearLayout.LayoutParams(-2, -2));
                bubble.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View v) {
                        android.widget.PopupMenu pm = new android.widget.PopupMenu(Chat.this, v);
                        pm.getMenu().add("回复");
                        pm.getMenu().add("复制");
                        if (out && m.msgid != null && m.msgid.length() > 0) pm.getMenu().add("撤回");
                        pm.setOnMenuItemClickListener(new android.widget.PopupMenu.OnMenuItemClickListener() {
                            public boolean onMenuItemClick(android.view.MenuItem item) {
                                if (item.getTitle().equals("回复")) { startReply(m); }
                                else if (item.getTitle().equals("复制")) {
                                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                    cm.setPrimaryClip(ClipData.newPlainText("msg", m.body));
                                    Toast.makeText(Chat.this, "已复制", Toast.LENGTH_SHORT).show();
                                } else sendRecall(m);
                                return true;
                            }
                        });
                        pm.show();
                        return true;
                    }
                });
            }
            TextView tm = UI.label(Chat.this, UI.timeStr(m.ts),
                    UI.withAlpha(txtColor, 0x99), 10, false);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);  // 占满气泡宽
            tlp.topMargin = dp(2);
            tm.setGravity(Gravity.RIGHT);
            bubble.addView(tm, tlp);

            // 群聊双方头像: 收=左头像|气泡, 发=气泡|右头像; 点/长按他人头像=@
            android.view.View av = null;
            if (type == 1) {
                String peerId = out ? P.acct(Chat.this) : m.sender;
                String peerName = out ? (P.name(Chat.this)==null?"我":P.name(Chat.this))
                        : (Db.get(Chat.this).contactName(m.sender));
                android.graphics.Bitmap ab = AvatarCache.cached(Chat.this, peerId);
                if (ab == null) {
                    final String fid = peerId;
                    AvatarCache.get(Chat.this, fid, new AvatarCache.Cb(){ public void on(android.graphics.Bitmap bm){ if(bm!=null&&!isFinishing()&&ad!=null){ try{ad.notifyDataSetChanged();}catch(Throwable t){} } } });
                }
                av = UI.avatar(Chat.this, ab, peerName, peerId, 34);
                if (!out) {
                    final String atId = peerId;
                    final String atName = peerName;
                    android.view.View.OnClickListener atClick = new android.view.View.OnClickListener() { public void onClick(View v) {
                        if (inputBox != null) {
                            String cur = inputBox.getText().toString();
                            String ins = "@" + (atName==null||atName.isEmpty()?atId.substring(0,8):atName) + " ";
                            inputBox.setText(cur + ins);
                            inputBox.setSelection(inputBox.getText().length());
                            inputBox.requestFocus();
                        }
                    }};
                    av.setOnClickListener(atClick);
                    av.setOnLongClickListener(new android.view.View.OnLongClickListener() { public boolean onLongClick(View v) { atClick.onClick(v); return true; } });
                } else {
                    // 自己的头像: 长按=复制自己ID
                    final String selfId = P.acct(Chat.this);
                    av.setOnLongClickListener(new android.view.View.OnLongClickListener() { public boolean onLongClick(View v) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("id", selfId));
                        Toast.makeText(Chat.this, "已复制我的ID", Toast.LENGTH_SHORT).show();
                        return true;
                    }});
                }
            }
            // row: 收= [头像][气泡]; 发= [气泡][头像]
            if (type == 1 && av != null) {
                if (!out) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
                    lp.rightMargin = dp(6);
                    outer.addView(av, lp);
                    outer.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
                } else {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
                    lp.leftMargin = dp(6);
                    outer.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
                    outer.addView(av, lp);
                }
            } else {
                outer.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
            }
            outer.setGravity(out ? Gravity.END : Gravity.START);
            row.addView(outer, new LinearLayout.LayoutParams(-1, -2));
            final Db.Msg mm = m;
            // 拖动回复: 气泡跟手左移+淡蓝高亮, 松手超阈值触发回复并回弹, 否则弹回原位
            row.setOnTouchListener(new View.OnTouchListener() {
                private float dx0 = 0, dy0 = 0;
                private boolean horiz = false;
                private void settle(float dx, boolean up) {
                    row.setTranslationX(0f);
                    if (up && dx < -dp(45)) startReply(mm);
                    row.animate().translationX(0f).setDuration(160).start();
                    row.postDelayed(new Runnable() { public void run() {
                        row.setBackgroundColor(0x00000000);
                    }}, 180);
                }
                public boolean onTouch(View v, android.view.MotionEvent ev) {
                    switch (ev.getActionMasked()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            dx0 = ev.getRawX(); dy0 = ev.getRawY();
                            horiz = false;
                            break;
                        case android.view.MotionEvent.ACTION_MOVE:
                            float dx = ev.getRawX() - dx0;
                            float dy = Math.abs(ev.getRawY() - dy0);
                            if (!horiz && Math.abs(dx) > dp(8) && Math.abs(dx) > dy) horiz = true;
                            if (horiz) {
                                float tx = Math.max(dx, -dp(130));
                                row.setTranslationX(tx);
                                row.setBackgroundColor(0x1A00AEEF);
                            }
                            break;
                        case android.view.MotionEvent.ACTION_UP:
                            settle(ev.getRawX() - dx0, true);
                            break;
                        case android.view.MotionEvent.ACTION_CANCEL:
                            settle(ev.getRawX() - dx0, false);
                            break;
                    }
                    return false;
                }
            });
            return row;
        }
    }

    private boolean BuildOutlined() { return android.os.Build.VERSION.SDK_INT >= 31; }
}
