package com.lumi.dockeditor

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import org.json.JSONException
import org.json.JSONObject

// Moving variables to the primary constructor with default values fixes the build errors
class AppInfo(
    var packageName: String = "",
    var type: String = "APP",
    var platformName: String = "ANDROID_6DOF",
    var activity: String = "",
    var componentName: String = "",
    var originalJsonString: String = ""
) : Parcelable {

    // Secondary constructor for parsing JSON
    constructor(jsonString: String) : this() {
        this.originalJsonString = jsonString
        try {
            val jsonObject = JSONObject(jsonString)
            this.packageName = jsonObject.optString("packageName", "Unknown")
            this.type = jsonObject.optString("type", "APP")
            this.platformName = jsonObject.optString("platformName", "ANDROID_6DOF")

            val appPanelData = jsonObject.optJSONObject("appPanelData")
            this.componentName = appPanelData?.optString("componentName", "") ?: ""
            this.activity = if (this.componentName.contains("/")) {
                this.componentName.substring(this.componentName.indexOf("/") + 1)
            } else {
                this.componentName
            }
        } catch (e: JSONException) {
            this.packageName = "JSON_Parse_Error"
            this.activity = e.message ?: ""
            this.componentName = "Error"
        }
    }

    // Secondary constructor for creating a brand new app entry
    constructor(packageName: String, type: String, platformName: String, activity: String?) : this(
        packageName = packageName,
        type = type,
        platformName = platformName,
        activity = activity ?: ""
    ) {
        this.componentName = if (this.activity.isEmpty()) "" else "$packageName/${this.activity}"

        val jsonObject = JSONObject().apply {
            put("packageName", packageName)
            put("type", type)
            put("platformName", platformName)
            val appPanelData = JSONObject()
            if (componentName.isNotEmpty()) {
                appPanelData.put("componentName", componentName)
            }
            put("appPanelData", appPanelData)
        }
        this.originalJsonString = jsonObject.toString()
    }

    // Manual Parcelable implementation
    protected constructor(parcel: Parcel) : this(
        packageName = parcel.readString() ?: "",
        type = parcel.readString() ?: "",
        platformName = parcel.readString() ?: "",
        activity = parcel.readString() ?: "",
        componentName = parcel.readString() ?: "",
        originalJsonString = parcel.readString() ?: ""
    )

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(packageName)
        dest.writeString(type)
        dest.writeString(platformName)
        dest.writeString(activity)
        dest.writeString(componentName)
        dest.writeString(originalJsonString)
    }

    override fun describeContents(): Int = 0

    fun getDisplayName(): String {
        return when (packageName) {
            "com.oculus.explore" -> "Oculus Explore"
            "com.oculus.store" -> "Oculus Store"
            "messenger_system_app" -> "Messenger"
            "share_system_app" -> "Share"
            "com.oculus.browser" -> "Oculus Browser"
            else -> try {
                // Note: Ensure App.getContext() is accessible in your project
                val pm = App.getContext().packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: Exception) {
                packageName.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: packageName
            }
        }
    }

    fun getDisplayIcon(context: Context): Drawable? {
        val pm = context.packageManager
        return when (packageName) {
            "messenger_system_app" -> context.getDrawable(android.R.drawable.ic_dialog_email)
            "share_system_app" -> context.getDrawable(android.R.drawable.ic_menu_share)
            else -> try {
                pm.getApplicationIcon(packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                context.getDrawable(android.R.drawable.sym_def_app_icon)
            }
        }
    }

    companion object CREATOR : Parcelable.Creator<AppInfo> {
        override fun createFromParcel(parcel: Parcel) = AppInfo(parcel)
        override fun newArray(size: Int) = arrayOfNulls<AppInfo>(size)
    }
}