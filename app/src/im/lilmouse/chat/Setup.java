package im.lilmouse.chat;

import android.app.Activity;
import android.content.Intent;
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

public class Setup extends Activity {
    private EditText server; private EditText nick;
    private TextView go;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(UI.background(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = UI.dp(this, 24);
        root.setPadding(pad, UI.dp(this, 40), pad, pad);

        TextView logo = UI.label(this, "LM Chat", UI.accent(this), 30, true);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo, new LinearLayout.LayoutParams(-1, -2));
        TextView sub = UI.label(this, "小圈子端到端加密聊天", UI.textSub(this), 13, false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = UI.dp(this, 4);
        root.addView(sub, slp);

        addField(root, "服务器地址", server = input(P.DEF_SERVER), UI.dp(this, 24));
        addField(root, "昵称", nick = input(""), 0);

                go = UI.primaryBtn(this, "开始使用", null);
        int bp = UI.dp(this, 16);
        go.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { doRegister(); } });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = UI.dp(this, 32);
        root.addView(go, blp);

        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(UI.wrap(this, scroll, UI.background(this), UI.background(this)));
    }

    private void addField(LinearLayout root, String label, EditText e, int top) {
        TextView t = UI.label(this, label, UI.textSub(this), 12, false);
        t.setPadding(UI.dp(this, 4), UI.dp(this, 16), 0, UI.dp(this, 6));
        root.addView(t, new LinearLayout.LayoutParams(-2, -2));
        root.addView(e, new LinearLayout.LayoutParams(-1, -2));
    }

    private EditText input(String prefill) {
        EditText e = new EditText(this);
        e.setText(prefill);
        e.setTextSize(15);
        e.setTextColor(UI.textMain(this));
        e.setHintTextColor(UI.textSub(this));
        e.setSingleLine(true);
        e.setBackground(UI.round(this, UI.surface(this), 12));
        int p = UI.dp(this, 14);
        e.setPadding(p, p, p, p);
        return e;
    }

    private void doRegister() {
        final String sv = server.getText().toString().trim();
        final String nm = nick.getText().toString().trim();
        if (sv.length() == 0 || nm.length() == 0) {
            Toast.makeText(this, "都填一下", Toast.LENGTH_SHORT).show();
            return;
        }
        go.setEnabled(false);
        go.setText("注册中…");
        P.POOL.execute(new Runnable() {
            public void run() {
                try {
                    Crypto.createAccount(Setup.this, sv, nm);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            startActivity(new Intent(Setup.this, Conversations.class));
                            finish();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            go.setEnabled(true);
                            go.setText("开始使用");
                            String msg = e.getMessage() == null ? "失败" : e.getMessage();
                            if (msg.contains("409")) msg = "账号已存在，请重试";
                            Toast.makeText(Setup.this, "注册失败: " + msg, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }
}
