package im.lilmouse.chat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import java.io.File;

/**
 * 更新流程参考 ab-download-manager（Android 端）：
 *   拉清单 → 展示更新说明 → 应用内自行下载到私有目录 → 自家 FileProvider + ACTION_VIEW 交给系统安装器。
 * 相比 DownloadManager 方案，文件与 URI 都由本应用掌握，避开各 ROM 下载器差异。
 */
public class Updater {
    public static class Info { public int code; public String name; public String url; public String notes; public String md5; }
    public interface Cb { void on(boolean newer, Info i, String err); }

    public static void check(final Context c, final boolean silent, final Cb cb) {
        P.POOL.execute(new Runnable() {
            public void run() {
                if (P.siteRoot(c).length() == 0) {   // 未配置服务器地址时不做更新检查
                    if (cb != null) cb.on(false, null, "未配置服务器地址");
                    return;
                }
                try {
                    JsonObject o = Json.parse(Api.get(P.updateManifest(c), null)).asObject();
                    final Info i = new Info();
                    i.code = o.getInt("code", 0);
                    i.name = o.getString("name", "");
                    i.url = o.getString("url", P.updateApk(c));
                    i.notes = o.getString("notes", "");
                    i.md5 = o.getString("md5", "");
                    int cur = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
                    final boolean newer = i.code > cur;
                    if (newer && silent) startDownload(c, i);
                    if (cb != null) cb.on(newer, i, null);
                } catch (final Exception e) {
                    if (cb != null) cb.on(false, null, e.getMessage());
                }
            }
        });
    }

    /** 手动检查：弹出更新说明，让用户选择应用内更新或浏览器下载 */
    public static void promptOrToast(final Activity a) {
        Toast.makeText(a, "检查中…", Toast.LENGTH_SHORT).show();
        check(a, false, new Cb() {
            public void on(final boolean newer, final Info i, final String err) {
                a.runOnUiThread(new Runnable() {
                    public void run() {
                        if (err != null) { Toast.makeText(a, "检查失败: " + err, Toast.LENGTH_SHORT).show(); return; }
                        if (!newer) { Toast.makeText(a, "已是最新版本", Toast.LENGTH_SHORT).show(); return; }
                        showDialog(a, i);
                    }
                });
            }
        });
    }

    public static void showDialog(final Activity a, final Info i) {
        new AlertDialog.Builder(a)
                .setTitle("发现新版本 " + i.name)
                .setMessage(i.notes == null || i.notes.length() == 0 ? "点击“立即更新”开始下载。" : i.notes)
                .setPositiveButton("立即更新", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        startDownload(a, i);
                        Toast.makeText(a, "开始下载，完成后会提示安装", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("浏览器下载", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { openInBrowser(a); }
                })
                .setNegativeButton("以后再说", null)
                .show();
    }

    public static void openInBrowser(Context c) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(P.updateApk(c)));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Throwable t) {}
    }

    public static void download(Context c, Info i) { startDownload(c, i); }

    /** 应用内自动下载+安装(前台服务下载→点击安装)，恢复原自动更新行为 */
    public static void startDownload(Context c, Info i) {
        try {
            Intent s = new Intent(c, UpdateService.class);
            s.putExtra("url", i.url == null || i.url.length() == 0 ? P.updateApk(c) : i.url);
            s.putExtra("name", i.name);
            s.putExtra("code", i.code);
            s.putExtra("md5", i.md5 == null ? "" : i.md5);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
            else c.startService(s);
        } catch (Throwable t) {}
    }

    public static void resumeInstall(Context c) { installIfReady(c, false); }

    /** 下载完成后由用户点击触发（前台，全 ROM 允许） */
    public static void installIfReady(Context c, boolean manual) {
        try {
            int code = P.sp(c).getInt("updCode", 0);
            if (code == 0) {
                if (manual) Toast.makeText(c, "没有已下载的更新包", Toast.LENGTH_SHORT).show();
                return;
            }
            int cur = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
            File f = new File(new File(c.getFilesDir(), "update"), "lmchat-" + code + ".apk");
            if (code <= cur || !f.exists()) {
                P.sp(c).edit().remove("updCode").commit();
                if (f.exists()) f.delete();
                if (manual) Toast.makeText(c, "已是最新版本", Toast.LENGTH_SHORT).show();
                return;
            }
            if (Build.VERSION.SDK_INT >= 26 && !c.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(c, "请允许 LM Chat 安装应用，返回后自动继续", Toast.LENGTH_LONG).show();
                Intent s = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + c.getPackageName()));
                s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(s);
                return;
            }
            // ab-download-manager 的做法：FileProvider URI + ACTION_VIEW
            Uri u = Uri.parse(UpdateProvider.BASE + f.getName());
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            c.startActivity(i);
        } catch (Throwable t) {
            if (manual) Toast.makeText(c, "安装失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
