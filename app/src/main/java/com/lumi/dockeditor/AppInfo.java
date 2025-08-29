package com.lumi.dockeditor;

public class AppInfo {
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
}