package com.lumi.dockeditor;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;

public class AppInfo implements Parcelable {
    public String packageName;
    public String type;
    public String platformName;
    public String activity;

    public AppInfo(String packageName, String type, String platformName, String activity) {
        this.packageName = packageName;
        this.type = type;
        this.platformName = platformName;
        this.activity = activity != null ? activity : "";
    }

    // Backward compatibility constructor
    public AppInfo(String packageName, String type, String platformName) {
        this(packageName, type, platformName, "");
    }

    protected AppInfo(Parcel in) {
        packageName = in.readString();
        type = in.readString();
        platformName = in.readString();
        activity = in.readString();
    }

    public static final Creator<AppInfo> CREATOR = new Creator<AppInfo>() {
        @Override
        public AppInfo createFromParcel(Parcel in) {
            return new AppInfo(in);
        }

        @Override
        public AppInfo[] newArray(int size) {
            return new AppInfo[size];
        }
    };

    public String getDisplayName() {
        // Convert package names to more readable names
        switch (packageName) {
            case "com.oculus.explore":
                return "Oculus Explore";
            case "com.oculus.store":
                return "Oculus Store";
            case "messenger_system_app":
                return "Messenger";
            case "share_system_app":
                return "Share";
            case "com.oculus.browser":
                return "Oculus Browser";
            default:
                // Extract app name from package name
                String[] parts = packageName.split("\\.");
                if (parts.length > 0) {
                    String appName = parts[parts.length - 1];
                    // Capitalize first letter
                    return appName.substring(0, 1).toUpperCase() + appName.substring(1);
                }
                return packageName;
        }
    }
    
    // **NEW METHOD TO GET ICONS**
    @SuppressWarnings("deprecation") // Needed for getDrawable on older APIs
    public Drawable getDisplayIcon(Context context) {
        PackageManager pm = context.getPackageManager();

        // Handle special cases that don't have standard packages
        switch (packageName) {
            case "messenger_system_app":
                return context.getResources().getDrawable(android.R.drawable.ic_dialog_email);
            case "share_system_app":
                return context.getResources().getDrawable(android.R.drawable.ic_menu_share);
        }

        // Try to get the icon for a standard package
        try {
            return pm.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            // Return a generic fallback icon if the package can't be found
            return context.getResources().getDrawable(android.R.drawable.sym_def_app_icon);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(packageName);
        dest.writeString(type);
        dest.writeString(platformName);
        dest.writeString(activity);
    }
}