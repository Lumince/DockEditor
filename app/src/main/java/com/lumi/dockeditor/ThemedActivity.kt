package com.lumi.dockeditor

import android.os.Bundle
import androidx.activity.ComponentActivity

/** Reads the UX Patcher colours before the first frame, and again when coming back to the app. */
abstract class ThemedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ModuleTheme.refresh(this)
    }

    override fun onResume() {
        super.onResume()
        ModuleTheme.refresh(this)
    }
}
