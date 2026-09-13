package im.lilmouse.chat;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/** 崩溃捕获：把最后一次异常栈写到本地，随"复制诊断信息"发出 */
public class CrashLog {
    public static void install(final Context c) {
        try {
            final Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(Thread t, Throwable e) {
                    try {
                        File f = new File(c.getFilesDir(), "crash.txt");
                        StringWriter sw = new StringWriter();
                        e.printStackTrace(new PrintWriter(sw));
                        FileOutputStream fo = new FileOutputStream(f);
                        fo.write(("time=" + System.currentTimeMillis() + "\nthread=" + t.getName() + "\n" + sw).getBytes());
                        fo.close();
                    } catch (Throwable t2) {}
                    if (def != null) def.uncaughtException(t, e);
                }
            });
        } catch (Throwable t) {}
    }
    public static String read(Context c) {
        try {
            File f = new File(c.getFilesDir(), "crash.txt");
            if (!f.exists()) return "";
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] b = new byte[(int) f.length()];
            in.read(b); in.close();
            return new String(b, "UTF-8");
        } catch (Throwable t) { return ""; }
    }
}
