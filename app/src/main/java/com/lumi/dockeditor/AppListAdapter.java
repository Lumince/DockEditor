package com.lumi.dockeditor;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.AppViewHolder> {
    private List<AppInfo> apps;
    private OnStartDragListener dragListener;
    private OnItemClickListener clickListener;
    private OnItemRemoveListener removeListener;

    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }
    
    public interface OnItemClickListener {
        void onItemClick(int position);
    }
    
    public interface OnItemRemoveListener {
        void onItemRemove(int position);
    }
    
    public AppListAdapter(List<AppInfo> apps, OnStartDragListener dragListener,
                         OnItemClickListener clickListener, OnItemRemoveListener removeListener) {
        this.apps = apps;
        this.dragListener = dragListener;
        this.clickListener = clickListener;
        this.removeListener = removeListener;
    }
    
    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppInfo app = apps.get(position);
        holder.bind(app);
    }
    
    @Override
    public int getItemCount() {
        return apps.size();
    }
    
    public class AppViewHolder extends RecyclerView.ViewHolder {
        private ImageView appIcon;
        private TextView appName;
        private TextView packageName;
        private TextView activityName;
        private ImageView dragHandle;
        private ImageView removeButton;
        
        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.appIcon);
            appName = itemView.findViewById(R.id.appName);
            packageName = itemView.findViewById(R.id.packageName);
            activityName = itemView.findViewById(R.id.activityName);
            dragHandle = itemView.findViewById(R.id.dragHandle);
            removeButton = itemView.findViewById(R.id.removeButton);
        }
        
        public void bind(AppInfo app) {
            appName.setText(app.getDisplayName());
            packageName.setText(app.packageName);
            activityName.setText(app.activity.isEmpty() ? "Default Activity" : app.activity);

            // **UPDATED ICON LOGIC**
            // Use the new, smarter method to get the display icon
            appIcon.setImageDrawable(app.getDisplayIcon(itemView.getContext()));
            
            // Click to edit app
            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onItemClick(getAdapterPosition());
                }
            });
            
            // Remove button
            removeButton.setOnClickListener(v -> {
                if (removeListener != null) {
                    removeListener.onItemRemove(getAdapterPosition());
                }
            });
            
            // Drag handle for reordering
            dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (dragListener != null) {
                        dragListener.onStartDrag(this);
                    }
                }
                return false;
            });
        }
    }
}