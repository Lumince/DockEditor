package com.lumi.dockeditor;

import android.graphics.drawable.Drawable;

public class InstalledAppInfo {
    public String packageName;
    public String appName;
    public Drawable icon;
    
    public InstalledAppInfo(String packageName, String appName, Drawable icon) {
        this.packageName = packageName;
        this.appName = appName;
        this.icon = icon;
    }
}