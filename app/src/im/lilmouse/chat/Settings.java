package im.lilmouse.chat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
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

public class Settings extends Activity {
    private EditText nick;
    private String version = "";
    private android.widget.FrameLayout avSlot;
    private static final int REQ_AV = 7;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {}

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(UI.background(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = UI.dp(this, 20);
        root.setPadding(pad, UI.dp(this, 24), pad, pad);

        // 头部
        TextView t = UI.label(this, "设置", UI.textMain(this), 24, true);
        root.addView(t, new LinearLayout.LayoutParams(-1, -2));

        avSlot = new android.widget.FrameLayout(this);
        // gravity via layout
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(76), dp(76));
        alp.gravity = Gravity.CENTER_HORIZONTAL;
        alp.topMargin = dp(16);
        avSlot.setBackground(UI.round(this, UI.surface(this), 40));
        avSlot.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickAvatar(); } });
        root.addView(avSlot, alp);
        TextView hint = UI.label(this, "点击头像更换（端到端加密存储）", UI.textSub(this), 10, false);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        renderAv();

        // 我的资料
        root.addView(section("我的资料"));
        nick = new EditText(this);
        nick.setText(P.name(this));
        nick.setTextSize(15);
        nick.setTextColor(UI.textMain(this));
        nick.setSingleLine(true);
        nick.setBackground(UI.round(this, UI.surface(this), 12));
        int fp = UI.dp(this, 14);
        nick.setPadding(fp, fp, fp, fp);
        root.addView(nick, new LinearLayout.LayoutParams(-1, -2));
        root.addView(gap(8));

        TextView save = rowBtn("保存昵称", UI.accent(this));
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { saveName(); } });
        root.addView(save, new LinearLayout.LayoutParams(-1, -2));

        root.addView(section("账号"));
        View idRow = rowInfo("账号ID（点击复制）", P.acct(this));
        idRow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("id", P.acct(Settings.this)));
                Toast.makeText(Settings.this, "已复制", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(idRow, new LinearLayout.LayoutParams(-1, -2));
        root.addView(gap(6));
        root.addView(rowInfo("服务器", P.server(this)), new LinearLayout.LayoutParams(-1, -2));

        root.addView(section("外观"));
        root.addView(accentRow(), new LinearLayout.LayoutParams(-1, -2));
        root.addView(gap(6));

        root.addView(section("安全"));
        String ky = Db.get(this).kvGet("kyber:1") != null
                ? "已完成，可被新版客户端添加"
                : "未完成：联网打开一次 App 将自动补发";
        root.addView(rowInfo("预密钥升级", ky), new LinearLayout.LayoutParams(-1, -2));
        root.addView(gap(6));

        root.addView(section("通用"));
        TextView fix = rowBtn("未读异常修复（全部已读+去重）", 0xFF00897B);
        fix.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int n = Db.get(Settings.this).repairAll();
                Toast.makeText(Settings.this, "已清理并标记 " + n + " 条", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(fix, new LinearLayout.LayoutParams(-1, -2));

        TextView dbg = rowBtn("复制诊断信息（发给管理员）", 0xFF546E7A);
        dbg.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { copyDiag(); }
        });
        root.addView(dbg, new LinearLayout.LayoutParams(-1, -2));

        TextView upd = rowBtn("检查更新", UI.accent(this));
        upd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Updater.promptOrToast(Settings.this); }
        });
        root.addView(upd, new LinearLayout.LayoutParams(-1, -2));

        root.addView(section("危险区"));
        TextView logout = rowBtn("退出登录（清除本机全部数据）", 0xFFD32F2F);
        logout.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(Settings.this)
                        .setTitle("退出登录")
                        .setMessage("将清除本机的账号、密钥和全部聊天记录，且无法恢复。确定？")
                        .setPositiveButton("清除", new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int w) { doLogout(); }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        root.addView(logout, new LinearLayout.LayoutParams(-1, -2));

        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(UI.wrap(this, scroll, UI.background(this), UI.background(this)));
    }

    private void renderAv() {
        String me = P.acct(this);
        android.graphics.Bitmap b = AvatarCache.cached(this, me);
        avSlot.removeAllViews();
        avSlot.addView(UI.avatar(this, b, P.name(this), me, 60));
        if (b == null) {
            AvatarCache.get(this, me, new AvatarCache.Cb() {
                public void on(android.graphics.Bitmap bm) { if (bm != null) runOnUiThread(new Runnable() { public void run() { renderAv(); } }); }
            });
        }
    }

    private void pickAvatar() {
        Intent it = new Intent(Intent.ACTION_GET_CONTENT);
        it.setType("image/*");
        startActivityForResult(Intent.createChooser(it, "选头像"), REQ_AV);
    }

    @Override protected void onResume() { super.onResume(); if (avSlot != null) renderAv(); Updater.resumeInstall(this); }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_AV || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        Toast.makeText(this, "上传中…", Toast.LENGTH_SHORT).show();
        P.POOL.execute(new Runnable() { public void run() {
            try {
                java.io.InputStream is = getContentResolver().openInputStream(uri);
                android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeStream(is);
                is.close();
                if (bm == null) throw new Exception("图片读取失败");
                int r = Math.min(bm.getWidth(), bm.getHeight());
                android.graphics.Bitmap sq = android.graphics.Bitmap.createBitmap(bm,
                        (bm.getWidth() - r) / 2, (bm.getHeight() - r) / 2, r, r);
                if (r > 256) {
                    android.graphics.Bitmap small = android.graphics.Bitmap.createScaledBitmap(sq, 256, 256, true);
                    sq = small;
                }
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                sq.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, bo);
                byte[] jpg = bo.toByteArray();
                java.io.File dir = new java.io.File(getFilesDir(), "avatars");
                if (!dir.exists()) dir.mkdirs();
                java.io.File f = new java.io.File(dir, P.acct(Settings.this) + ".jpg");
                java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
                fo.write(jpg);
                fo.close();
                String field = AvatarCache.upload(Settings.this, jpg);
                AvatarCache.saveProfileField(Settings.this, field);
                AvatarCache.clearMemory(P.acct(Settings.this));
                runOnUiThread(new Runnable() { public void run() {
                    Toast.makeText(Settings.this, "头像已更新", Toast.LENGTH_SHORT).show();
                    renderAv();
                }});
            } catch (final Exception e) {
                runOnUiThread(new Runnable() { public void run() { Toast.makeText(Settings.this, "失败: " + e.getMessage(), Toast.LENGTH_LONG).show(); } });
            }
        }});
    }

    /** 主题色色板：点选即刻生效 */
    private View accentRow() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(UI.round(this, UI.surface(this), 12));
        int p = dp(14);
        box.setPadding(p, dp(12), p, dp(12));
        int cur = P.accentPref(this);
        String curName = "跟随系统";
        for (int i = 0; i < P.ACCENTS.length; i++) if (P.ACCENTS[i] == cur) curName = P.ACCENT_NAMES[i];
        box.addView(UI.label(this, "主题色 · " + curName, UI.textSub(this), 11, false),
                new LinearLayout.LayoutParams(-2, -2));

        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, dp(2));
        for (int i = 0; i < P.ACCENTS.length; i++) {
            final int argb = P.ACCENTS[i];
            int show = argb == 0
                    ? (android.os.Build.VERSION.SDK_INT >= 31
                        ? getColor(android.R.color.system_accent1_200)
                        : (UI.dark(this) ? 0xFF8AB4F8 : 0xFF0088CC))
                    : argb;
            TextView dot = new TextView(this);
            dot.setText(argb == cur ? "✓" : (argb == 0 ? "A" : ""));
            dot.setTextColor(UI.dark(this) ? 0xFF111111 : 0xFFFFFFFF);
            dot.setTextSize(15);
            dot.setTypeface(Typeface.DEFAULT_BOLD);
            dot.setGravity(Gravity.CENTER);
            dot.setBackground(UI.circle(show));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            lp.rightMargin = dp(10);
            dot.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    P.setAccent(Settings.this, argb);
                    recreate();
                }
            });
            row.addView(dot, lp);
        }
        hs.addView(row);
        box.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    private void copyDiag() {
        try {
            android.database.sqlite.SQLiteDatabase db = Db.get(this).getWritableDatabase();
            StringBuilder sb = new StringBuilder();
            long stale = System.currentTimeMillis() - MsgService.heartbeatTs;
            sb.append("版本 ").append(version).append(" | WS状态=").append(MsgService.connState)
              .append(" | 心跳=").append(stale > 15000 ? "死" : ("活+" + stale + "ms")).append("\n");
            android.database.Cursor c = db.rawQuery(
                "SELECT c.id,c.name,c.type,"+
                "(SELECT COUNT(*) FROM msgs m WHERE m.conv=c.id AND m.out=0 AND m.read=0),"+
                "(SELECT COUNT(*) FROM msgs m WHERE m.conv=c.id) FROM convs c", null);
            while (c.moveToNext()) sb.append(c.getString(0)).append(" | ").append(c.getString(1))
                .append(" | t").append(c.getInt(2)).append(" | 未读").append(c.getInt(3))
                .append(" | 共").append(c.getInt(4)).append("\n");
            c.close();
            java.io.File[] imgs = new java.io.File(getFilesDir(), "imgs").listFiles();
            sb.append("空id会话:").append(countId(db, "")).append(" | imgs文件:").append(imgs == null ? 0 : imgs.length);
            String cr = CrashLog.read(Settings.this);
            if (cr.length() > 0) sb.append("\n--- 上次崩溃 ---\n").append(cr.length() > 1500 ? cr.substring(0, 1500) : cr);
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("diag", sb.toString()));
            Toast.makeText(this, "诊断信息已复制到剪贴板", Toast.LENGTH_LONG).show();
        } catch (Throwable t) { Toast.makeText(this, "失败 " + t.getMessage(), Toast.LENGTH_SHORT).show(); }
    }
    private long countId(android.database.sqlite.SQLiteDatabase db, String id) {
        try {
            android.database.Cursor c = db.rawQuery("SELECT COUNT(*) FROM convs WHERE id=?", new String[]{id});
            c.moveToFirst(); long r = c.getLong(0); c.close(); return r;
        } catch (Throwable t) { return -1; }
    }

    private int dp(int v) { return UI.dp(this, v); }
    private TextView section(String s) {
        TextView t = UI.label(this, s, UI.textSub(this), 12, false);
        t.setPadding(dp(4), dp(22), 0, dp(8));
        return t;
    }
    private View gap(int dp) {
        View v = new View(this);
        v.setMinimumHeight(dp(dp));
        return v;
    }
    private TextView rowBtn(String s, int color) {
        TextView b = new TextView(this);
        b.setText(s);
        b.setTextSize(15);
        b.setTextColor(0xFFFFFFFF);
        b.setGravity(Gravity.CENTER);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), UI.round(this, color, 14), null));
        int p = dp(14);
        b.setPadding(p, p, p, p);
        return b;
    }
    private View rowInfo(String k, String v) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(UI.round(this, UI.surface(this), 12));
        int p = dp(14);
        box.setPadding(p, dp(10), p, dp(10));
        box.addView(UI.label(this, k, UI.textSub(this), 11, false), new LinearLayout.LayoutParams(-2, -2));
        TextView val = UI.label(this, v, UI.textMain(this), 14, false);
        val.setTypeface(Typeface.MONOSPACE);
        box.addView(val, new LinearLayout.LayoutParams(-2, -2));
        return box;
    }

    private void saveName() {
        final String nm = nick.getText().toString().trim();
        if (nm.length() == 0 || nm.length() > 32) {
            Toast.makeText(this, "昵称 1-32 字", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "保存中…", Toast.LENGTH_SHORT).show();
        P.POOL.execute(new Runnable() {
            public void run() {
                try {
                    P.sp(Settings.this).edit().putString("name", nm).commit();
                    AvatarCache.saveProfileField(Settings.this, P.sp(Settings.this).getString("myAvatarField", ""));
                    runOnUiThread(new Runnable() { public void run() { Toast.makeText(Settings.this, "已保存", Toast.LENGTH_SHORT).show(); } });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() { public void run() { Toast.makeText(Settings.this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show(); } });
                }
            }
        });
    }

    private void doLogout() {
        MsgService.stop(this);
        Db.get(this).reset(this);
        P.sp(this).edit().clear().commit();
        startActivity(new Intent(this, Setup.class));
        finish();
    }
}
