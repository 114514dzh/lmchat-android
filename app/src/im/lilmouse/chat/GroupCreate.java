package im.lilmouse.chat;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public class GroupCreate extends Activity {
    private EditText gname;
    private final LinkedHashSet<String> picked = new LinkedHashSet<String>();
    private LinearLayout listBox;
    private final List<Db.Conv> friends = new ArrayList<Db.Conv>();
    private final java.util.HashMap<String, CheckBox> boxes = new java.util.HashMap<String, CheckBox>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(UI.background(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = UI.dp(this, 20);
        root.setPadding(pad, UI.dp(this, 24), pad, UI.dp(this, 24));

        TextView t = UI.label(this, "创建群聊", UI.textMain(this), 22, true);
        root.addView(t, new LinearLayout.LayoutParams(-1, -2));

        TextView l1 = UI.label(this, "群名", UI.textSub(this), 12, false);
        l1.setPadding(UI.dp(this, 4), UI.dp(this, 16), 0, UI.dp(this, 6));
        root.addView(l1, new LinearLayout.LayoutParams(-2, -2));
        gname = new EditText(this);
        gname.setTextSize(15);
        gname.setTextColor(UI.textMain(this));
        gname.setBackground(UI.round(this, UI.surface(this), 12));
        int ip = UI.dp(this, 12);
        gname.setPadding(ip, ip, ip, ip);
        root.addView(gname, new LinearLayout.LayoutParams(-1, -2));

        TextView l2 = UI.label(this, "选择好友（点选，可多选）", UI.textSub(this), 12, false);
        l2.setPadding(UI.dp(this, 4), UI.dp(this, 16), 0, UI.dp(this, 6));
        root.addView(l2, new LinearLayout.LayoutParams(-2, -2));

        // 好友候选 = 所有 1v1 会话
        for (Db.Conv c : Db.get(this).convList()) if (c.type == 0) friends.add(c);
        if (friends.isEmpty()) {
            TextView e = UI.label(this, "还没有可选的单聊好友，先去加好友吧", UI.textSub(this), 13, false);
            e.setPadding(0, UI.dp(this, 6), 0, UI.dp(this, 6));
            root.addView(e, new LinearLayout.LayoutParams(-2, -2));
        }
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        for (final Db.Conv c : friends) {
            final String pid = c.id;
            String name = (c.name == null || c.name.length() == 0) ? pid.substring(0, 8) : c.name;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(UI.round(this, UI.surface(this), 10));
            int rp = UI.dp(this, 10);
            row.setPadding(rp, rp, rp, rp);
            row.addView(UI.avatar(this, name, pid, 34), new LinearLayout.LayoutParams(-2, -2));

            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            TextView n = UI.label(this, name, UI.textMain(this), 15, true);
            TextView s = UI.label(this, pid.substring(0, 8) + "…", UI.textSub(this), 11, false);
            s.setTypeface(Typeface.MONOSPACE);
            mid.addView(n, new LinearLayout.LayoutParams(-2, -2));
            mid.addView(s, new LinearLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -2, 1);
            mlp.leftMargin = UI.dp(this, 10);
            row.addView(mid, mlp);

            final CheckBox cb = new CheckBox(this);
            boxes.put(pid, cb);
            // 选中状态统一由 toggle() 管理, 避免 row/cb 双重点击时序错乱
            // 仅 row 处理点击; checkbox 本身不消费点击, 避免 double-fire
            cb.setClickable(false);
            cb.setFocusable(false);
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    boolean now = !cb.isChecked();
                    cb.setChecked(now);
                    if (now) picked.add(pid); else picked.remove(pid);
                }
            });
            row.addView(cb, new LinearLayout.LayoutParams(-2, -2));

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.bottomMargin = UI.dp(this, 6);
            listBox.addView(row, rlp);
        }
        root.addView(listBox, new LinearLayout.LayoutParams(-1, -2));

        TextView l3 = UI.label(this, "或手动输入对方账号ID（逗号分隔，可空）", UI.textSub(this), 12, false);
        l3.setPadding(UI.dp(this, 4), UI.dp(this, 10), 0, UI.dp(this, 6));
        root.addView(l3, new LinearLayout.LayoutParams(-2, -2));
        final EditText extra = new EditText(this);
        extra.setTextSize(14);
        extra.setTextColor(UI.textMain(this));
        extra.setMinLines(2);
        extra.setGravity(Gravity.TOP);
        extra.setBackground(UI.round(this, UI.surface(this), 10));
        extra.setPadding(ip, ip, ip, ip);
        root.addView(extra, new LinearLayout.LayoutParams(-1, -2));

        TextView         go = UI.primaryBtn(this, "创建并分发密钥", null);
        int bp = UI.dp(this, 16);
        go.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { doCreate(extra); }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = UI.dp(this, 20);
        root.addView(go, blp);

        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(UI.wrap(this, scroll, UI.background(this), UI.background(this)));
    }

    private void doCreate(EditText extra) {
        final LinkedHashSet<String> ms = new LinkedHashSet<String>(picked);
        for (String part : extra.getText().toString().split("[,\\n\\s]+")) {
            String s = part.trim();
            if (s.matches("[0-9a-f]{32}") && !s.equals(P.acct(this))) ms.add(s);
        }
        final String gn = gname.getText().toString().trim();
        if (gn.length() == 0) { Toast.makeText(this, "填个群名", Toast.LENGTH_SHORT).show(); return; }
        if (ms.isEmpty()) { Toast.makeText(this, "至少选一个好友", Toast.LENGTH_SHORT).show(); return; }

        Toast.makeText(this, "建群中…", Toast.LENGTH_SHORT).show();
        P.POOL.execute(new Runnable() {
            public void run() {
                try {
                    String gid = UUID.randomUUID().toString();
                    byte[] dist = Crypto.groupCreate(GroupCreate.this, gid).serialize();
                    JsonObject gd = new JsonObject();
                    gd.add("ty", "gdist");
                    gd.add("gid", gid);
                    gd.add("gname", gn);
                    gd.add("owner", P.acct(GroupCreate.this));
                    JsonArray ma = new JsonArray();
                    ma.add(P.acct(GroupCreate.this)); // 群主也是成员
                    for (String m : ms) ma.add(m);
                    gd.add("members", ma);
                    gd.add("dist", P.b64(dist));
                    byte[] gdb = gd.toString().getBytes("UTF-8");
                    List<String> dests = new ArrayList<String>();
                    List<String> payloads = new ArrayList<String>();
                    for (String m : ms) {
                        dests.add(m);
                        payloads.add(P.b64(Crypto.seal(GroupCreate.this, m, gdb)));
                    }
                    Api.send(P.server(GroupCreate.this), P.auth(GroupCreate.this), dests, payloads);
                    StringBuilder sb = new StringBuilder();
                    for (String m : ms) {
                        if (sb.length() > 0) sb.append(",");
                        sb.append(m);
                    }
                    Db.get(GroupCreate.this).convEnsure(gid, 1, gn, sb.toString());
                    Db.get(GroupCreate.this).convMeta(gid, gn, sb.toString());
                    Db.get(GroupCreate.this).convRole(gid, P.acct(GroupCreate.this), "");
                    try { G.publish(GroupCreate.this, gid, gn); } catch (Throwable t) {}
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Intent it = new Intent(GroupCreate.this, Conversations.class);
                            it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(it);
                            finish();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() { Toast.makeText(GroupCreate.this, "失败: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
                    });
                }
            }
        });
    }
}
