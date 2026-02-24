package com.lumi.dockeditor

class ActivityInfo(
    var name: String,
    var displayName: String,
    var isMainActivity: Boolean
) {
    fun getDisplayText(): String {
        return if (isMainActivity) "$displayName (Main)" else displayName
    }
}