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
        scroll.setBackgroundColor(UI.surface(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, UI.dp(this, 22), 0, UI.dp(this, 36));

        // 大标题
        TextView t = UI.medium(this, "设置", UI.onSurface(this), 26f);
        t.setPadding(UI.dp(this, 22), 0, UI.dp(this, 22), 0);
        root.addView(t, new LinearLayout.LayoutParams(-1, -2));

        // 头像（圆角方形，与列表头像语言一致）
        avSlot = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(84), dp(84));
        alp.gravity = Gravity.CENTER_HORIZONTAL;
        alp.topMargin = dp(22);
        avSlot.setBackground(UI.round(this, UI.scLow(this), 26));
        avSlot.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pickAvatar(); } });
        root.addView(avSlot, alp);
        TextView hint = UI.label(this, "点击更换头像 · 端到端加密存储", UI.onSurfaceVariant(this), 11.5f, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(12), 0, 0);
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        renderAv();

        // ── 我的资料 ──
        root.addView(UI.sectionTitle(this, "我的资料"));
        LinearLayout prof = UI.card(this);
        nick = new EditText(this);
        nick.setText(P.name(this));
        nick.setTextSize(UI.T_BODY);
        nick.setTextColor(UI.onSurface(this));
        nick.setHintTextColor(UI.onSurfaceVariant(this));
        nick.setSingleLine(true);
        nick.setBackground(UI.round(this, UI.scHigh(this), UI.R_FIELD));
        int fp = UI.dp(this, 14);
        nick.setPadding(fp, fp, fp, fp);
        LinearLayout nickWrap = new LinearLayout(this);
        nickWrap.setPadding(dp(16), dp(16), dp(16), dp(8));
        nickWrap.addView(nick, new LinearLayout.LayoutParams(-1, -2));
        prof.addView(nickWrap, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout saveWrap = new LinearLayout(this);
        saveWrap.setPadding(dp(16), dp(4), dp(16), dp(16));
        TextView save = UI.primaryBtn(this, "保存昵称", null);
        save.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { saveName(); } });
        saveWrap.addView(save, new LinearLayout.LayoutParams(-1, -2));
        prof.addView(saveWrap, new LinearLayout.LayoutParams(-1, -2));
        root.addView(UI.cardWrap(this, prof));

        // ── 账号 ──
        root.addView(UI.sectionTitle(this, "账号"));
        LinearLayout acct = UI.card(this);
        acct.addView(cardRow("账号 ID（点击复制）", P.acct(this), false, new View.OnClickListener() {
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("id", P.acct(Settings.this)));
                Toast.makeText(Settings.this, "已复制", Toast.LENGTH_SHORT).show();
            }
        }), new LinearLayout.LayoutParams(-1, -2));
        acct.addView(UI.divider(this, 18));
        acct.addView(cardRow("服务器", P.server(this), false, null), new LinearLayout.LayoutParams(-1, -2));
        root.addView(UI.cardWrap(this, acct));

        // ── 外观 ──
        root.addView(UI.sectionTitle(this, "外观"));
        LinearLayout look = UI.card(this);
        look.addView(cardRow("配色方案", themeName(), true, new View.OnClickListener() {
            public void onClick(View v) { pickTheme(); }
        }), new LinearLayout.LayoutParams(-1, -2));
        look.addView(UI.divider(this, 18));
        look.addView(cardRow("深色模式", darkName(), true, new View.OnClickListener() {
            public void onClick(View v) { pickDark(); }
        }), new LinearLayout.LayoutParams(-1, -2));
        root.addView(UI.cardWrap(this, look));

        // ── 安全 ──
        root.addView(UI.sectionTitle(this, "安全"));
        LinearLayout sec = UI.card(this);
        String ky = Db.get(this).kvGet("kyber:1") != null
                ? "已完成，可被新版客户端添加"
                : "未完成：联网打开一次 App 将自动补发";
        sec.addView(cardRow("预密钥升级", ky, false, null), new LinearLayout.LayoutParams(-1, -2));
        root.addView(UI.cardWrap(this, sec));

        // ── 通用 ──
        root.addView(UI.sectionTitle(this, "通用"));
        LinearLayout gen = UI.card(this);
        gen.addView(cardRow("未读异常修复", "全部已读 + 去重", false, new View.OnClickListener() {
            public void onClick(View v) {
                int n = Db.get(Settings.this).repairAll();
                Toast.makeText(Settings.this, "已清理并标记 " + n + " 条", Toast.LENGTH_SHORT).show();
            }
        }), new LinearLayout.LayoutParams(-1, -2));
        gen.addView(UI.divider(this, 18));
        gen.addView(cardRow("复制诊断信息", "发给管理员", false, new View.OnClickListener() {
            public void onClick(View v) { copyDiag(); }
        }), new LinearLayout.LayoutParams(-1, -2));
        gen.addView(UI.divider(this, 18));
        gen.addView(cardRow("检查更新", version, true, new View.OnClickListener() {
            public void onClick(View v) { Updater.promptOrToast(Settings.this); }
        }), new LinearLayout.LayoutParams(-1, -2));
        root.addView(UI.cardWrap(this, gen));

        // ── 危险区 ──
        root.addView(UI.sectionTitle(this, "危险区"));
        LinearLayout dgr = UI.card(this);
        dgr.addView(cardRow("退出登录", "清除本机全部数据", false, new View.OnClickListener() {
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
        }), new LinearLayout.LayoutParams(-1, -2));
        root.addView(UI.cardWrap(this, dgr));

        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(UI.wrap(this, scroll, UI.surface(this), UI.surface(this)));
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

    @Override protected void onResume() { super.onResume(); UI.syncTheme(this); if (avSlot != null) renderAv(); Updater.resumeInstall(this); }

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

    /** 卡片内一行：标题 + 右侧值 + 可选箭头 */
    private View cardRow(String title, String value, boolean chevron, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(UI.rippleOnly(this));
        r.setPadding(dp(18), dp(15), dp(18), dp(15));
        r.addView(UI.label(this, title, UI.onSurface(this), UI.T_ROW, false),
                new LinearLayout.LayoutParams(0, -2, 1));
        if (value != null && value.length() > 0) {
            TextView v = UI.label(this, value, UI.onSurfaceVariant(this), 13.5f, false);
            v.setMaxLines(1);
            v.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
            vlp.leftMargin = dp(12);
            r.addView(v, vlp);
        }
        if (chevron) {
            TextView c = UI.label(this, "›", UI.onSurfaceVariant(this), 18f, false);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.leftMargin = dp(8);
            r.addView(c, clp);
        }
        if (l != null) r.setOnClickListener(l);
        return r;
    }

    private String themeName() {
        String id = P.themeId(this);
        for (int i = 0; i < Palettes.IDS.length; i++) {
            if (Palettes.IDS[i].equals(id)) return Palettes.NAMES[i];
        }
        return Palettes.NAMES[1];
    }

    private String darkName() {
        int m = P.darkMode(this);
        return m == 1 ? "浅色" : (m == 2 ? "深色" : "跟随系统");
    }

    /** 取指定配色方案的某个令牌色，用于列表里的预览色块 */
    private int swatch(String id, int token) {
        for (int i = 0; i < Palettes.IDS.length; i++) {
            if (Palettes.IDS[i].equals(id)) {
                if (i == 0) return UI.tok(this, token);   // 跟随系统：用当前动态色
                try {
                    return 0xFF000000 | Integer.parseInt(Palettes.C[i][UI.dark(this) ? 1 : 0][token], 16);
                } catch (Throwable t) { return 0xFF808080; }
            }
        }
        return 0xFF808080;
    }

    /** 配色方案选择：每项带 主色/容器色/表面色 三色预览 */
    private void pickTheme() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(8);
        box.setPadding(p, p, p, p);
        final AlertDialog dlg = new AlertDialog.Builder(this).setTitle("配色方案").setView(box).create();
        final String cur = P.themeId(this);
        for (int i = 0; i < Palettes.IDS.length; i++) {
            final String id = Palettes.IDS[i];
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setBackground(UI.rippleOnly(this));
            r.setPadding(dp(14), dp(13), dp(14), dp(13));
            int[] tks = {UI.TK_PRIMARY, UI.TK_PRIMARY_CONTAINER, UI.TK_SC_HIGH};
            for (int tk : tks) {
                TextView dot = new TextView(this);
                dot.setBackground(UI.circle(swatch(id, tk)));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(22), dp(22));
                dlp.rightMargin = dp(5);
                r.addView(dot, dlp);
            }
            TextView nm = UI.label(this, Palettes.NAMES[i], UI.onSurface(this), UI.T_ROW, false);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, -2, 1);
            nlp.leftMargin = dp(14);
            r.addView(nm, nlp);
            if (id.equals(cur)) r.addView(UI.label(this, "✓", UI.primary(this), 17f, true));
            r.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    P.setThemeId(Settings.this, id);
                    dlg.dismiss();
                    recreate();
                }
            });
            box.addView(r, new LinearLayout.LayoutParams(-1, -2));
        }
        dlg.show();
    }

    private void pickDark() {
        final String[] names = {"跟随系统", "浅色", "深色"};
        new AlertDialog.Builder(this).setTitle("深色模式")
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        P.setDarkMode(Settings.this, w);
                        recreate();
                    }
                }).show();
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
