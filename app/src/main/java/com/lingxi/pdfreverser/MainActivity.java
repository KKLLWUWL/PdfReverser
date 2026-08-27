package com.lingxi.pdfreverser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页：
 *  - 1-2 个常用文件夹卡片：快捷列出其中 PDF；也可点击跳转到系统文件选择器，
 *    预定位到该文件夹，在整棵目录树中选择任意 PDF。
 *  - 两个文件夹下方是「选择打开方式」按钮：固定一个可接收 PDF 的目标 APP，
 *    之后点 PDF 即直接发送，无需确认。
 *  - 点击 PDF → 结构级倒序重排 → 直接发送到目标 APP。
 */
public class MainActivity extends Activity {

    private static final int REQ_PICK_FOLDER_1 = 101;
    private static final int REQ_PICK_FOLDER_2 = 102;
    private static final int REQ_PICK_FILE_1 = 201;
    private static final int REQ_PICK_FILE_2 = 202;

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    /** 重建：两个文件夹卡片 + 「选择打开方式」按钮 + 说明 */
    private void rebuild() {
        content.removeAllViews();
        for (int slot = 1; slot <= SettingsStore.FOLDER_SLOTS; slot++) {
            content.addView(buildFolderBlock(slot));
        }
        content.addView(buildTargetRow());
        content.addView(buildNote());
    }

    // ---------- 文件夹卡片 ----------
    private View buildFolderBlock(final int slot) {
        final Uri treeUri = settings.getFolder(slot);

        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);

        // 头部：文件夹名 + 选择/更换
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(10), dp(12), dp(4));

        TextView name = new TextView(this);
        name.setTextSize(17);
        name.setTextColor(0xFF1D1D1F);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        name.setText((slot == 1 ? "常用文件夹一" : "常用文件夹二") + " · " + SafFiles.treeName(this, treeUri));
        head.addView(name);

        Button change = new Button(this);
        change.setText(treeUri == null ? "选择" : "更换");
        change.setTextSize(12);
        change.setTextColor(0xFF0071E3);
        change.setBackgroundResource(R.drawable.bg_ghost_btn);
        change.setPadding(dp(12), dp(4), dp(12), dp(4));
        change.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
                        slot == 1 ? REQ_PICK_FOLDER_1 : REQ_PICK_FOLDER_2);
            }
        });
        head.addView(change);
        block.addView(head);

        if (treeUri == null) {
            block.addView(textRow("未设置文件夹，点击右侧「选择」添加"));
        } else {
            // 「在此文件夹中选择 PDF」：应用内浏览器直接进入该文件夹（主按钮，加大）
            Button browse = new Button(this);
            browse.setText("在此文件夹中选择 PDF");
            browse.setTextSize(16);
            browse.setTextColor(0xFFFFFFFF);
            browse.setTypeface(Typeface.DEFAULT_BOLD);
            browse.setBackgroundResource(R.drawable.bg_primary_btn);
            browse.setGravity(Gravity.CENTER);
            browse.setPadding(dp(16), dp(16), dp(16), dp(16));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.setMargins(dp(12), dp(8), dp(12), dp(10));
            browse.setLayoutParams(bp);
            browse.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    openFolderBrowser(slot, treeUri);
                }
            });
            block.addView(browse);

            // 根目录下 PDF 快捷列表
            List<PdfEntry> pdfs = listPdfs(treeUri);
            if (pdfs.isEmpty()) {
                block.addView(textRow("该文件夹根目录暂无 PDF（可用上方按钮进入选择）"));
            } else {
                ListView list = new ListView(this);
                list.setDivider(null);
                list.setPadding(dp(12), 0, dp(12), dp(4));
                // 固定高度：约展示 4 个文件并露出第 5 个，提示可继续滑动
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(252));
                list.setLayoutParams(llp);
                list.setAdapter(new PdfAdapter(pdfs));
                list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        processAndSend((PdfEntry) parent.getItemAtPosition(position));
                    }
                });
                block.addView(list);
            }
        }
        return block;
    }

    private TextView textRow(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(0xFF999999);
        t.setPadding(dp(16), dp(6), dp(16), dp(12));
        return t;
    }

    /** 打开应用内文件夹浏览器（直接进入该常用文件夹，不依赖系统选择器） */
    private void openFolderBrowser(int slot, Uri treeUri) {
        Intent intent = new Intent(this, BrowseActivity.class);
        intent.putExtra(BrowseActivity.EXTRA_TREE, treeUri.toString());
        intent.putExtra(BrowseActivity.EXTRA_TITLE,
                SafFiles.treeName(this, treeUri));
        startActivityForResult(intent, slot == 1 ? REQ_PICK_FILE_1 : REQ_PICK_FILE_2);
    }

    // ---------- 目标 APP 选择（放在两个文件夹下方） ----------
    private View buildTargetRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        ViewGroup.MarginLayoutParams mp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.bottomMargin = dp(12);
        row.setLayoutParams(mp);

        TextView k = new TextView(this);
        k.setText("选择打开方式（固定发送目标 APP）");
        k.setTextSize(13);
        k.setTextColor(0xFF666677);
        row.addView(k);

        String[] t = settings.getTargetApp();
        String cur = t == null ? "未设置 · 点击选择" : appLabel(t[0], t[1]);

        TextView v = new TextView(this);
        v.setText(cur);
        v.setTextSize(17);
        v.setTextColor(t == null ? 0xFF0071E3 : 0xFF1D1D1F);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(3), 0, 0);
        row.addView(v);

        TextView hint = new TextView(this);
        hint.setText("选择后可接收 PDF 的应用之一；之后点 PDF 即直接发送、无需确认；\n想换其他方式时，再点这里重新选择即可。");
        hint.setTextSize(12);
        hint.setTextColor(0xFF999999);
        hint.setPadding(0, dp(6), 0, 0);
        row.addView(hint);

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickTargetApp(); }
        });
        return row;
    }

    private void pickTargetApp() {
        // 合并两类入口（均声明于 Manifest <queries>）：
        // 1) 可「分享/发送」PDF 的应用；2) 可「打开/查看」PDF 的应用（WPS 等多数只注册后者）
        final List<ResolveInfo> apps = new ArrayList<>();
        final List<String> modes = new ArrayList<>();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        PackageManager pm = getPackageManager();

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/pdf");
        for (ResolveInfo r : pm.queryIntentActivities(send, 0)) {
            String key = r.activityInfo.packageName + "/" + r.activityInfo.name;
            if (seen.add(key)) { apps.add(r); modes.add("send"); }
        }
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setType("application/pdf");
        for (ResolveInfo r : pm.queryIntentActivities(view, 0)) {
            String key = r.activityInfo.packageName + "/" + r.activityInfo.name;
            if (seen.add(key)) { apps.add(r); modes.add("view"); }
        }

        if (apps.isEmpty()) {
            new AlertDialog.Builder(this).setMessage("未找到可打开 PDF 的应用")
                    .setPositiveButton("好", null).show();
            return;
        }
        String[] names = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            names[i] = apps.get(i).loadLabel(pm).toString();
        }
        new AlertDialog.Builder(this)
                .setTitle("选择打开方式")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        ResolveInfo r = apps.get(which);
                        settings.setTargetApp(r.activityInfo.packageName,
                                r.activityInfo.name, modes.get(which));
                        rebuild();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String appLabel(String pkg, String act) {
        try {
            ComponentName cn = new ComponentName(pkg, act);
            return getPackageManager().getActivityInfo(cn, 0).loadLabel(getPackageManager()).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private View buildNote() {
        TextView note = new TextView(this);
        note.setText("用法：打印机顺序出纸时，PDF 倒序后打印即为正确顺序。\n· 超过 1 页自动倒序重排，仅调整页序、不损失清晰度；单页直接发送。\n· 重排结果为临时文件，不会保存在手机。");
        note.setTextSize(12);
        note.setTextColor(0xFF999999);
        note.setLineSpacing(dp(4), 1f);
        note.setPadding(dp(16), 0, dp(16), 0);
        return note;
    }

    // ---------- 文件列表 ----------
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
            InputStream is = getContentResolver().openInputStream(entry.uri);
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
                Uri shareUri = FileContentProvider.shareFile(MainActivity.this, fileName, toSend);
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
        String[] target = settings.getTargetApp();   // {pkg, activity, mode}
        String action = (pageCount > 1 ? "倒序重排后" : "单页无需重排，") + "发送 " + fileName;

        if (target != null) {
            try {
                Intent launch;
                if ("view".equals(target[2])) {
                    // 目标以「打开/查看」方式接收（WPS 等多为这类）
                    launch = new Intent(Intent.ACTION_VIEW);
                    launch.setDataAndType(uri, "application/pdf");
                } else {
                    launch = new Intent(Intent.ACTION_SEND);
                    launch.setType("application/pdf");
                    launch.putExtra(Intent.EXTRA_STREAM, uri);
                    launch.setClipData(ClipData.newRawUri("", uri));
                }
                launch.setComponent(new ComponentName(target[0], target[1]));
                launch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(launch);
                toast(action);
                return;
            } catch (Exception ignore) {
                // 目标失效则退回系统选择
            }
        }
        // 未设置目标：系统选择器（兼容 ACTION_SEND 语义）
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/pdf");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(ClipData.newRawUri("", uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
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
    private byte[] readAll(InputStream is) throws java.io.IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
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

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void showProgress(boolean show) {
        progressOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        if (requestCode == REQ_PICK_FOLDER_1 || requestCode == REQ_PICK_FOLDER_2) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignore) { }
            settings.setFolder(requestCode == REQ_PICK_FOLDER_1 ? 1 : 2, uri);
            rebuild();
            return;
        }
        // 文件选择器返回：直接处理所选 PDF
        int slot = (requestCode == REQ_PICK_FILE_1) ? 1 : 2;
        String name = queryDisplayName(uri);
        processAndSend(new PdfEntry(name, uri));
    }

    private String queryDisplayName(Uri uri) {
        String name = "print.pdf";
        try {
            Cursor c = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null && !n.isEmpty()) name = n;
                c.close();
            }
        } catch (Exception ignore) { }
        return name;
    }
}
