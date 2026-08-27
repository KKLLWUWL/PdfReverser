package com.lingxi.pdfreverser;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.util.ArrayList;
import java.util.List;

/**
 * 零依赖 SAF（Storage Access Framework）辅助：
 * 直接用 DocumentsContract 列出文件夹内 PDF，避免引入 androidx。
 */
public class SafFiles {

    public static class Pdf {
        public final String name;
        public final Uri uri;
        public Pdf(String name, Uri uri) { this.name = name; this.uri = uri; }
    }

    /** 列出 treeUri 直接子级中的 PDF 文件（按修改时间倒序，最新在上） */
    public static List<Pdf> listPdfs(Context ctx, Uri treeUri) {
        List<Pdf> out = new ArrayList<>();
        for (Entry e : listChildren(ctx, treeUri, null)) {
            if (!e.dir) out.add(new Pdf(e.name, e.uri));
        }
        return out;
    }


    /** 目录项：子目录或 PDF 文件（应用内文件夹浏览器用） */
    public static class Entry {
        public final String name;
        public final String docId;   // DocumentsContract 的 document ID
        public final boolean dir;
        public final Uri uri;
        public final long lastModified;   // 毫秒时间戳，用于按时间倒序
        public Entry(String name, String docId, boolean dir, Uri uri, long lastModified) {
            this.name = name; this.docId = docId; this.dir = dir; this.uri = uri;
            this.lastModified = lastModified;
        }
    }

    /** 列出 treeUri 下某子目录（parentDocId 为 null/空 = 根）的子目录与 PDF */
    public static List<Entry> listChildren(Context ctx, Uri treeUri, String parentDocId) {
        List<Entry> out = new ArrayList<>();
        try {
            ContentResolver cr = ctx.getContentResolver();
            String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
            String parent = (parentDocId == null || parentDocId.isEmpty())
                    ? treeDocId : parentDocId;
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent);
            String[] cols = { DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                              DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                              DocumentsContract.Document.COLUMN_MIME_TYPE,
                              DocumentsContract.Document.COLUMN_LAST_MODIFIED };
            Cursor c = cr.query(children, cols, null, null, null);
            if (c == null) return out;
            while (c.moveToNext()) {
                String id = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                long last = c.isNull(3) ? 0 : c.getLong(3);
                if (name == null) continue;
                boolean isDir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                boolean isPdf = !isDir && ((mime != null && mime.equalsIgnoreCase("application/pdf"))
                        || name.toLowerCase().endsWith(".pdf"));
                if (isDir || isPdf) {
                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                    out.add(new Entry(name, id, isDir, docUri, last));
                }
            }
            c.close();
        } catch (Exception ignore) { }
        // 目录始终在前（按名称）；PDF 按修改时间倒序，最新在最上方
        java.util.Collections.sort(out, new java.util.Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                if (a.dir != b.dir) return a.dir ? -1 : 1;
                if (!a.dir && !b.dir) {
                    if (a.lastModified != b.lastModified) {
                        return a.lastModified > b.lastModified ? -1 : 1;
                    }
                }
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return out;
    }

    /** 取树根目录的显示名 */
    public static String treeName(Context ctx, Uri treeUri) {
        if (treeUri == null) return "未设置";
        try {
            ContentResolver cr = ctx.getContentResolver();
            String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId);
            Cursor c = cr.query(doc, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null);
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                c.close();
                if (n != null && !n.isEmpty()) return n;
            }
        } catch (Exception ignore) { }
        return "已选择文件夹";
    }
}
