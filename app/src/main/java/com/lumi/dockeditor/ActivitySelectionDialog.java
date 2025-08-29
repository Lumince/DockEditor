package com.lumi.dockeditor;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ActivitySelectionDialog {
    private Context context;
    private InstalledAppInfo selectedApp;
    private OnActivitySelectedListener listener;
    
    public interface OnActivitySelectedListener {
        void onActivitySelected(com.lumi.dockeditor.ActivityInfo activity);
    }
    
    public ActivitySelectionDialog(Context context, InstalledAppInfo selectedApp, OnActivitySelectedListener listener) {
        this.context = context;
        this.selectedApp = selectedApp;
        this.listener = listener;
    }
    
    public void show() {
        List<com.lumi.dockeditor.ActivityInfo> activities = getAppActivities();
        
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(new ActivitySelectionAdapter(activities, listener));
        
        new AlertDialog.Builder(context)
            .setTitle("Select Activity for " + selectedApp.appName)
            .setView(recyclerView)
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private List<com.lumi.dockeditor.ActivityInfo> getAppActivities() {
        List<com.lumi.dockeditor.ActivityInfo> activities = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        
        try {
            // Get main launcher activity
            Intent mainIntent = pm.getLaunchIntentForPackage(selectedApp.packageName);
            if (mainIntent != null) {
                String mainActivity = mainIntent.getComponent().getClassName();
                activities.add(new com.lumi.dockeditor.ActivityInfo(
                    mainActivity, 
                    "Main Activity", 
                    true
                ));
            }
            
            // Get all activities for this package
            android.content.pm.PackageInfo packageInfo = pm.getPackageInfo(selectedApp.packageName, PackageManager.GET_ACTIVITIES);
            if (packageInfo.activities != null) {
                for (ActivityInfo activityInfo : packageInfo.activities) {
                    if (activityInfo.exported) { // Only exported activities
                        String activityName = activityInfo.name;
                        String displayName = activityName.substring(activityName.lastIndexOf('.') + 1);
                        
                        // Don't duplicate the main activity
                        boolean isMain = mainIntent != null && 
                                        activityName.equals(mainIntent.getComponent().getClassName());
                        
                        if (!isMain) {
                            activities.add(new com.lumi.dockeditor.ActivityInfo(
                                activityName,
                                displayName,
                                false
                            ));
                        }
                    }
                }
            }
            
        } catch (PackageManager.NameNotFoundException e) {
            // If we can't get activities, just add a default option
            activities.add(new com.lumi.dockeditor.ActivityInfo(
                "",
                "Default Activity",
                true
            ));
        }
        
        // If no activities found, add default
        if (activities.isEmpty()) {
            activities.add(new com.lumi.dockeditor.ActivityInfo(
                "",
                "Default Activity",
                true
            ));
        }
        
        return activities;
    }
    
    private static class ActivitySelectionAdapter extends RecyclerView.Adapter<ActivitySelectionAdapter.ViewHolder> {
        private List<com.lumi.dockeditor.ActivityInfo> activities;
        private OnActivitySelectedListener listener;
        
        public ActivitySelectionAdapter(List<com.lumi.dockeditor.ActivityInfo> activities, OnActivitySelectedListener listener) {
            this.activities = activities;
            this.listener = listener;
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_activity_selection, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            com.lumi.dockeditor.ActivityInfo activity = activities.get(position);
            holder.activityName.setText(activity.getDisplayText());
            holder.activityFullName.setText(activity.name.isEmpty() ? "Default" : activity.name);
            
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onActivitySelected(activity);
                }
            });
        }
        
        @Override
        public int getItemCount() {
            return activities.size();
        }
        
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView activityName;
            TextView activityFullName;
            
            ViewHolder(View itemView) {
                super(itemView);
                activityName = itemView.findViewById(R.id.activityName);
                activityFullName = itemView.findViewById(R.id.activityFullName);
            }
        }
    }
}