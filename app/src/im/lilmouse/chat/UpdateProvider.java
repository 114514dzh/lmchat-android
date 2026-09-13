package im.lilmouse.chat;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * 迷你 FileProvider（对齐 AndroidX FileProvider 行为）：
 * 必须实现 query() 返回 _display_name / _size，否则部分 ROM 的安装器
 * 拿不到包信息会直接报「解析软件包时出现问题」。
 */
public class UpdateProvider extends ContentProvider {
    public static final String AUTH = "im.lilmouse.chat.updates";
    public static final String BASE = "content://" + AUTH + "/";
    private static final String MIME = "application/vnd.android.package-archive";

    public boolean onCreate() { return true; }

    private File resolve(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || !name.endsWith(".apk")) throw new FileNotFoundException("bad name");
        File dir = new File(getContext().getFilesDir(), "update");
        File f = new File(dir, name);
        try {
            if (!f.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator))
                throw new FileNotFoundException("outside");
        } catch (java.io.IOException e) {
            throw new FileNotFoundException("io");
        }
        if (!f.exists()) throw new FileNotFoundException("missing");
        return f;
    }

    public String getType(Uri uri) { return MIME; }

    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** 安装器会 query 文件名与大小；返回 null 会导致解析失败 */
    public Cursor query(Uri uri, String[] projection, String sel, String[] selArgs, String order) {
        File f;
        try { f = resolve(uri); } catch (FileNotFoundException e) { return null; }
        String[] cols = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        Object[] vals = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) vals[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) vals[i] = Long.valueOf(f.length());
            else if ("_data".equals(cols[i])) vals[i] = f.getAbsolutePath();
            else if ("mime_type".equals(cols[i])) vals[i] = MIME;
            else vals[i] = null;
        }
        MatrixCursor c = new MatrixCursor(cols, 1);
        c.addRow(vals);
        return c;
    }

    public Uri insert(Uri uri, ContentValues v) { throw new UnsupportedOperationException(); }
    public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }
    public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
