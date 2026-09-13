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

    /** 主题色：0 = 跟随系统(Material You / 默认蓝) */
    public static final int[] ACCENTS = {
            0, 0xFF0088CC, 0xFF3F51B5, 0xFF7E57C2, 0xFFE91E63,
            0xFFEF6C00, 0xFF2E7D32, 0xFF00897B, 0xFF546E7A
    };
    public static final String[] ACCENT_NAMES = {
            "跟随系统", "电报蓝", "靛蓝", "紫罗兰", "玫红",
            "落日橙", "森林绿", "青碧", "石墨灰"
    };
    public static int accentPref(Context c) { return sp(c).getInt("accent", 0); }
    public static void setAccent(Context c, int argb) { sp(c).edit().putInt("accent", argb).commit(); }

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
