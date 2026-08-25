package com.lingxi.pdfreverser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * 设置页：选择常用文件夹（2 个槽位）、选择发送目标 APP。
 */
public class SettingsActivity extends Activity {

    private static final int REQ_FOLDER = 200;

    private SettingsStore settings;
    private LinearLayout root;
    private TextView folder1, folder2, targetText;
    private int pendingSlot = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new SettingsStore(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF5F5F7);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);

        addTitle("设置");
        addSection("常用文件夹（首页固定展示，可直接点击更换）");
        folder1 = addRow("文件夹 1", "");
        folder2 = addRow("文件夹 2", "");
        folder1.setOnClickListener(pick(1));
        folder2.setOnClickListener(pick(2));

        addSection("发送目标 APP（重排后直接发送，无需确认）");
        targetText = addRow("目标 APP", "未设置（首次发送会弹出选择）");
        targetText.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickTargetApp(); }
        });

        addSection("说明");
        TextView note = new TextView(this);
        note.setText("· PDF 超过 1 页时按倒序重排，仅调整页面顺序，\n  不改变任何内容与清晰度；单页 PDF 直接发送。\n· 重排后的文件为临时文件，不会保存在手机上。\n· 用法：打印机顺序出纸时，倒序文件打印后即为正确顺序。");
        note.setTextSize(13);
        note.setTextColor(0xFF888899);
        note.setLineSpacing(dp(4), 1f);
        root.addView(note);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        folder1.setText(folderLabel(1));
        folder2.setText(folderLabel(2));
        String[] t = settings.getTargetApp();
        targetText.setText(t == null ? "未设置（首次发送会弹出选择）" : appLabel(t[0], t[1]));
    }

    private String folderLabel(int slot) {
        Uri u = settings.getFolder(slot);
        return u == null ? "未设置（点击选择）" : SafFiles.treeName(this, u);
    }

    private View.OnClickListener pick(final int slot) {
        return new View.OnClickListener() {
            @Override public void onClick(View v) {
                pendingSlot = slot;
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_FOLDER);
            }
        };
    }

    private void pickTargetApp() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/pdf");
        final List<ResolveInfo> apps = getPackageManager().queryIntentActivities(send, 0);
        if (apps.isEmpty()) {
            new AlertDialog.Builder(this).setMessage("未找到可接收 PDF 的应用")
                    .setPositiveButton("好", null).show();
            return;
        }
        String[] names = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            names[i] = apps.get(i).loadLabel(getPackageManager()).toString();
        }
        new AlertDialog.Builder(this)
                .setTitle("选择发送目标")
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        ResolveInfo r = apps.get(which);
                        settings.setTargetApp(r.activityInfo.packageName, r.activityInfo.name);
                        refresh();
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

    // ---------- UI 辅助 ----------
    private void addTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(28);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFF1D1D1F);
        t.setPadding(0, dp(8), 0, dp(12));
        root.addView(t);
    }

    private void addSection(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(0xFF666677);
        t.setPadding(0, dp(14), 0, dp(6));
        root.addView(t);
    }

    private TextView addRow(String key, String val) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ((ViewGroup.MarginLayoutParams) row.getLayoutParams()).bottomMargin = dp(8);

        TextView k = new TextView(this);
        k.setText(key);
        k.setTextSize(13);
        k.setTextColor(0xFF666677);
        row.addView(k);

        TextView v = new TextView(this);
        v.setText(val);
        v.setTextSize(16);
        v.setTextColor(0xFF1D1D1F);
        v.setPadding(0, dp(3), 0, 0);
        row.addView(v);

        root.addView(row);
        return v;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FOLDER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri tree = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(tree,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignore) { }
            settings.setFolder(pendingSlot, tree);
            refresh();
        }
    }
}
