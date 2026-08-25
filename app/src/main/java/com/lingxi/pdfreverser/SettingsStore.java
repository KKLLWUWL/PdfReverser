package com.lingxi.pdfreverser;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/** 偏好存储：常用文件夹（2 个固定槽位）+ 目标 APP */
public class SettingsStore {

    private static final String PREFS = "settings";
    private static final String KEY_FOLDER_1 = "folder_1";   // treeUri 字符串
    private static final String KEY_FOLDER_2 = "folder_2";
    private static final String KEY_TARGET_PKG = "target_pkg";
    private static final String KEY_TARGET_ACT = "target_act";

    private final SharedPreferences sp;

    public SettingsStore(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static final int FOLDER_SLOTS = 2;

    public Uri getFolder(int slot) {
        String s = sp.getString(slot == 1 ? KEY_FOLDER_1 : KEY_FOLDER_2, null);
        return s == null ? null : Uri.parse(s);
    }

    public void setFolder(int slot, Uri uri) {
        sp.edit().putString(slot == 1 ? KEY_FOLDER_1 : KEY_FOLDER_2,
                uri == null ? null : uri.toString()).apply();
    }

    public void setTargetApp(String pkg, String activity) {
        sp.edit().putString(KEY_TARGET_PKG, pkg).putString(KEY_TARGET_ACT, activity).apply();
    }

    public String[] getTargetApp() {
        String pkg = sp.getString(KEY_TARGET_PKG, null);
        String act = sp.getString(KEY_TARGET_ACT, null);
        return (pkg == null || act == null) ? null : new String[]{pkg, act};
    }

    public boolean hasFolders() {
        return getFolder(1) != null || getFolder(2) != null;
    }
}
