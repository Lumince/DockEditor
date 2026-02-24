package com.lumi.dockeditor

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private val apps: List<AppInfo>,
    private val dragListener: OnStartDragListener?,
    private val clickListener: OnItemClickListener?,
    private val removeListener: OnItemRemoveListener?
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    /**
     * Interface for initiating drag-and-drop actions.
     */
    interface OnStartDragListener {
        fun onStartDrag(viewHolder: RecyclerView.ViewHolder)
    }

    /**
     * Interface for handling item clicks.
     */
    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    /**
     * Interface for handling item removal.
     */
    interface OnItemRemoveListener {
        fun onItemRemove(position: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun getItemCount(): Int = apps.size

    /**
     * ViewHolder class that manages individual item views and their click/touch events.
     */
    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val appName: TextView = itemView.findViewById(R.id.appName)
        private val packageNameText: TextView = itemView.findViewById(R.id.packageName)
        private val activityName: TextView = itemView.findViewById(R.id.activityName)
        private val dragHandle: View = itemView.findViewById(R.id.dragHandle)
        private val removeButton: View = itemView.findViewById(R.id.removeButton)

        fun bind(app: AppInfo) {
            // Setting text fields using property access syntax
            appName.text = app.getDisplayName()
            packageNameText.text = app.packageName
            activityName.text = if (app.activity.isEmpty()) "Default Activity" else app.activity

            // Load icon using the logic defined in AppInfo
            appIcon.setImageDrawable(app.getDisplayIcon(itemView.context))

            // Item click to edit the app
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    clickListener?.onItemClick(position)
                }
            }

            // Click to remove app from the list
            removeButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    removeListener?.onItemRemove(position)
                }
            }

            // Handle touch events on the drag handle for reordering
            dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    dragListener?.onStartDrag(this)
                }
                false
            }
        }
    }
}