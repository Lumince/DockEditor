package com.lumi.dockeditor

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ActivitySelectionDialog(
    private val context: Context,
    private val selectedApp: InstalledAppInfo,
    private val listener: OnActivitySelectedListener
) {

    /**
     * Interface for handling activity selection events.
     */
    interface OnActivitySelectedListener {
        fun onActivitySelected(activity: com.lumi.dockeditor.ActivityInfo)
    }

    /**
     * Builds and displays an AlertDialog containing a list of activities for the selected app.
     */
    fun show() {
        val activities = getAppActivities()
        
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
        }
        
        val adapter = ActivitySelectionAdapter(activities, listener)
        recyclerView.adapter = adapter
        
        val dialog = AlertDialog.Builder(context)
            .setTitle("Select Activity for ${selectedApp.appName}")
            .setView(recyclerView)
            .setNegativeButton("Cancel", null)
            .show()
            
        adapter.setDialog(dialog)
    }

    /**
     * Retrieves a list of exported activities for the current package using the PackageManager.
     * Activities are sorted to place the Main activity at the top of the list.
     */
    private fun getAppActivities(): List<com.lumi.dockeditor.ActivityInfo> {
        val activities = mutableListOf<com.lumi.dockeditor.ActivityInfo>()
        val pm = context.packageManager
        
        try {
            val mainIntent = pm.getLaunchIntentForPackage(selectedApp.packageName)
            val mainActivityClassName = mainIntent?.component?.className

            val packageInfo = pm.getPackageInfo(selectedApp.packageName, PackageManager.GET_ACTIVITIES)
            packageInfo.activities?.forEach { activityInfo ->
                if (activityInfo.exported) { 
                    val activityName = activityInfo.name
                    val displayName = activityName.substringAfterLast('.')
                    val isMain = activityName == mainActivityClassName
                    
                    activities.add(
                        com.lumi.dockeditor.ActivityInfo(
                            activityName,
                            displayName,
                            isMain
                        )
                    )
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            // Handle error
        }
        
        if (activities.isEmpty()) {
            activities.add(
                com.lumi.dockeditor.ActivityInfo(
                    "",
                    "Default Activity",
                    true
                )
            )
        }
        
        // Sorts activities so that the main activity appears first.
        activities.sortByDescending { it.isMainActivity }
        
        return activities
    }

    /**
     * Adapter for displaying ActivityInfo items in the RecyclerView.
     */
    private class ActivitySelectionAdapter(
        private val activities: List<com.lumi.dockeditor.ActivityInfo>,
        private val listener: OnActivitySelectedListener
    ) : RecyclerView.Adapter<ActivitySelectionAdapter.ViewHolder>() {
        
        private var dialog: Dialog? = null
        
        fun setDialog(dialog: Dialog) {
            this.dialog = dialog
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_activity_selection, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val activity = activities[position]
            holder.activityName.text = activity.getDisplayText()
            holder.activityFullName.text = if (activity.name.isEmpty()) "Default" else activity.name
            
            holder.itemView.setOnClickListener {
                listener.onActivitySelected(activity)
                dialog?.dismiss()
            }
        }
        
        override fun getItemCount(): Int = activities.size
        
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val activityName: TextView = itemView.findViewById(R.id.activityName)
            val activityFullName: TextView = itemView.findViewById(R.id.activityFullName)
        }
    }
}