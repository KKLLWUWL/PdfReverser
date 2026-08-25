package com.lingxi.pdfreverser;

import android.app.Activity;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页：展示 1-2 个常用文件夹内的 PDF，
 * 点击即倒序重排并直接发送到设置的目标 APP。
 */
public class MainActivity extends Activity {

    private static final int REQ_PICK_FOLDER_1 = 101;
    private static final int REQ_PICK_FOLDER_2 = 102;

    private SettingsStore settings;
    private LinearLayout content;
    private View progressOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new SettingsStore(this);
        setContentView(R.layout.activity_main);

        content = findViewById(R.id.content);
        progressOverlay = findViewById(R.id.progressOverlay);

        findViewById(R.id.btnSettings).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuildFolders();
    }

    private void rebuildFolders() {
        content.removeAllViews();
        for (int slot = 1; slot <= SettingsStore.FOLDER_SLOTS; slot++) {
            content.addView(buildFolderBlock(slot));
        }
        if (!settings.hasFolders()) {
            TextView tip = new TextView(this);
            tip.setText("请先在上方齿轮「设置」中选择 1-2 个常用文件夹，\n例如「下载」「文档」或专门的打印目录。");
            tip.setTextSize(13);
            tip.setTextColor(0xFF999999);
            int p = dp(16);
            tip.setPadding(p, p, p, p);
            tip.setGravity(android.view.Gravity.CENTER);
            content.addView(tip, 0);
        }
    }

    private View buildFolderBlock(final int slot) {
        final Uri treeUri = settings.getFolder(slot);

        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);

        // 头部：文件夹名 + 更换
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(8), dp(12), dp(8));

        TextView name = new TextView(this);
        name.setTextSize(17);
        name.setTextColor(0xFF1D1D1F);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        name.setText(slot + " · " + folderName(treeUri));
        head.addView(name);

        Button change = new Button(this);
        change.setText(treeUri == null ? "选择" : "更换");
        change.setTextSize(12);
        change.setTextColor(0xFF0071E3);
        change.setBackgroundResource(R.drawable.bg_ghost_btn);
        change.setPadding(dp(12), dp(4), dp(12), dp(4));
        change.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, slot == 1 ? REQ_PICK_FOLDER_1 : REQ_PICK_FOLDER_2);
            }
        });
        head.addView(change);
        block.addView(head);

        if (treeUri == null) {
            TextView empty = new TextView(this);
            empty.setText("未设置文件夹，点击右侧「选择」添加");
            empty.setTextSize(13);
            empty.setTextColor(0xFF999999);
            empty.setPadding(dp(16), dp(4), dp(16), dp(16));
            block.addView(empty);
        } else {
            List<PdfEntry> pdfs = listPdfs(treeUri);
            if (pdfs.isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText("该文件夹内没有 PDF 文件");
                empty.setTextSize(13);
                empty.setTextColor(0xFF999999);
                empty.setPadding(dp(16), dp(4), dp(16), dp(16));
                block.addView(empty);
            } else {
                ListView list = new ListView(this);
                list.setDivider(null);
                list.setPadding(dp(12), 0, dp(12), dp(4));
                list.setAdapter(new PdfAdapter(pdfs));
                list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
                    @Override public void onItemClick(android.widget.AdapterView<?> parent, View view,
                                                      int position, long id) {
                        PdfEntry e = (PdfEntry) parent.getItemAtPosition(position);
                        processAndSend(e);
                    }
                });
                block.addView(list);
            }
        }
        return block;
    }

    private String folderName(Uri treeUri) {
        return SafFiles.treeName(this, treeUri);
    }

    private static class PdfEntry {
        final String name;
        final Uri uri;
        PdfEntry(String name, Uri uri) { this.name = name; this.uri = uri; }
    }

    private List<PdfEntry> listPdfs(Uri treeUri) {
        List<PdfEntry> out = new ArrayList<>();
        for (SafFiles.Pdf p : SafFiles.listPdfs(this, treeUri)) {
            out.add(new PdfEntry(p.name, p.uri));
        }
        return out;
    }

    private class PdfAdapter extends BaseAdapter {
        final List<PdfEntry> items;
        PdfAdapter(List<PdfEntry> items) { this.items = items; }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int p) { return items.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView tv;
            if (convertView == null) {
                row = new LinearLayout(MainActivity.this);
                row.setBackgroundResource(R.drawable.bg_card);
                row.setPadding(dp(14), dp(14), dp(14), dp(14));
                tv = new TextView(MainActivity.this);
                tv.setTextSize(15);
                tv.setTextColor(0xFF1D1D1F);
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                row.addView(tv);
                ViewGroup.MarginLayoutParams mp = new ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                mp.bottomMargin = dp(8);
                row.setLayoutParams(mp);
            } else {
                row = (LinearLayout) convertView;
                tv = (TextView) row.getChildAt(0);
            }
            tv.setText(items.get(position).name);
            return row;
        }
    }

    // ---------- 处理并发送 ----------
    private void processAndSend(PdfEntry entry) {
        showProgress(true);
        final byte[] input;
        try {
            java.io.InputStream is = getContentResolver().openInputStream(entry.uri);
            if (is == null) { showProgress(false); toast("无法读取文件"); return; }
            input = readAll(is);
            is.close();
        } catch (Exception e) {
            showProgress(false);
            toast("读取失败：" + e.getMessage());
            return;
        }

        PdfEngine.get(this).process(input, new PdfEngine.Callback() {
            @Override public void onDone(byte[] outBytes, int pageCount) {
                showProgress(false);
                byte[] toSend = (outBytes == null) ? input : outBytes;
                String fileName = baseName(entry.name);
                Uri shareUri = FileContentProvider.shareFile(MainActivity.this,
                        fileName, toSend);
                if (shareUri == null) { toast("生成临时文件失败"); return; }
                sendToApp(shareUri, fileName, pageCount);
                cleanupCacheExcept(fileName);
            }

            @Override public void onError(String message) {
                showProgress(false);
                toast("处理失败：" + message);
            }
        });
    }

    private void sendToApp(Uri uri, String fileName, int pageCount) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/pdf");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(ClipData.newRawUri("", uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        String[] target = settings.getTargetApp();
        String action = (pageCount > 1 ? "倒序重排后" : "单页无需重排，") + "发送 " + fileName;
        if (target != null) {
            try {
                send.setComponent(new ComponentName(target[0], target[1]));
                startActivity(send);
                toast(action);
                return;
            } catch (Exception ignore) {
                // 目标失效则退回系统选择
            }
        }
        startActivity(Intent.createChooser(send, action));
    }

    private void cleanupCacheExcept(String keep) {
        try {
            File dir = getCacheDir();
            for (File f : dir.listFiles()) {
                if (f.isFile() && !f.getName().equals(keep)) f.delete();
            }
        } catch (Exception ignore) { }
    }

    // ---------- 工具 ----------
    private byte[] readAll(java.io.InputStream is) throws java.io.IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[65536];
        int n;
        while ((n = is.read(b)) != -1) buf.write(b, 0, n);
        return buf.toByteArray();
    }

    private String baseName(String name) {
        if (name == null || name.isEmpty()) name = "print.pdf";
        String stem = name.endsWith(".pdf") || name.endsWith(".PDF")
                ? name.substring(0, name.length() - 4) : name;
        return stem + "_reverse.pdf";
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void showProgress(boolean show) {
        progressOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignore) { }
        int slot = (requestCode == REQ_PICK_FOLDER_1) ? 1 : 2;
        settings.setFolder(slot, tree);
        rebuildFolders();
    }
}
