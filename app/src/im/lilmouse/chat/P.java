package im.lilmouse.chat;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class P {
    // 真实值来自构建期生成的 Local.java（见 local.properties.example），不入库。
    public static final String DEF_SERVER = Local.SERVER;
    public static final ExecutorService POOL = Executors.newFixedThreadPool(4);

    public static SharedPreferences sp(Context c) { return c.getSharedPreferences("lm", 0); }
    public static boolean hasAccount(Context c) { return sp(c).contains("acct"); }
    public static String acct(Context c) { return sp(c).getString("acct", ""); }
    public static String token(Context c) { return sp(c).getString("token", ""); }
    public static String server(Context c) { return sp(c).getString("server", DEF_SERVER); }

    /**
     * 站点根地址：从"服务器地址"推导，统一去掉 /im-api 后缀。
     *   https://host/im-api  ->  https://host
     * 更新地址由此继续推导，因此域名只在 local.properties 里写一次，
     * 源码与 APK 中都不再重复出现（用户改服务器地址时更新也随之切换）。
     */
    public static String siteRoot(Context c) {
        String s = server(c).trim().replaceAll("/+$", "");
        return s.replaceAll("/im-api$", "");
    }
    /** 更新清单：跟随服务器地址 */
    public static String updateManifest(Context c) { return siteRoot(c) + "/im-update/version.json"; }
    /** 新版本 APK：跟随服务器地址 */
    public static String updateApk(Context c) { return siteRoot(c) + "/im-update/lmchat.apk"; }
    public static String name(Context c) { return sp(c).getString("name", ""); }
    public static String auth(Context c) { return acct(c) + ":" + token(c); }

    // ───── 外观：配色方案 + 明暗模式（见 Palettes / UI）─────
    /** 配色方案 id，见 Palettes.IDS；默认跟随系统动态取色 */
    public static String themeId(Context c) { return sp(c).getString("theme", "system"); }
    public static void setThemeId(Context c, String id) { sp(c).edit().putString("theme", id).commit(); }
    /** 明暗：0 跟随系统 / 1 浅色 / 2 深色 */
    public static int darkMode(Context c) { return sp(c).getInt("darkmode", 0); }
    /** 外观签名：配色方案 + 明暗模式。变化即代表界面需要重建。 */
    public static String themeSig(Context c) { return themeId(c) + "|" + darkMode(c); }
    public static void setDarkMode(Context c, int m) { sp(c).edit().putInt("darkmode", m).commit(); }

    public static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
    public static byte[] slice(byte[] a, int off, int len) {
        byte[] r = new byte[len];
        System.arraycopy(a, off, r, 0, len);
        return r;
    }
    public static String b64(byte[] b) { return Base64.encodeToString(b, Base64.NO_WRAP); }
    public static byte[] unb64(String s) { return Base64.decode(s, Base64.NO_WRAP); }

    public static String randHex(int n) {
        SecureRandom r = new SecureRandom();
        char[] cs = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(cs[r.nextInt(16)]);
        return sb.toString();
    }
}
