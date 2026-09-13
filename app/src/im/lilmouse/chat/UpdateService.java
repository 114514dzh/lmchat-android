package im.lilmouse.chat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/** 前台服务直下更新包：断点续传 + MD5 校验 + 自动重试 ×2，通过才标记就绪 */
public class UpdateService extends Service {
    public static final int NID_PROGRESS = 20;
    public static final int NID_READY = 21;
    public static final int NID_FAIL = 22;
    private final Handler h = new Handler();

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int f, int id) {
        if (i == null) { stopSelf(); return START_NOT_STICKY; }
        String url = i.getStringExtra("url");
        String name = i.getStringExtra("name");
        int code = i.getIntExtra("code", 0);
        String md5 = i.getStringExtra("md5");
        int attempt = i.getIntExtra("attempt", 0);
        if (code == 0 || url == null) { stopSelf(); return START_NOT_STICKY; }
        startForeground(NID_PROGRESS, notif("下载更新 " + name, "连接中…"));
        final String fUrl = url, fName = name, fMd5 = md5;
        final int fCode = code, fAttempt = attempt;
        P.POOL.execute(new Runnable() { public void run() { download(fUrl, fName, fCode, fMd5, fAttempt); } });
        return START_NOT_STICKY;
    }

    @SuppressWarnings("deprecation")
    private Notification notif(String t, String b) {
        Notification.Builder n = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, MsgService.CH_SVC)
                : new Notification.Builder(this);
        n.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(t).setContentText(b).setOngoing(true);
        return n.build();
    }

    private void download(String url, String name, int code, String md5, int attempt) {
        File dir = new File(getFilesDir(), "update");
        if (!dir.exists()) dir.mkdirs();
        File part = new File(dir, "part-" + code + ".apk");
        HttpURLConnection c = null;
        try {
            long done = part.exists() ? part.length() : 0;
            long total = -1;
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(30000);
            if (done > 0) c.setRequestProperty("Range", "bytes=" + done + "-");
            int resp = c.getResponseCode();
            if (resp == 416) { part.delete(); done = 0; c.disconnect(); c = null;
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000); c.setReadTimeout(30000); resp = c.getResponseCode(); }
            long remaining = c.getContentLength();
            if (resp == 200) { part.delete(); done = 0; total = remaining; }
            else if (resp == 206) { total = done + remaining; }
            else throw new Exception("HTTP " + resp);
            InputStream in = c.getInputStream();
            RandomAccessFile raf = new RandomAccessFile(part, "rw");
            raf.seek(done);
            byte[] buf = new byte[65536];
            int n;
            int lastPct = -1;
            while ((n = in.read(buf)) > 0) {
                raf.write(buf, 0, n);
                done += n;
                if (total > 0) {
                    int pct = (int) (done * 100 / total);
                    if (pct != lastPct && pct % 10 == 0) {
                        lastPct = pct;
                        notifyProgress(name, pct);
                    }
                }
            }
            raf.close();
            in.close();
            c.disconnect();

            // 完整性：MD5 优先，无则长度比对
            boolean ok = false;
            if (md5 != null && md5.length() > 0) {
                String got = md5(part);
                ok = md5.equalsIgnoreCase(got);
            } else {
                ok = total <= 0 || done >= total;
            }
            if (!ok) throw new Exception("integrity-fail");

            File out = new File(dir, "lmchat-" + code + ".apk");
            part.renameTo(out);
            P.sp(this).edit().putInt("updCode", code).commit();
            ready(name);
        } catch (final Throwable t) {
            try { if (c != null) c.disconnect(); } catch (Throwable t2) {}
            if (attempt < 2) {
                final String fUrl = url, fName = name; final String fMd5 = md5;
                final int fCode = code, fAttempt = attempt;
                h.postDelayed(new Runnable() { public void run() {
                    Intent it = new Intent(UpdateService.this, UpdateService.class);
                    it.putExtra("url", fUrl).putExtra("name", fName).putExtra("code", fCode)
                      .putExtra("md5", fMd5).putExtra("attempt", fAttempt + 1);
                    try {
                        if (Build.VERSION.SDK_INT >= 26) startForegroundService(it);
                        else startService(it);
                    } catch (Throwable t2) {}
                }}, 3000);
            } else {
                stopForeground(true);
                try {
                    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    Notification.Builder n = Build.VERSION.SDK_INT >= 26
                            ? new Notification.Builder(this, MsgService.CH_MSG)
                            : new Notification.Builder(this);
                    Intent it = new Intent(this, Conversations.class);
                    n.setSmallIcon(android.R.drawable.stat_notify_error).setContentTitle("更新下载失败")
                            .setContentText("网络不稳定，请稍后在 设置→检查更新 重试")
                            .setAutoCancel(true)
                            .setContentIntent(PendingIntent.getActivity(this, 1, it,
                                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
                    nm.notify(NID_FAIL, n.build());
                } catch (Throwable t2) {}
                stopSelf();
            }
        }
    }

    private void notifyProgress(String name, int pct) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NID_PROGRESS, notif("下载更新 " + name, pct + "%"));
        } catch (Throwable t) {}
    }

    private void ready(String name) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            Notification.Builder n = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, MsgService.CH_MSG)
                    : new Notification.Builder(this);
            Intent it = new Intent(this, Conversations.class);
            n.setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("新版本 " + name + " 已就绪")
                    .setContentText("点击安装")
                    .setAutoCancel(true)
                    .setContentIntent(PendingIntent.getActivity(this, 0, it,
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            nm.notify(NID_READY, n.build());
        } catch (Throwable t) {}
        stopForeground(true);
        stopSelf();
    }

    private static String md5(File f) throws Exception {
        MessageDigest d = MessageDigest.getInstance("MD5");
        InputStream is = new java.io.FileInputStream(f);
        byte[] buf = new byte[65536];
        int n;
        while ((n = is.read(buf)) > 0) d.update(buf, 0, n);
        is.close();
        StringBuilder sb = new StringBuilder();
        for (byte b : d.digest()) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }
}
