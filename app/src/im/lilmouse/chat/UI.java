package im.lilmouse.chat;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * 设计系统。
 *
 * 令牌取自 Palettes（Material 3 完整色板，来源见该类注释）。
 * 配色方案与明暗模式在「设置 → 外观」切换，这里是唯一的取色出口——
 * 各界面不要再写死颜色，一律走本类。
 */
public class UI {

    // ───────── 令牌索引（与 Palettes.TOKEN 一一对应）─────────
    public static final int TK_PRIMARY = 0, TK_ON_PRIMARY = 1,
            TK_PRIMARY_CONTAINER = 2, TK_ON_PRIMARY_CONTAINER = 3,
            TK_SECONDARY_CONTAINER = 4, TK_ON_SECONDARY_CONTAINER = 5,
            TK_SURFACE = 6, TK_ON_SURFACE = 7,
            TK_SURFACE_VARIANT = 8, TK_ON_SURFACE_VARIANT = 9,
            TK_SC_LOWEST = 10, TK_SC_LOW = 11, TK_SC = 12,
            TK_SC_HIGH = 13, TK_SC_HIGHEST = 14,
            TK_OUTLINE = 15, TK_OUTLINE_VARIANT = 16,
            TK_INVERSE_SURFACE = 17, TK_INVERSE_ON_SURFACE = 18, TK_ERROR = 19;

    // ───────── 字号（sp）与圆角（dp）─────────
    public static final float T_DISPLAY = 30f, T_TITLE_L = 22f, T_TITLE = 17f,
            T_ROW = 16f, T_BODY = 14.5f, T_LABEL = 12.5f, T_CAPTION = 11.5f;
    public static final int R_CARD = 18, R_FIELD = 16, R_PILL = 999, R_BUBBLE = 20;

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** 明暗：优先用户设置（0 跟随系统 / 1 浅色 / 2 深色） */
    public static boolean dark(Context c) {
        int m = P.darkMode(c);
        if (m == 1) return false;
        if (m == 2) return true;
        int x = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return x == Configuration.UI_MODE_NIGHT_YES;
    }

    static int paletteIndex(Context c) {
        String id = P.themeId(c);
        for (int i = 0; i < Palettes.IDS.length; i++) {
            if (Palettes.IDS[i].equals(id)) return i;
        }
        return 1; // 未知 id 回退到「极简」
    }

    /** 取令牌色；索引 0（跟随系统）走 API31+ 动态取色 */
    public static int tok(Context c, int idx) {
        boolean dk = dark(c);
        int pi = paletteIndex(c);
        if (pi == 0) return dynamic(c, idx, dk);
        String[] row = Palettes.C[pi][dk ? 1 : 0];
        try {
            return 0xFF000000 | Integer.parseInt(row[idx], 16);
        } catch (Throwable t) {
            return 0xFF808080;
        }
    }

    private static int dynamic(Context c, int idx, boolean dk) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                switch (idx) {
                    case TK_PRIMARY:
                        return c.getColor(dk ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600);
                    case TK_ON_PRIMARY:
                        return c.getColor(dk ? android.R.color.system_accent1_900 : android.R.color.system_accent1_0);
                    case TK_PRIMARY_CONTAINER:
                        return c.getColor(dk ? android.R.color.system_accent1_700 : android.R.color.system_accent1_100);
                    case TK_ON_PRIMARY_CONTAINER:
                        return c.getColor(dk ? android.R.color.system_accent1_100 : android.R.color.system_accent1_900);
                    case TK_SECONDARY_CONTAINER:
                        return c.getColor(dk ? android.R.color.system_accent2_700 : android.R.color.system_accent2_100);
                    case TK_ON_SECONDARY_CONTAINER:
                        return c.getColor(dk ? android.R.color.system_accent2_100 : android.R.color.system_accent2_900);
                    case TK_SURFACE:
                        return c.getColor(dk ? android.R.color.system_neutral1_900 : android.R.color.system_neutral1_0);
                    case TK_ON_SURFACE:
                        return c.getColor(dk ? android.R.color.system_neutral1_100 : android.R.color.system_neutral1_900);
                    case TK_SURFACE_VARIANT:
                        return c.getColor(dk ? android.R.color.system_neutral2_800 : android.R.color.system_neutral2_100);
                    case TK_ON_SURFACE_VARIANT:
                        return c.getColor(dk ? android.R.color.system_neutral2_200 : android.R.color.system_neutral2_700);
                    case TK_SC_LOWEST:
                        return c.getColor(dk ? android.R.color.system_neutral1_1000 : android.R.color.system_neutral1_0);
                    case TK_SC_LOW:
                        return c.getColor(dk ? android.R.color.system_neutral1_900 : android.R.color.system_neutral1_50);
                    case TK_SC:
                        return c.getColor(dk ? android.R.color.system_neutral1_800 : android.R.color.system_neutral1_100);
                    case TK_SC_HIGH:
                        return c.getColor(dk ? android.R.color.system_neutral1_700 : android.R.color.system_neutral1_200);
                    case TK_SC_HIGHEST:
                        return c.getColor(dk ? android.R.color.system_neutral1_600 : android.R.color.system_neutral1_300);
                    case TK_OUTLINE:
                        return c.getColor(dk ? android.R.color.system_neutral2_400 : android.R.color.system_neutral2_500);
                    case TK_OUTLINE_VARIANT:
                        return c.getColor(dk ? android.R.color.system_neutral2_700 : android.R.color.system_neutral2_200);
                    case TK_INVERSE_SURFACE:
                        return c.getColor(dk ? android.R.color.system_neutral1_100 : android.R.color.system_neutral1_800);
                    case TK_INVERSE_ON_SURFACE:
                        return c.getColor(dk ? android.R.color.system_neutral1_900 : android.R.color.system_neutral1_100);
                    case TK_ERROR:
                        return dk ? 0xFFFFB4AB : 0xFFBA1A1A;
                }
            } catch (Throwable t) { /* 落回色板 */ }
        }
        try {
            return 0xFF000000 | Integer.parseInt(Palettes.C[1][dk ? 1 : 0][idx], 16);
        } catch (Throwable t) {
            return 0xFF808080;
        }
    }

    // ───────── 令牌快捷取值 ─────────
    public static int primary(Context c) { return tok(c, TK_PRIMARY); }
    public static int onPrimary(Context c) { return tok(c, TK_ON_PRIMARY); }
    public static int primaryContainer(Context c) { return tok(c, TK_PRIMARY_CONTAINER); }
    public static int onPrimaryContainer(Context c) { return tok(c, TK_ON_PRIMARY_CONTAINER); }
    public static int secondaryContainer(Context c) { return tok(c, TK_SECONDARY_CONTAINER); }
    public static int onSecondaryContainer(Context c) { return tok(c, TK_ON_SECONDARY_CONTAINER); }
    public static int surface(Context c) { return tok(c, TK_SURFACE); }
    public static int onSurface(Context c) { return tok(c, TK_ON_SURFACE); }
    public static int surfaceVariant(Context c) { return tok(c, TK_SURFACE_VARIANT); }
    public static int onSurfaceVariant(Context c) { return tok(c, TK_ON_SURFACE_VARIANT); }
    public static int scLowest(Context c) { return tok(c, TK_SC_LOWEST); }
    public static int scLow(Context c) { return tok(c, TK_SC_LOW); }
    public static int sc(Context c) { return tok(c, TK_SC); }
    public static int scHigh(Context c) { return tok(c, TK_SC_HIGH); }
    public static int scHighest(Context c) { return tok(c, TK_SC_HIGHEST); }
    public static int outline(Context c) { return tok(c, TK_OUTLINE); }
    public static int outlineVariant(Context c) { return tok(c, TK_OUTLINE_VARIANT); }
    public static int inverseSurface(Context c) { return tok(c, TK_INVERSE_SURFACE); }
    public static int inverseOnSurface(Context c) { return tok(c, TK_INVERSE_ON_SURFACE); }
    public static int error(Context c) { return tok(c, TK_ERROR); }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    // ───────── 兼容旧调用（语义映射到新令牌）─────────
    /** 旧名，现等价于 primary */
    public static int accent(Context c) { return primary(c); }
    /**
     * 旧名：原实现把主色加深当作顶栏色（Holo/MD2 的染色条）。
     * 新设计顶栏用表面色，此处返回 surface，各界面顶栏文字需改 onSurface。
     */
    public static int accentHeader(Context c) { return surface(c); }
    /** 旧名，等价 surfaceContainerLow（卡片/容器底） */
    public static int cardBg(Context c) { return scLow(c); }
    public static int background(Context c) { return surface(c); }
    public static int textMain(Context c) { return onSurface(c); }
    public static int textSub(Context c) { return onSurfaceVariant(c); }
    /** 发送气泡：与主色同族的容器色（旧实现用的是异色系的 accent3_100） */
    public static int outBubble(Context c) { return primaryContainer(c); }
    public static int outBubbleText(Context c) { return onPrimaryContainer(c); }
    /** 接收气泡 */
    public static int inBubble(Context c) { return scHigh(c); }
    public static int inBubbleText(Context c) { return onSurface(c); }

    /** 与背景混合 */
    public static int blend(int fg, int bg, float ratio) {
        int r = (int) (((fg >> 16) & 0xff) * ratio + ((bg >> 16) & 0xff) * (1 - ratio));
        int g = (int) (((fg >> 8) & 0xff) * ratio + ((bg >> 8) & 0xff) * (1 - ratio));
        int b = (int) ((fg & 0xff) * ratio + (bg & 0xff) * (1 - ratio));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // ───────── 水波纹（跟随前景色，不再硬编码纯黑）─────────
    /** 旧签名（无 Context）：按底色亮暗自动选波纹色——亮底深波纹、暗底浅波纹 */
    public static RippleDrawable ripple(int base) {
        int rc = isLight(base) ? 0x22000000 : 0x22FFFFFF;
        return new RippleDrawable(ColorStateList.valueOf(rc), new ColorDrawable(base), null);
    }

    public static RippleDrawable ripple(Context c, int base) {
        return new RippleDrawable(ColorStateList.valueOf(withAlpha(onSurface(c), 0x22)),
                new ColorDrawable(base), null);
    }

    /** 旧签名（无 Context），保留以兼容既有调用 */
    public static RippleDrawable rippleOnly() {
        return new RippleDrawable(ColorStateList.valueOf(0x22000000), null, new ColorDrawable(0xFFFFFFFF));
    }

    public static RippleDrawable rippleOnly(Context c) {
        return new RippleDrawable(ColorStateList.valueOf(withAlpha(onSurface(c), 0x22)),
                null, new ColorDrawable(0xFFFFFFFF));
    }

    // ───────── 形状 ─────────
    public static GradientDrawable round(Context c, int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusDp >= R_PILL ? dp(c, 999) : dp(c, radiusDp));
        return g;
    }

    public static GradientDrawable outlinedShape(Context c, int fill, int stroke, int radiusDp, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(dp(c, strokeDp), stroke);
        return g;
    }

    /** 气泡形状：靠发送方的那一角收小，形成"尾巴" */
    public static GradientDrawable bubbleShape(Context c, int color, boolean out) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        float r = dp(c, R_BUBBLE), s = dp(c, 6);
        if (out) g.setCornerRadii(new float[]{r, r, r, r, s, s, r, r}); // 右下收口
        else     g.setCornerRadii(new float[]{r, r, r, r, r, r, s, s}); // 左下收口
        return g;
    }

    public static GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    public static TextView label(Context c, String s, int color, float size, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return t;
    }

    /** 中等字重（M3 的标题多用 medium 而非 bold） */
    public static TextView medium(Context c, String s, int color, float size) {
        TextView t = label(c, s, color, size, false);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    // ───────── 头像（圆角方形；旧的圆形保留为 avatarCircle）─────────
    private static final int[] PALETTE = {
            0xFFE17076, 0xFF7BC862, 0xFF65AADD, 0xFFA695E7,
            0xFFEE7AAE, 0xFFFAA774, 0xFF6EC9CB
    };

    public static int hashColor(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) h = h * 31 + s.charAt(i);
        return PALETTE[Math.abs(h) % PALETTE.length];
    }

    private static String firstLetter(String name, String id) {
        if (name == null || name.length() == 0) name = id == null ? "?" : id.substring(0, 1);
        return name.substring(0, 1).toUpperCase();
    }

    public static TextView avatar(Context c, String name, String id, int sizeDp) {
        TextView t = new TextView(c);
        t.setText(firstLetter(name, id));
        t.setTextColor(0xFFFFFFFF);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextSize(sizeDp * 0.38f);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(hashColor(id == null || id.length() == 0 ? "?" : id));
        g.setCornerRadius(dp(c, Math.max(8, Math.round(sizeDp * 0.32f))));
        t.setBackground(g);
        int px = dp(c, sizeDp);
        t.setWidth(px);
        t.setHeight(px);
        return t;
    }

    public static TextView avatarCircle(Context c, String name, String id, int sizeDp) {
        TextView t = avatar(c, name, id, sizeDp);
        t.setBackground(circle(hashColor(id == null || id.length() == 0 ? "?" : id)));
        return t;
    }

    public static View avatar(Context c, Bitmap bmp, String name, String id, int sizeDp) {
        if (bmp == null) return avatar(c, name, id, sizeDp);
        ImageView iv = new ImageView(c);
        iv.setImageBitmap(bmp);
        int px = dp(c, sizeDp);
        iv.setLayoutParams(new ViewGroup.LayoutParams(px, px));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setClipToOutline(true);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, Math.max(8, Math.round(sizeDp * 0.32f))));
        iv.setBackground(g);
        return iv;
    }

    public static Bitmap circle(Bitmap src) {
        if (src == null) return null;
        int w = src.getWidth(), h = src.getHeight();
        int r = Math.min(w, h);
        Bitmap out = Bitmap.createBitmap(r, r, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path path = new Path();
        path.addCircle(r / 2f, r / 2f, r / 2f, Path.Direction.CW);
        cv.clipPath(path);
        cv.drawBitmap(src, (r - w) / 2f, (r - h) / 2f, null);
        return out;
    }

    public static Bitmap decodeFile(String path, int max) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        int s = 1;
        while (o.outWidth / (s * 2) >= max || o.outHeight / (s * 2) >= max) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        return BitmapFactory.decodeFile(path, o2);
    }

    // ───────── 组件 ─────────

    /** 表面色顶栏：返回容器，调用方往里加标题/图标。不再染色、不再加阴影。 */
    public static LinearLayout appBar(Context c) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(surface(c));
        int h = dp(c, 18), v = dp(c, 14);
        bar.setPadding(h, v, h, dp(c, 10));
        return bar;
    }

    /** 顶栏右侧的圆形图标按钮 */
    public static TextView iconBtn(Context c, String glyph) {
        TextView t = label(c, glyph, onSurfaceVariant(c), 18, false);
        t.setGravity(Gravity.CENTER);
        t.setBackground(rippleOnly(c));
        int s = dp(c, 38);
        t.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return t;
    }

    /** 分区标题（小号、主色、字距略松） */
    public static TextView sectionTitle(Context c, String s) {
        TextView t = medium(c, s, primary(c), T_LABEL);
        t.setLetterSpacing(0.06f);
        t.setPadding(dp(c, 22), dp(c, 18), dp(c, 22), dp(c, 8));
        return t;
    }

    /** 卡片容器：圆角 + surfaceContainerLow，用于分组 */
    public static LinearLayout card(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(round(c, scLow(c), R_CARD));
        v.setClipToOutline(true);
        return v;
    }

    /** 卡片的左右外边距包装 */
    public static LinearLayout cardWrap(Context c, View inner) {
        LinearLayout w = new LinearLayout(c);
        w.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        w.addView(inner, new LinearLayout.LayoutParams(-1, -2));
        return w;
    }

    /** 内缩分隔线（与文字对齐） */
    public static View divider(Context c, int insetDp) {
        View v = new View(c);
        v.setBackgroundColor(outlineVariant(c));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 0)));
        p.height = 1;
        p.leftMargin = dp(c, insetDp);
        v.setLayoutParams(p);
        return v;
    }

    /** 列表行容器（统一内边距与点击反馈） */
    public static LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(rippleOnly(c));
        r.setPadding(dp(c, 18), dp(c, 13), dp(c, 18), dp(c, 13));
        return r;
    }

    /** 主按钮（填充） */
    public static TextView primaryBtn(final Context c, String text, View.OnClickListener l) {
        TextView b = medium(c, text, onPrimary(c), 15.5f);
        b.setGravity(Gravity.CENTER);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(onPrimary(c), 0x33)),
                round(c, primary(c), R_PILL), null));
        int v = dp(c, 15);
        b.setPadding(dp(c, 20), v, dp(c, 20), v);
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    /** 次级按钮（色调容器） */
    public static TextView tonalBtn(final Context c, String text, View.OnClickListener l) {
        TextView b = medium(c, text, onSecondaryContainer(c), 15f);
        b.setGravity(Gravity.CENTER);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(onSecondaryContainer(c), 0x33)),
                round(c, secondaryContainer(c), R_PILL), null));
        int v = dp(c, 13);
        b.setPadding(dp(c, 20), v, dp(c, 20), v);
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    /** 文本按钮 */
    public static TextView textBtn(final Context c, String text, View.OnClickListener l) {
        TextView b = medium(c, text, primary(c), 15f);
        b.setGravity(Gravity.CENTER);
        b.setBackground(rippleOnly(c));
        int v = dp(c, 11);
        b.setPadding(dp(c, 16), v, dp(c, 16), v);
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    /** 描边输入框（有边界、有聚焦态） */
    public static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setTextSize(T_BODY);
        e.setTextColor(onSurface(c));
        e.setHintTextColor(onSurfaceVariant(c));
        e.setBackground(outlinedShape(c, scLow(c), outlineVariant(c), R_FIELD, 1));
        int p = dp(c, 15);
        e.setPadding(p, p, p, p);
        if (hint != null) e.setHint(hint);
        return e;
    }

    /** 药丸输入容器（聊天输入区） */
    public static LinearLayout pill(Context c) {
        LinearLayout f = new LinearLayout(c);
        f.setOrientation(LinearLayout.HORIZONTAL);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setBackground(round(c, scHigh(c), R_PILL));
        f.setPadding(dp(c, 16), dp(c, 9), dp(c, 10), dp(c, 9));
        return f;
    }

    /** 圆形主色动作按钮（发送等） */
    public static TextView fab(Context c, String glyph, int sizeDp) {
        TextView t = medium(c, glyph, onPrimary(c), sizeDp * 0.4f);
        t.setGravity(Gravity.CENTER);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(onPrimary(c), 0x33)),
                circle(primary(c)), null));
        int s = dp(c, sizeDp);
        t.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return t;
    }

    /**
     * 外观同步：在 Activity.onResume 里调用。
     * 换了配色/明暗后，后台已存在的界面不会被 recreate() 波及，
     * 这里按 Activity 记录各自构建时的外观签名，变了就重建自己。
     */
    private static final java.util.WeakHashMap<Activity, String> APPLIED =
            new java.util.WeakHashMap<Activity, String>();

    public static void syncTheme(Activity a) {
        try {
            String sig = P.themeSig(a);
            String old = APPLIED.get(a);
            if (old == null) {
                APPLIED.put(a, sig);
                return;
            }
            if (!old.equals(sig)) {
                APPLIED.put(a, sig);
                a.recreate();
            }
        } catch (Throwable t) {}
    }

    // ───────── 系统栏 ─────────
    private static boolean isLight(int c) {
        int r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0 > 0.6;
    }

    public static void applyBars(Activity a, int color) {
        try {
            Window w = a.getWindow();
            w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(color);
            boolean light = isLight(color);
            View dv = w.getDecorView();
            int flags = dv.getSystemUiVisibility();
            if (light) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                w.setNavigationBarColor(color);
                if (light) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            dv.setSystemUiVisibility(flags);
        } catch (Throwable t) {}
    }

    /**
     * 系统栏跟随背景：Android 11+ 自绘色块（Android 15 起 setStatusBarColor 对
     * targetSdk35 已失效）；Android 6-10 沿用 setStatusBarColor + 内边距。
     */
    public static View wrap(Activity a, View content, int topColor, int bottomColor) {
        boolean lightTop = isLight(topColor), lightBottom = isLight(bottomColor);
        Window w = a.getWindow();
        if (Build.VERSION.SDK_INT < 30) {
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            try {
                w.setStatusBarColor(topColor);
                w.setNavigationBarColor(bottomColor);
            } catch (Throwable t) {}
            View dv2 = w.getDecorView();
            int fl = dv2.getSystemUiVisibility();
            if (lightTop) fl |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else fl &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                if (lightBottom) fl |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                else fl &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            dv2.setSystemUiVisibility(fl);
            return content;
        }
        w.setDecorFitsSystemWindows(false);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        try { w.setStatusBarColor(0); w.setNavigationBarColor(0); } catch (Throwable t) {}
        try {
            android.view.WindowInsetsController ic = w.getInsetsController();
            if (ic != null) {
                int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                int app = (lightTop ? android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0)
                        | (lightBottom ? android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0);
                ic.setSystemBarsAppearance(app, mask);
            }
        } catch (Throwable t) {}

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        final View top = new View(a);
        top.setBackgroundColor(topColor);
        final View bot = new View(a);
        bot.setBackgroundColor(Build.VERSION.SDK_INT >= 30 ? 0x00000000 : bottomColor);
        box.addView(top, new LinearLayout.LayoutParams(-1, 0));
        box.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        box.addView(bot, new LinearLayout.LayoutParams(-1, 0));
        box.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets in) {
                android.graphics.Insets bars = in.getInsets(android.view.WindowInsets.Type.systemBars());
                android.graphics.Insets ime = in.getInsets(android.view.WindowInsets.Type.ime());
                int t = bars.top;
                int b = Math.max(bars.bottom, ime.bottom);
                if (top.getLayoutParams().height != t) { top.getLayoutParams().height = t; top.requestLayout(); }
                if (bot.getLayoutParams().height != b) { bot.getLayoutParams().height = b; bot.requestLayout(); }
                return in;
            }
        });
        return box;
    }

    public static void fitSystemBars(final View root) {
        try {
            root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets in) {
                    int top = in.getSystemWindowInsetTop();
                    int bottom = in.getSystemWindowInsetBottom();
                    v.setPadding(v.getPaddingLeft(), top, v.getPaddingRight(), bottom);
                    return in;
                }
            });
            root.requestApplyInsets();
        } catch (Throwable t) {}
    }

    public static String timeStr(long ts) {
        Calendar now = Calendar.getInstance();
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(ts);
        String f = (now.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR)
                && now.get(Calendar.YEAR) == t.get(Calendar.YEAR)) ? "HH:mm" : "MM-dd";
        return new SimpleDateFormat(f, Locale.CHINA).format(t.getTime());
    }
}
