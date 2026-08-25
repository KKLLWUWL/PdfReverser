package com.lingxi.pdfreverser;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.CancellationSignal;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * 极简 ContentProvider：从应用 cache 目录向外共享临时文件。
 * （不依赖 androidx FileProvider，保持零依赖、包体最小）
 * URI 形如 content://com.lingxi.pdfreverser.files/<fileName>
 */
public class FileContentProvider extends ContentProvider {

    public static final String AUTHORITY = "com.lingxi.pdfreverser.files";

    /** 写一个临时文件并返回可分享的 content:// URI */
    public static Uri shareFile(Context ctx, String fileName, byte[] data) {
        try {
            File f = new File(ctx.getCacheDir(), fileName);
            OutputStream os = new FileOutputStream(f);
            os.write(data);
            os.close();
            return Uri.parse("content://" + AUTHORITY + "/" + fileName);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null) throw new FileNotFoundException();
        File f = new File(getContext().getCacheDir(), name);
        if (!f.exists()) throw new FileNotFoundException("缓存文件不存在");
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "application/pdf";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) { return null; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder, CancellationSignal cs) { return null; }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
