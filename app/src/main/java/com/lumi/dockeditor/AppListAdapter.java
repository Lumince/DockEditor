package com.lumi.dockeditor;

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
    private OnItemMoveListener moveListener;
    private OnItemClickListener clickListener;
    private OnItemRemoveListener removeListener;
    
    public interface OnItemMoveListener {
        void onItemMove(int fromPosition, int toPosition);
    }
    
    public interface OnItemClickListener {
        void onItemClick(int position);
    }
    
    public interface OnItemRemoveListener {
        void onItemRemove(int position);
    }
    
    public AppListAdapter(List<AppInfo> apps, OnItemMoveListener moveListener, 
                         OnItemClickListener clickListener, OnItemRemoveListener removeListener) {
        this.apps = apps;
        this.moveListener = moveListener;
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
        holder.bind(app, position);
    }
    
    @Override
    public int getItemCount() {
        return apps.size();
    }
    
    public class AppViewHolder extends RecyclerView.ViewHolder {
        private TextView appName;
        private TextView packageName;
        private TextView activityName;
        private ImageView dragHandle;
        private ImageView removeButton;
        private int startPosition = -1;
        
        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
            appName = itemView.findViewById(R.id.appName);
            packageName = itemView.findViewById(R.id.packageName);
            activityName = itemView.findViewById(R.id.activityName);
            dragHandle = itemView.findViewById(R.id.dragHandle);
            removeButton = itemView.findViewById(R.id.removeButton);
        }
        
        public void bind(AppInfo app, int position) {
            appName.setText(app.getDisplayName());
            packageName.setText(app.packageName);
            activityName.setText(app.activity.isEmpty() ? "Default Activity" : app.activity);
            
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
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startPosition = getAdapterPosition();
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (startPosition != -1) {
                            int endPosition = getAdapterPosition();
                            if (startPosition != endPosition && moveListener != null) {
                                moveListener.onItemMove(startPosition, endPosition);
                            }
                        }
                        startPosition = -1;
                        return true;
                }
                return false;
            });
        }
    }
}