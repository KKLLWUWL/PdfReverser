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

    /** 列出 treeUri 直接子级中的 PDF 文件 */
    public static List<Pdf> listPdfs(Context ctx, Uri treeUri) {
        List<Pdf> out = new ArrayList<>();
        try {
            ContentResolver cr = ctx.getContentResolver();
            String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId);
            String[] cols = { DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                              DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                              DocumentsContract.Document.COLUMN_MIME_TYPE };
            Cursor c = cr.query(children, cols, null, null, null);
            if (c == null) return out;
            while (c.moveToNext()) {
                String id = c.getString(0);
                String name = c.getString(1);
                String mime = c.getString(2);
                boolean isPdf = (mime != null && mime.equalsIgnoreCase("application/pdf"))
                        || (name != null && name.toLowerCase().endsWith(".pdf"));
                if (isPdf) {
                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                    out.add(new Pdf(name, docUri));
                }
            }
            c.close();
        } catch (Exception ignore) { }
        java.util.Collections.sort(out, new java.util.Comparator<Pdf>() {
            @Override public int compare(Pdf a, Pdf b) { return a.name.compareToIgnoreCase(b.name); }
        });
        return out;
    }

    /** 生成文件选择器可预定位到该目录的 document URI（供 EXTRA_INITIAL_URI 使用） */
    public static Uri treeAsDocumentUri(Uri treeUri) {
        try {
            String treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId);
        } catch (Exception e) {
            return null;
        }
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
