package im.lilmouse.chat;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public class Conversations extends Activity {
    private ListView list;
    private ConvAdapter ad;
    private List<Db.Conv> data;
    private TextView subtitle;
    private TextView connDot;
    private static boolean updateChecked = false;
    private final android.os.Handler uiH = new android.os.Handler();
    private final Runnable connTick = new Runnable() {
        public void run() { refreshConn(); uiH.postDelayed(this, 3000); }
    };
    private android.widget.FrameLayout headAv;
    private final java.util.HashMap<String, android.graphics.Bitmap> avatars = new java.util.HashMap<String, android.graphics.Bitmap>();
    private final java.util.HashSet<String> avReq = new java.util.HashSet<String>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        CrashLog.install(this);
        if (!P.hasAccount(this)) {
            startActivity(new Intent(this, Setup.class));
            finish();
            return;
        }
        buildUi();
    }

    private String dispName(Db.Conv c) {
        return (c.name == null || c.name.length() == 0) ? c.id.substring(0, 8) : c.name;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(UI.background(this));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        // ---- 顶栏：标题 + 我的ID(点击复制) + ⋮菜单 ----
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        int hp = UI.dp(this, 12);
        head.setPadding(UI.dp(this, 16), hp, UI.dp(this, 4), hp);
        head.setBackground(UI.ripple(UI.accentHeader(this)));
        head.setElevation(UI.dp(this, 3));
        head.setLongClickable(true);

        headAv = new android.widget.FrameLayout(this);
        headAv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(Conversations.this, Settings.class)); }
        });
        LinearLayout.LayoutParams halp = new LinearLayout.LayoutParams(UI.dp(this, 38), -1);
        halp.rightMargin = UI.dp(this, 10);
        head.addView(headAv, halp);
        renderHeadAv();
        head.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("id", P.acct(Conversations.this)));
                subtitle.setText("已复制 " + P.acct(Conversations.this));
                Toast.makeText(Conversations.this, "账号ID已复制", Toast.LENGTH_SHORT).show();
            }
        });

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        TextView title = UI.label(this, P.name(this), 0xFFFFFFFF, 20, true);
        subtitle = UI.label(this, P.acct(this), 0xCCFFFFFF, 11, false);
        subtitle.setTypeface(Typeface.MONOSPACE);
        mid.addView(title, new LinearLayout.LayoutParams(-2, -2));
        mid.addView(subtitle, new LinearLayout.LayoutParams(-2, -2));
        head.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));

        connDot = UI.label(this, "●", 0xFFFFFFFF, 12, false);
        connDot.setPadding(0, 0, UI.dp(this, 6), 0);
        connDot.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(connDot, new LinearLayout.LayoutParams(-2, -1));

        TextView more = UI.label(this, "⋮", 0xFFFFFFFF, 26, false);
        more.setGravity(Gravity.CENTER);
        more.setBackground(UI.rippleOnly());
        more.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showMore(v); }
        });
        head.addView(more, new LinearLayout.LayoutParams(UI.dp(this, 46), -1));
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // ---- 会话列表 ----
        list = new ListView(this);
        list.setDivider(null);
        list.setBackgroundColor(UI.background(this));
        ad = new ConvAdapter();
        list.setAdapter(ad);
        TextView empty = new TextView(this);
        empty.setText("还没有会话\n点右下角 ＋ 加好友或建群");
        empty.setTextSize(14);
        empty.setGravity(Gravity.CENTER);
        empty.setTextColor(UI.textSub(this));
        list.setEmptyView(empty);
        try { col.addView(empty, new LinearLayout.LayoutParams(-1, -1)); } catch (Throwable t2) {}
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                Db.Conv cv = data.get(pos);
                Intent it = new Intent(Conversations.this, Chat.class);
                it.putExtra("conv", cv.id);
                it.putExtra("name", dispName(cv));
                it.putExtra("type", cv.type);
                startActivity(it);
            }
        });
        col.addView(list, new LinearLayout.LayoutParams(-1, -1, 1));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        // ---- FAB ----
        TextView fab = new TextView(this);
        fab.setText("+");
        fab.setTextSize(26);
        fab.setTextColor(0xFFFFFFFF);
        fab.setGravity(Gravity.CENTER);
        fab.setElevation(UI.dp(this, 6));
        fab.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF),
                UI.circle(UI.accent(this)), null));
        fab.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showFabMenu(v); }
        });
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                UI.dp(this, 56), UI.dp(this, 56), Gravity.BOTTOM | Gravity.RIGHT);
        flp.rightMargin = UI.dp(this, 18);
        flp.bottomMargin = UI.dp(this, 18);
        root.addView(fab, flp);

        setContentView(UI.wrap(this, root, UI.accentHeader(this), UI.background(this)));
    }

    private void showMore(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add("搜索聊天记录");
        pm.getMenu().add("加入群聊");
        pm.getMenu().add("检查更新");
        pm.getMenu().add("复制账号ID");
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem item) {
                String t = item.getTitle().toString();
                if (t.equals("搜索聊天记录")) startActivity(new Intent(Conversations.this, Search.class));
                else if (t.equals("加入群聊")) showJoinDialog();
                else if (t.equals("检查更新")) manualUpdate();
                else {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("id", P.acct(Conversations.this)));
                    Toast.makeText(Conversations.this, "已复制", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        pm.show();
    }

    /** 搜群名 → 入群申请 */
    private void showJoinDialog() {
        try {
            android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
            b.setTitle("加入群聊（按群名搜索）");
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            int pad = UI.dp(this, 16);
            box.setPadding(pad, pad, pad, pad);
            final EditText q = new EditText(this);
            q.setHint("群名关键字");
            q.setTextSize(15);
            q.setBackground(UI.round(this, UI.surface(this), 10));
            q.setPadding(UI.dp(this, 10), UI.dp(this, 10), UI.dp(this, 10), UI.dp(this, 10));
            box.addView(q, new LinearLayout.LayoutParams(-1, -2));
            final LinearLayout res = new LinearLayout(this);
            res.setOrientation(LinearLayout.VERTICAL);
            res.setPadding(0, UI.dp(this, 8), 0, 0);
            box.addView(res, new LinearLayout.LayoutParams(-1, -2));
            final android.widget.ScrollView sc = new android.widget.ScrollView(this);
            sc.addView(box);
            final android.app.AlertDialog d = b.setView(sc).setNegativeButton("关闭", null).create();
            d.show();
            android.widget.Button btn = new android.widget.Button(this);
            btn.setText("搜索");
            btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    final String kw = q.getText().toString().trim();
                    if (kw.length() == 0) return;
                    Toast.makeText(Conversations.this, "搜索中…", Toast.LENGTH_SHORT).show();
                    P.POOL.execute(new Runnable() { public void run() {
                        try {
                            final java.util.List<String[]> gs = G.search(Conversations.this, kw);
                            runOnUiThread(new Runnable() { public void run() {
                                res.removeAllViews();
                                if (gs.isEmpty()) { res.addView(UI.label(Conversations.this, "没有找到相关群", UI.textSub(Conversations.this), 14, false)); return; }
                                for (final String[] g : gs) {
                                    LinearLayout row = new LinearLayout(Conversations.this);
                                    row.setOrientation(LinearLayout.HORIZONTAL);
                                    row.setGravity(Gravity.CENTER_VERTICAL);
                                    row.setBackground(new android.graphics.drawable.RippleDrawable(
                                        android.content.res.ColorStateList.valueOf(0x14000000),
                                        UI.round(Conversations.this, UI.surface(Conversations.this), 16), null));
                                    row.setPadding(UI.dp(Conversations.this, 12), UI.dp(Conversations.this, 9), UI.dp(Conversations.this, 10), UI.dp(Conversations.this, 9));
                                    LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                                    rlp.bottomMargin = UI.dp(Conversations.this, 6);
                                    row.addView(UI.avatar(Conversations.this, g[1], g[0], 34), new LinearLayout.LayoutParams(-2, -2));
                                    LinearLayout mid = new LinearLayout(Conversations.this);
                                    mid.setOrientation(LinearLayout.VERTICAL);
                                    TextView n = UI.label(Conversations.this, g[1], UI.textMain(Conversations.this), 16, true);
                                    TextView o = UI.label(Conversations.this, "群主 · " + g[2].substring(0, 8), UI.textSub(Conversations.this), 11, false);
                                    o.setTypeface(Typeface.MONOSPACE);
                                    mid.addView(n, new LinearLayout.LayoutParams(-2, -2));
                                    mid.addView(o, new LinearLayout.LayoutParams(-2, -2));
                                    LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -2, 1);
                                    mlp.leftMargin = UI.dp(Conversations.this, 8);
                                    row.addView(mid, mlp);
                                    row.setOnClickListener(new View.OnClickListener() { public void onClick(View v2) {
                                        if (g[2].equals(P.acct(Conversations.this))) { Toast.makeText(Conversations.this, "这是你自己的群", Toast.LENGTH_SHORT).show(); return; }
                                        d.dismiss();
                                        Toast.makeText(Conversations.this, "已发送加入申请…", Toast.LENGTH_SHORT).show();
                                        P.POOL.execute(new Runnable() { public void run() {
                                            try { G.reqJoin(Conversations.this, g[0], g[1], g[2]); } catch (Throwable t) {}
                                        }});
                                    }});
                                    res.addView(row, rlp);
                                }
                            }});
                        } catch (Throwable t) {}
                    }});
                }
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
            blp.topMargin = UI.dp(this, 8);
            box.addView(btn, blp);
        } catch (Throwable t) {}
    }

    private void showFabMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add("加好友");
        pm.getMenu().add("建群");
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem item) {
                startActivity(new Intent(Conversations.this,
                        item.getTitle().equals("加好友") ? AddContact.class : GroupCreate.class));
                return true;
            }
        });
        pm.show();
    }

    private void manualUpdate() {
        Toast.makeText(this, "检查中…", Toast.LENGTH_SHORT).show();
        Updater.check(this, false, new Updater.Cb() {
            public void on(final boolean newer, final Updater.Info i, final String err) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (err != null) Toast.makeText(Conversations.this, "检查失败", Toast.LENGTH_SHORT).show();
                        else if (newer) Updater.showDialog(Conversations.this, i);
                        else Toast.makeText(Conversations.this, "已是最新版本", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        reload();
        maybeCheckUpdate();
        Updater.resumeInstall(this);
        AvatarCache.keepAlive(this);
        renderHeadAv();
        Sync.on(this);
        refreshConn();
        uiH.removeCallbacks(connTick);
        uiH.postDelayed(connTick, 3000);
    }
    @Override protected void onPause() {
        super.onPause();
        uiH.removeCallbacks(connTick);
    }
    private long lastTryStart = 0;
    private void refreshConn() {
        if (connDot == null) return;
        int st = MsgService.connState;
        long heart = MsgService.heartbeatTs;
        boolean serviceDead = heart > 0 && System.currentTimeMillis() - heart > 15000;
        int c;
        if (serviceDead) c = 0xFFD32F2F;                       // 心跳死=服务/连接真挂了
        else if (st == MsgService.ST_ONLINE) c = 0xFF2E7D32;
        else if (st == MsgService.ST_OFFLINE) c = 0xFFD32F2F;
        else c = 0xFFFF8F00;
        connDot.setTextColor(c);
        // 服务疑似没跑：尝试拉起(带间隔限制)
        if (st != MsgService.ST_ONLINE && System.currentTimeMillis() - lastTryStart > 20000) {
            lastTryStart = System.currentTimeMillis();
            try {
                Intent svc = new Intent(Conversations.this, MsgService.class);
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
            } catch (Throwable t) {}
        }
    }

    /** 拉取最新昵称：有手动备注的保留备注，无备注/自动ID的用服务器昵称 */
    private long lastNames = 0;
    private void refreshNames() {
        if (System.currentTimeMillis() - lastNames < 30000) return;  // B3: 30s 限流
        lastNames = System.currentTimeMillis();
        try {
            final java.util.ArrayList<String> peers = new java.util.ArrayList<String>();
            if (data != null) for (Db.Conv cv : data) if (cv.type == 0) peers.add(cv.id);
            if (peers.isEmpty()) return;
            P.POOL.execute(new Runnable() { public void run() {
                try {
                    StringBuilder ids = new StringBuilder();
                    for (int i = 0; i < peers.size() && i < 40; i++) {
                        if (ids.length() > 0) ids.append(",");
                        ids.append(peers.get(i));
                    }
                    com.eclipsesource.json.JsonArray ps = com.eclipsesource.json.Json.parse(
                            Api.get(P.server(Conversations.this) + "/v1/directory?ids=" + ids,
                                    P.auth(Conversations.this))).asObject().get("profiles").asArray();
                    boolean changed = false;
                    for (int i = 0; i < ps.size(); i++) {
                        com.eclipsesource.json.JsonObject prof = ps.get(i).asObject();
                        String id = prof.getString("accountId", "");
                        String nm = prof.getString("displayName", "");
                        if (id.length() != 32 || nm.length() == 0) continue;
                        Db db = Db.get(Conversations.this);
                        String remark = db.contactName(id);
                        if (remark == null || remark.equals(id.substring(0, 8))) {
                            db.contactSet(id, nm);
                            Db.Conv cv = db.convGet(id);
                            if (cv != null && (cv.name == null || cv.name.equals(id.substring(0, 8)))) {
                                db.convMeta(id, nm, cv.members);
                            }
                            changed = true;
                        }
                    }
                    if (changed) runOnUiThread(new Runnable() { public void run() { reloadOnly(); } });
                } catch (Throwable t) {}
            }});
        } catch (Throwable t) {}
    }
    private void reload() {
        data = Db.get(this).convList();
        refreshNames();
        if (data != null) for (Db.Conv c : data) {
            if (c.type != 0) continue;
            android.graphics.Bitmap b = AvatarCache.cached(this, c.id);
            if (b != null) avatars.put(c.id, b);
            else if (!avReq.contains(c.id)) {
                avReq.add(c.id);
                final String pid = c.id;
                AvatarCache.get(this, pid, new AvatarCache.Cb() {
                    public void on(android.graphics.Bitmap bm) {
                        if (bm != null) { avatars.put(pid, bm); ad.notifyDataSetChanged(); }
                    }
                });
            }
        }
        ad.notifyDataSetChanged();
    }
    private void reloadOnly() {
        data = Db.get(this).convList();
        ad.notifyDataSetChanged();
    }

    private void renderHeadAv() {
        if (headAv == null) return;
        final String me = P.acct(this);
        android.graphics.Bitmap b = AvatarCache.cached(this, me);
        headAv.removeAllViews();
        android.view.View av = UI.avatar(this, b, P.name(this), me, 38);
        android.widget.FrameLayout.LayoutParams alp = new android.widget.FrameLayout.LayoutParams(
                UI.dp(this, 38), UI.dp(this, 38), android.view.Gravity.CENTER);
        headAv.addView(av, alp);
        if (b == null) {
            AvatarCache.get(this, me, new AvatarCache.Cb() {
                public void on(android.graphics.Bitmap bm) {
                    if (bm == null || headAv == null) return;
                    headAv.removeAllViews();
                    android.view.View av2 = UI.avatar(Conversations.this, bm, P.name(Conversations.this), me, 38);
                    android.widget.FrameLayout.LayoutParams alp2 = new android.widget.FrameLayout.LayoutParams(
                            UI.dp(Conversations.this, 38), UI.dp(Conversations.this, 38), android.view.Gravity.CENTER);
                    headAv.addView(av2, alp2);
                }
            });
        }
    }

    private void maybeCheckUpdate() {
        if (updateChecked) return;
        updateChecked = true;
        Updater.check(this, true, new Updater.Cb() {
            public void on(final boolean newer, final Updater.Info i, final String err) {
                if (newer && i != null) runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(Conversations.this, "发现新版本 " + i.name + "，后台下载中", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private class ConvAdapter extends BaseAdapter {
        public int getCount() { return data == null ? 0 : data.size(); }
        public Object getItem(int p) { return data.get(p); }
        public long getItemId(int p) { return p; }
        public View getView(int p, View cv, ViewGroup pg) {
            final Db.Conv c = data.get(p);
            String nm = dispName(c);

            // 外层留白 + 卡片
            LinearLayout root = new LinearLayout(Conversations.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(UI.dp(Conversations.this, 12), UI.dp(Conversations.this, 3),
                    UI.dp(Conversations.this, 12), UI.dp(Conversations.this, 3));

            LinearLayout card = new LinearLayout(Conversations.this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(UI.ripple(UI.surface(Conversations.this)));
            android.graphics.drawable.GradientDrawable bgc = UI.round(Conversations.this, UI.surface(Conversations.this), 20);
            card.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x14000000), bgc, null));
            card.setPadding(UI.dp(Conversations.this, 14), UI.dp(Conversations.this, 10),
                    UI.dp(Conversations.this, 12), UI.dp(Conversations.this, 10));

            card.addView(UI.avatar(Conversations.this, avatars.get(c.id), nm, c.id, 48),
                    new LinearLayout.LayoutParams(-2, -2));
            LinearLayout sp2 = new LinearLayout(Conversations.this);
            sp2.setMinimumWidth(UI.dp(Conversations.this, 12));
            card.addView(sp2, new LinearLayout.LayoutParams(UI.dp(Conversations.this, 12), -2));

            LinearLayout midc = new LinearLayout(Conversations.this);
            midc.setOrientation(LinearLayout.VERTICAL);
            TextView n = UI.label(Conversations.this, nm, UI.textMain(Conversations.this), 17, true);
            TextView l = UI.label(Conversations.this, c.last == null ? c.id : c.last,
                    UI.textSub(Conversations.this), 14, false);
            l.setSingleLine(true);
            l.setMaxWidth(UI.dp(Conversations.this, 220));
            l.setEllipsize(android.text.TextUtils.TruncateAt.END);
            l.setPadding(0, UI.dp(Conversations.this, 2), 0, 0);
            midc.addView(n, new LinearLayout.LayoutParams(-2, -2));
            midc.addView(l, new LinearLayout.LayoutParams(-2, -2));
            card.addView(midc, new LinearLayout.LayoutParams(0, -2, 1));

            LinearLayout right = new LinearLayout(Conversations.this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.RIGHT);
            TextView t = UI.label(Conversations.this, UI.timeStr(c.ts), UI.textSub(Conversations.this), 12, false);
            right.addView(t, new LinearLayout.LayoutParams(-2, -2));
            if (c.unread > 0) {
                TextView badge = new TextView(Conversations.this);
                badge.setText(c.unread > 99 ? "99+" : String.valueOf(c.unread));
                badge.setTextSize(11);
                badge.setTypeface(Typeface.DEFAULT_BOLD);
                badge.setTextColor(0xFFFFFFFF);
                badge.setGravity(Gravity.CENTER);
                badge.setBackground(UI.round(Conversations.this, UI.accent(Conversations.this), 11));
                badge.setMinWidth(UI.dp(Conversations.this, 22));
                badge.setMaxWidth(UI.dp(Conversations.this, 44));
                badge.setPadding(UI.dp(Conversations.this, 6), 0, UI.dp(Conversations.this, 6), 0);
                badge.setMinHeight(UI.dp(Conversations.this, 22));
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
                blp.topMargin = UI.dp(Conversations.this, 6);
                right.addView(badge, blp);
            }
            card.addView(right, new LinearLayout.LayoutParams(-2, -2));

            root.addView(card, new LinearLayout.LayoutParams(-1, -2));
            return root;
        }
  }
}
