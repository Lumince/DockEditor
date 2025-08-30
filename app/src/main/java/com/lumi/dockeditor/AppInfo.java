package com.lumi.dockeditor;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

public class AppInfo implements Parcelable {
    public String packageName;
    public String type;
    public String platformName;
    public String activity;
    public String componentName; // New field to preserve the original componentName

    public String originalJsonString;

    /**
     * Constructor used when PARSING an existing app.
     */
    public AppInfo(String originalJsonString) {
        this.originalJsonString = originalJsonString;
        try {
            JSONObject jsonObject = new JSONObject(originalJsonString);
            this.packageName = jsonObject.optString("packageName", "Unknown");
            this.type = jsonObject.optString("type", "APP");
            this.platformName = jsonObject.optString("platformName", "ANDROID_6DOF");

            if (jsonObject.has("appPanelData")) {
                JSONObject appPanelData = jsonObject.getJSONObject("appPanelData");
                this.componentName = appPanelData.optString("componentName", "");
            } else {
                this.componentName = "";
            }

            if (this.componentName.contains("/")) {
                this.activity = this.componentName.substring(this.componentName.indexOf("/") + 1);
            } else {
                this.activity = this.componentName;
            }

        } catch (JSONException e) {
            this.packageName = "JSON_Parse_Error";
            this.activity = e.getMessage();
            this.componentName = "Error";
        }
    }

    /**
     * Constructor used when CREATING a brand new app entry.
     */
    public AppInfo(String packageName, String type, String platformName, String activity) {
        this.packageName = packageName;
        this.type = type;
        this.platformName = platformName;
        this.activity = activity != null ? activity : "";
        this.componentName = activity.isEmpty() ? "" : this.packageName + "/" + this.activity;

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("packageName", this.packageName);
            jsonObject.put("type", this.type);
            jsonObject.put("platformName", this.platformName);

            JSONObject appPanelData = new JSONObject();
            if (!this.componentName.isEmpty()) {
                appPanelData.put("componentName", this.componentName);
            }
            jsonObject.put("appPanelData", appPanelData);

        } catch (JSONException e) {
            // Should not happen
        }
        this.originalJsonString = jsonObject.toString();
    }

    protected AppInfo(Parcel in) {
        packageName = in.readString();
        type = in.readString();
        platformName = in.readString();
        activity = in.readString();
        componentName = in.readString();
        originalJsonString = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(packageName);
        dest.writeString(type);
        dest.writeString(platformName);
        dest.writeString(activity);
        dest.writeString(componentName);
        dest.writeString(originalJsonString);
    }

    @Override
    public int describeContents() {
        return 0;
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
        switch (packageName) {
            case "com.oculus.explore": return "Oculus Explore";
            case "com.oculus.store": return "Oculus Store";
            case "messenger_system_app": return "Messenger";
            case "share_system_app": return "Share";
            case "com.oculus.browser": return "Oculus Browser";
            default:
                try {
                    PackageManager pm = App.getContext().getPackageManager();
                    return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
                } catch (Exception e) {
                    String[] parts = packageName.split("\\.");
                    if (parts.length > 0) {
                        String appName = parts[parts.length - 1];
                        return appName.substring(0, 1).toUpperCase() + appName.substring(1);
                    }
                    return packageName;
                }
        }
    }
    
    @SuppressWarnings("deprecation")
    public Drawable getDisplayIcon(Context context) {
        PackageManager pm = context.getPackageManager();
        switch (packageName) {
            case "messenger_system_app":
                return context.getResources().getDrawable(android.R.drawable.ic_dialog_email);
            case "share_system_app":
                return context.getResources().getDrawable(android.R.drawable.ic_menu_share);
        }
        try {
            return pm.getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return context.getResources().getDrawable(android.R.drawable.sym_def_app_icon);
        }
    }
}