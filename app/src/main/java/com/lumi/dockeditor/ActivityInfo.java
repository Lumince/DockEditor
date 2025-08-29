package com.lumi.dockeditor;

public class ActivityInfo {
    public String name;
    public String displayName;
    public boolean isMainActivity;
    
    public ActivityInfo(String name, String displayName, boolean isMainActivity) {
        this.name = name;
        this.displayName = displayName;
        this.isMainActivity = isMainActivity;
    }
    
    public String getDisplayText() {
        if (isMainActivity) {
            return displayName + " (Main)";
        }
        return displayName;
    }
}