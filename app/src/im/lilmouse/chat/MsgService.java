package im.lilmouse.chat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import javax.net.ssl.SSLSocketFactory;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class MsgService extends Service {
    public static final String CH_SVC = "svc";
    public static final String CH_MSG = "msg";
    public static final int ST_CONNECTING = 0;
    public static final int ST_ONLINE = 1;
    public static final int ST_OFFLINE = 2;
    /** 全局连接状态：1=WS实时在线 2=断线(轮询兜底) 0=连接中 */
    public static volatile int connState = ST_CONNECTING;
    /** 心跳时间戳：服务/连接活跃时持续刷新，用于检测服务是否存活 */
    public static volatile long heartbeatTs = 0;
    private volatile WebSocketClient ws;
    private volatile boolean stopping = false;
    private volatile boolean loopStarted = false;
    private static final long CHK_INTERVAL = 6L * 3600 * 1000;
    private final android.os.Handler h = new android.os.Handler();
    private final Runnable chkTask = new Runnable() {
        public void run() {
            if (stopping) return;
            autoCheck();
            h.postDelayed(this, CHK_INTERVAL);
        }
    };
    private final Runnable pollTask = new Runnable() {
        public void run() {
            if (stopping) return;
            if (connState != ST_ONLINE) fetchBacklog();   // 在线靠 WS 推送, 断线才轮询(3s)
            h.postDelayed(this, 3 * 1000);
        }
    };

    private void autoCheck() {
        long last = P.sp(this).getLong("lastAutoChk", 0);
        if (System.currentTimeMillis() - last < 30L * 60 * 1000) return;
        P.sp(this).edit().putLong("lastAutoChk", System.currentTimeMillis()).commit();
        try { Updater.check(this, true, null); } catch (Throwable t) {}
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() { super.onCreate(); CrashLog.install(this); chans(); }

    @Override public int onStartCommand(Intent i, int f, int id) {
        startForged();
        if (!loopStarted && P.hasAccount(this)) {
            loopStarted = true;
            P.POOL.execute(new Runnable() { public void run() { loop(); } });
            h.removeCallbacks(chkTask);
            h.postDelayed(chkTask, CHK_INTERVAL);
            h.removeCallbacks(pollTask);
            h.postDelayed(pollTask, 3000);
        }
        return START_STICKY;
    }

    /** 微信式保活：从最近任务划掉后立即重启前台服务，保住 WS 推送 */
    @Override public void onTaskRemoved(Intent rootIntent) {
        try {
            Intent restart = new Intent(getApplicationContext(), MsgService.class);
            restart.setPackage(getPackageName());
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(restart);
            else startService(restart);
        } catch (Throwable t) {}
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        stopping = true;
        try { if (ws != null) ws.close(); } catch (Throwable t) {}
        super.onDestroy();
    }

    public static void stop(Context c) { c.stopService(new Intent(c, MsgService.class)); }

    private void chans() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(CH_SVC, "Service", NotificationManager.IMPORTANCE_MIN));
            nm.createNotificationChannel(new NotificationChannel(CH_MSG, "Messages", NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    @SuppressWarnings("deprecation")
    private void startForged() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH_SVC) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("LM Chat")
                .setContentText("在线")
                .setOngoing(true);
        b.setPriority(Notification.PRIORITY_MIN);
        startForeground(1, b.build());
    }

    private void loop() {
        while (!stopping && P.hasAccount(this)) {
            try { openWs(); } catch (Throwable t) {}
            if (stopping) break;
            try { Thread.sleep(5000L + new Random().nextInt(10000)); } catch (InterruptedException e) { break; }
        }
    }

    private void beat() { heartbeatTs = System.currentTimeMillis(); }

    private void openWs() throws Exception {
        String uri = P.server(this).replaceFirst("^https", "wss")
                + "/v1/ws?auth=" + java.net.URLEncoder.encode(P.auth(this), "UTF-8");
        final CountDownLatch closed = new CountDownLatch(1);
        WebSocketClient c = new WebSocketClient(new URI(uri)) {
            @Override public void onOpen(ServerHandshake h) {
                beat();
                connState = ST_ONLINE;
                try { Crypto.migratePrekeys(MsgService.this); } catch (Throwable t) {}
                try { autoCheck(); } catch (Throwable t2) {}
                fetchBacklog();
            }
            @Override public void onMessage(String m) { beat(); handleWs(m); }
            @Override public void onClose(int code, String reason, boolean remote) { connState = ST_OFFLINE; beat(); closed.countDown(); }
            @Override public void onError(Exception e) { connState = ST_OFFLINE; beat(); }
        };
        if (uri.startsWith("wss")) {
            c.setSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault());
        }
        ws = c;
        // 连接带 8s 超时，避免握手卡死永远"连接中"
        final boolean[] okBox = {false};
        Thread conn = new Thread(new Runnable() {
            public void run() { try { okBox[0] = c.connectBlocking(); } catch (Throwable t) {} }
        }, "ws-connect");
        conn.setDaemon(true);
        conn.start();
        try { conn.join(3000); } catch (InterruptedException ie) { okBox[0] = false; }
        boolean ok = okBox[0];
        if (!ok) { try { c.close(); } catch (Throwable t) {} }
        if (ok) connState = ST_ONLINE; else connState = ST_OFFLINE;
        if (ok) {
            // 连接期间每5秒醒一次刷心跳(空闲无消息回调时也算活着)
            while (!closed.await(5000, java.util.concurrent.TimeUnit.MILLISECONDS)) { beat(); }
        }
        try { c.close(); } catch (Throwable t) {}
    }

    private void handleWs(String m) {
        try {
            JsonObject o = Json.parse(m).asObject();
            if (!"env".equals(o.getString("type", ""))) return;
            long id = o.getLong("id", 0);
            if (processEnv(id, o.getString("payload", ""))) {
                try { if (ws != null) ws.send("{\"type\":\"ack\",\"ids\":[" + id + "]}"); } catch (Throwable t) {}
            }
        } catch (Exception e) {}
    }

    private void fetchBacklog() {
        Sync.pullAll(this);
    }

    private boolean processEnv(long id, String payloadB64) {
        return Sync.process(this, id, payloadB64);
    }

    private static String membersOf(JsonObject o) {
        JsonValue v = o.get("members");
        JsonArray a = v == null ? new JsonArray() : v.asArray();
        StringBuilder sb = new StringBuilder();
        for (JsonValue x : a) {
            if (sb.length() > 0) sb.append(",");
            sb.append(x.asString());
        }
        return sb.toString();
    }

    @SuppressWarnings("deprecation")
    private void notifyMsg(String conv, String title, String body) {
        Chat.poke();
        if (conv.equals(Chat.openConv)) return;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, CH_MSG) : new Notification.Builder(this);
            Intent it = new Intent(this, Chat.class);
            it.putExtra("conv", conv);
            PendingIntent pi = PendingIntent.getActivity(this, conv.hashCode(), it, PendingIntent.FLAG_IMMUTABLE);
            b.setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            nm.notify(conv.hashCode(), b.build());
        } catch (Throwable t) {}
    }
}
