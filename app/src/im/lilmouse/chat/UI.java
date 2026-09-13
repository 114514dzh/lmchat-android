package im.lilmouse.chat;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class UI {
    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
    public static boolean dark(Context c) {
        int m = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }
    /** Material You 动态主色，低版本回退 Telegram 蓝 */
    public static int accent(Context c) {
        int custom = P.accentPref(c);
        if (custom != 0) return custom;
        if (Build.VERSION.SDK_INT >= 31) return c.getColor(android.R.color.system_accent1_600);
        return dark(c) ? 0xFF8AB4F8 : 0xFF0088CC;
    }

    /** 顶栏色：把主题色加深到白字可读的程度（浅色主题色会迭代加深） */
    public static int accentHeader(Context c) {
        int d = accent(c);
        for (int i = 0; i < 8 && isLight(d); i++) d = blend(0xFF000000, d, 0.18f);
        return d;
    }

    /** 与背景混合，用于自定义主色下的发送气泡 */
    public static int blend(int fg, int bg, float ratio) {
        int r = (int) (((fg >> 16) & 0xff) * ratio + ((bg >> 16) & 0xff) * (1 - ratio));
        int g = (int) (((fg >> 8) & 0xff) * ratio + ((bg >> 8) & 0xff) * (1 - ratio));
        int b = (int) ((fg & 0xff) * ratio + (bg & 0xff) * (1 - ratio));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    public static int surface(Context c)  { return dark(c) ? 0xFF1E1F22 : 0xFFFFFFFF; }
    public static int background(Context c) { return dark(c) ? 0xFF0E1114 : 0xFFF1F3F6; }
    public static int textMain(Context c) { return dark(c) ? 0xFFF2F2F2 : 0xFF1C1E21; }
    public static int textSub(Context c)  { return dark(c) ? 0xFF9AA0A6 : 0xFF707579; }
    public static int outBubble(Context c) {
        int custom = P.accentPref(c);
        if (custom != 0) return blend(custom, dark(c) ? 0xFF1E1F22 : 0xFFFFFFFF, dark(c) ? 0.45f : 0.20f);
        if (Build.VERSION.SDK_INT >= 31) return c.getColor(android.R.color.system_accent3_100);
        return dark(c) ? 0xFF2B5278 : 0xFFEFFDDE;
    }
    public static int inBubble(Context c) { return surface(c); }

    public static RippleDrawable ripple(int base) {
        return new RippleDrawable(ColorStateList.valueOf(0x33000000), new ColorDrawable(base), null);
    }
    public static RippleDrawable rippleOnly() {
        return new RippleDrawable(ColorStateList.valueOf(0x22000000), null, new ColorDrawable(0xFFFFFFFF));
    }

    private static final int[] PALETTE = {
            0xFFE17076, 0xFF7BC862, 0xFF65AADD, 0xFFA695E7, 0xFFEE7AAE, 0xFFFAA774, 0xFF6EC9CB
    };
    public static int hashColor(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) h = h * 31 + s.charAt(i);
        return PALETTE[Math.abs(h) % PALETTE.length];
    }

    public static GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }
    public static GradientDrawable round(Context c, int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** Telegram 风格圆头像（取首字母 + 按字符串哈希定色） */
    public static TextView avatar(Context c, String name, String id, int sizeDp) {
        TextView t = new TextView(c);
        if (name == null || name.length() == 0) name = id == null ? "?" : id.substring(0, 1);
        String letter = name.substring(0, 1).toUpperCase();
        t.setText(letter);
        t.setTextColor(0xFFFFFFFF);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(17);
        t.setGravity(Gravity.CENTER);
        t.setBackground(circle(hashColor(id == null || id.length() == 0 ? "?" : id)));
        int px = dp(c, sizeDp);
        t.setWidth(px);
        t.setHeight(px);
        return t;
    }

    /** 主题主按钮(统一样式, 减少各界面重复手写) */
    public static TextView primaryBtn(final Context c, String text, View.OnClickListener l) {
        TextView b = new TextView(c);
        b.setText(text);
        b.setTextSize(16);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setTextColor(0xFFFFFFFF);
        b.setGravity(Gravity.CENTER);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF),
                round(c, accent(c), 26), null));
        int pad = dp(c, 15);
        b.setPadding(pad, pad, pad, pad);
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    /** 圆角输入框 */
    public static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setTextSize(15);
        e.setTextColor(textMain(c));
        e.setHintTextColor(textSub(c));
        e.setBackground(round(c, surface(c), 16));
        int p = dp(c, 12);
        e.setPadding(p, p, p, p);
        if (hint != null) e.setHint(hint);
        return e;
    }
    public static TextView label(Context c, String s, int color, float size, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** 有头像图则圆切显示，否则字母头像 */
    public static View avatar(Context c, Bitmap bmp, String name, String id, int sizeDp) {
        if (name == null || name.length() == 0) name = id == null ? "?" : id.substring(0, 1);
        if (bmp == null) return avatar(c, name, id, sizeDp);
        ImageView iv = new ImageView(c);
        iv.setImageBitmap(bmp);
        int px = dp(c, sizeDp);
        iv.setLayoutParams(new ViewGroup.LayoutParams(px, px));   // 严格正方形
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);         // 裁剪不变形
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

    /** 亮度判断：>0.6 视为浅色，需要深色图标 */
    private static boolean isLight(int c) {
        int r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0 > 0.6;
    }

    /**
     * 状态栏/导航栏染成与当前界面背景同色，并自动切换图标明暗。
     * 状态栏染色 API21+，深色图标 API23+，导航栏图标 API26+（本应用 minSdk 23，全覆盖）。
     */
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
     * 系统栏跟随背景：Android 11+ 自己在状态栏/导航栏区域画色块
     * （Android 15 起 setStatusBarColor 对 targetSdk35 已失效，必须自绘）；
     * Android 6-10 沿用 setStatusBarColor + 内边距。
     */
    public static View wrap(Activity a, View content, int topColor, int bottomColor) {
        boolean lightTop = isLight(topColor), lightBottom = isLight(bottomColor);
        Window w = a.getWindow();
        if (Build.VERSION.SDK_INT < 30) {
            // 安卓6-10：状态栏=顶栏色；导航栏=页面底色(与页面融为一体,不再出现蓝色底条)
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            try {
                w.setStatusBarColor(topColor);
                if (Build.VERSION.SDK_INT >= 21) w.setNavigationBarColor(bottomColor);
            } catch (Throwable t) {}
            View dv2 = w.getDecorView();
            int fl = dv2.getSystemUiVisibility();
            if (isLight(topColor)) fl |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else fl &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                if (isLight(bottomColor)) fl |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
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
        // Android11+ 手势导航: 底栏区透明, 不留无用色条; 三键导航才着色(按钮可见)
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

    /** 让根布局避开状态栏/导航栏/键盘（Android 15 强制 edge-to-edge 的兼容处理） */
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
