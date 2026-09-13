package im.lilmouse.chat;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class AddContact extends Activity {
    private EditText id; private EditText nick;
    private TextView go;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(UI.background(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = UI.dp(this, 24);
        root.setPadding(pad, UI.dp(this, 40), pad, pad);

        TextView t = UI.label(this, "添加好友", UI.textMain(this), 24, true);
        root.addView(t, new LinearLayout.LayoutParams(-1, -2));

        TextView l1 = UI.label(this, "对方账号ID（32位）", UI.textSub(this), 12, false);
        l1.setPadding(UI.dp(this, 4), UI.dp(this, 20), 0, UI.dp(this, 6));
        root.addView(l1, new LinearLayout.LayoutParams(-2, -2));
        id = input();
        root.addView(id, new LinearLayout.LayoutParams(-1, -2));
        id.addTextChangedListener(new android.text.TextWatcher() {
            private boolean asked = false;
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                final String q = s.toString().trim();
                if (asked || !q.matches("[0-9a-f]{32}")) return;
                asked = true;
                P.POOL.execute(new Runnable() { public void run() {
                    try {
                        String prof = Api.get(P.server(AddContact.this) + "/v1/directory?ids=" + q, P.auth(AddContact.this));
                        final String dn = com.eclipsesource.json.Json.parse(prof).asObject()
                                .get("profiles").asArray().get(0).asObject().getString("displayName", "");
                        if (dn.length() > 0) runOnUiThread(new Runnable() { public void run() {
                            if (nick.getText().toString().trim().length() == 0) nick.setText(dn);
                        }});
                    } catch (Exception e) {}
                }});
            }
        });

        TextView l2 = UI.label(this, "备注昵称", UI.textSub(this), 12, false);
        l2.setPadding(UI.dp(this, 4), UI.dp(this, 16), 0, UI.dp(this, 6));
        root.addView(l2, new LinearLayout.LayoutParams(-2, -2));
        nick = input();
        root.addView(nick, new LinearLayout.LayoutParams(-1, -2));

                go = UI.primaryBtn(this, "添加", null);
        int bp = UI.dp(this, 16);
        go.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { doAdd(); } });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = UI.dp(this, 28);
        root.addView(go, blp);

        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(UI.wrap(this, scroll, UI.background(this), UI.background(this)));
    }

    private EditText input() {
        EditText e = new EditText(this);
        e.setTextSize(15);
        e.setTextColor(UI.textMain(this));
        e.setSingleLine(true);
        e.setBackground(UI.round(this, UI.surface(this), 12));
        int p = UI.dp(this, 14);
        e.setPadding(p, p, p, p);
        return e;
    }

    private void doAdd() {
        final String pid = id.getText().toString().trim();
        final String nm = nick.getText().toString().trim();
        if (!pid.matches("[0-9a-f]{32}")) {
            Toast.makeText(this, "ID 格式不对（32位小写hex）", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pid.equals(P.acct(this))) {
            Toast.makeText(this, "这是你自己", Toast.LENGTH_SHORT).show();
            return;
        }
        go.setEnabled(false);
        Toast.makeText(this, "正在交换密钥…", Toast.LENGTH_SHORT).show();
        P.POOL.execute(new Runnable() {
            public void run() {
                try {
                    Crypto.ensureSession(AddContact.this, pid);
                    String name = nm;
                    if (name.length() == 0) {
                        try {
                            String prof = Api.get(P.server(AddContact.this) + "/v1/directory?ids=" + pid, P.auth(AddContact.this));
                            name = com.eclipsesource.json.Json.parse(prof).asObject()
                                    .get("profiles").asArray().get(0).asObject().getString("displayName", "");
                        } catch (Exception e2) {}
                        if (name.length() == 0) name = pid.substring(0, 8);
                    }
                    final String fname = name;
                    Db.get(AddContact.this).contactSet(pid, fname);
                    Db.get(AddContact.this).convEnsure(pid, 0, fname, pid);
                    runOnUiThread(new Runnable() { public void run() { finish(); } });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            go.setEnabled(true);
                            Toast.makeText(AddContact.this, "失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }
}
