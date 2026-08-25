package com.lingxi.pdfreverser;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 应用内文件夹浏览器：
 * 直接打开常用文件夹（不依赖系统选择器的预定位，厂商 ROM 兼容性可控），
 * 逐级进入子目录，点选 PDF 后回传其 Uri。
 */
public class BrowseActivity extends Activity {

    public static final String EXTRA_TREE = "tree";
    public static final String EXTRA_TITLE = "title";

    private Uri treeUri;
    private String title = "文件夹";
    private String currentDocId = null;          // null/空 = 根目录
    private String currentName = "";
    private final Deque<String[]> backStack = new ArrayDeque<>();  // {docId, name}
    private TextView pathText;
    private ListView list;
    private final List<SafFiles.Entry> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String t = getIntent().getStringExtra(EXTRA_TREE);
        if (t == null) { finish(); return; }
        treeUri = Uri.parse(t);
        String ti = getIntent().getStringExtra(EXTRA_TITLE);
        if (ti != null) title = ti;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF5F5F7);

        // 顶栏：返回 + 标题
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(10), dp(16), dp(10));
        bar.setBackgroundColor(0xFFFFFFFF);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(bar);

        TextView back = new TextView(this);
        back.setText("‹ 返回");
        back.setTextSize(16);
        back.setTextColor(0xFF0071E3);
        back.setPadding(dp(12), dp(8), dp(12), dp(8));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goBack(); }
        });
        bar.addView(back);

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(17);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(0xFF1D1D1F);
        bar.addView(tv, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 路径指示
        pathText = new TextView(this);
        pathText.setTextSize(13);
        pathText.setTextColor(0xFF888899);
        pathText.setPadding(dp(16), dp(10), dp(16), dp(6));
        pathText.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(pathText);

        // 列表
        list = new ListView(this);
        list.setDivider(null);
        list.setPadding(dp(12), 0, dp(12), dp(12));
        list.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(list);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                SafFiles.Entry e = items.get(position);
                if (e.dir) {
                    backStack.push(new String[]{
                            currentDocId == null ? "" : currentDocId, currentName});
                    load(e.docId, e.name);
                } else {
                    Intent result = new Intent();
                    result.setData(e.uri);
                    setResult(RESULT_OK, result);
                    finish();
                }
            }
        });

        setContentView(root);
        load(null, "");
    }

    private void goBack() {
        if (backStack.isEmpty()) { finish(); return; }
        String[] p = backStack.pop();
        load(p[0], p[1]);
    }

    private void load(String docId, String name) {
        if (docId != null && docId.isEmpty()) docId = null;
        currentDocId = docId;
        currentName = name;
        items.clear();
        items.addAll(SafFiles.listChildren(this, treeUri, docId));
        pathText.setText(title + (docId == null ? "" : " / " + name) + "　·　共 "
                + countPdfs() + " 个 PDF");
        list.setAdapter(new RowAdapter());
    }

    private int countPdfs() {
        int n = 0;
        for (SafFiles.Entry e : items) if (!e.dir) n++;
        return n;
    }

    @Override
    public void onBackPressed() {
        if (!backStack.isEmpty()) goBack();
        else super.onBackPressed();
    }

    class RowAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int p) { return items.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView tv;
            if (convertView == null) {
                row = new LinearLayout(BrowseActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackgroundResource(R.drawable.bg_card);
                row.setPadding(dp(14), dp(14), dp(14), dp(14));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(8);
                row.setLayoutParams(lp);
                tv = new TextView(BrowseActivity.this);
                tv.setTextSize(15);
                row.addView(tv, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            } else {
                row = (LinearLayout) convertView;
                tv = (TextView) row.getChildAt(0);
            }
            SafFiles.Entry e = items.get(position);
            if (e.dir) {
                tv.setText("📁 " + e.name + " ›");
                tv.setTextColor(0xFF1D1D1F);
                tv.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                tv.setText(e.name);
                tv.setTextColor(0xFF0071E3);
                tv.setTypeface(Typeface.DEFAULT);
            }
            return row;
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
