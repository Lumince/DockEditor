package com.lumi.dockeditor

import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppSelectionDialog(
    private val context: Context,
    private val listener: OnAppSelectedListener
) {

    /**
     * Interface for handling app selection events.
     */
    interface OnAppSelectedListener {
        fun onAppSelected(app: InstalledAppInfo)
    }

    /**
     * Builds and displays an AlertDialog containing a list of all installed apps.
     */
    fun show() {
        val installedApps = getInstalledApps()
        
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
        }
        
        val adapter = AppSelectionAdapter(installedApps, listener)
        recyclerView.adapter = adapter

        val dialog = AlertDialog.Builder(context)
            .setTitle("Select App")
            .setView(recyclerView)
            .setNegativeButton("Cancel", null)
            .show()

        // Pass the dialog to the adapter so it can be dismissed upon selection
        adapter.setDialog(dialog)
    }

    /**
     * Retrieves all installed applications, excluding the current app itself.
     * The list is sorted alphabetically by the app's display name.
     */
    private fun getInstalledApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val installedPackages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        val apps = mutableListOf<InstalledAppInfo>()
        
        for (appInfo in installedPackages) {
            // Filter out the editor itself
            if (appInfo.packageName == context.packageName) {
                continue
            }
            
            try {
                val appName = pm.getApplicationLabel(appInfo).toString()
                apps.add(
                    InstalledAppInfo(
                        appInfo.packageName,
                        appName,
                        pm.getApplicationIcon(appInfo.packageName)
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // Skip apps that can't be resolved
            }
        }
        
        // Sort alphabetically by app name
        return apps.sortedBy { it.appName.lowercase() }
    }

    /**
     * Adapter for displaying InstalledAppInfo items in the RecyclerView.
     */
    private class AppSelectionAdapter(
        private val apps: List<InstalledAppInfo>,
        private val listener: OnAppSelectedListener
    ) : RecyclerView.Adapter<AppSelectionAdapter.ViewHolder>() {
        
        private var dialog: Dialog? = null

        fun setDialog(dialog: Dialog) {
            this.dialog = dialog
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_selection, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.appName.text = app.appName
            holder.packageName.text = app.packageName
            holder.appIcon.setImageDrawable(app.icon)
            
            holder.itemView.setOnClickListener {
                listener.onAppSelected(app)
                dialog?.dismiss()
            }
        }

        override fun getItemCount(): Int = apps.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
            val appName: TextView = itemView.findViewById(R.id.appName)
            val packageName: TextView = itemView.findViewById(R.id.packageName)
        }
    }
}