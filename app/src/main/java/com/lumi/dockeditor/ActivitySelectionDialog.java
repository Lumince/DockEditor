package com.lumi.dockeditor;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections; // ** THIS LINE IS THE FIX **
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
        
        ActivitySelectionAdapter adapter = new ActivitySelectionAdapter(activities, listener);
        recyclerView.setAdapter(adapter);
        
        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle("Select Activity for " + selectedApp.appName)
            .setView(recyclerView)
            .setNegativeButton("Cancel", null)
            .show();
            
        adapter.setDialog(dialog);
    }
    
    private List<com.lumi.dockeditor.ActivityInfo> getAppActivities() {
        List<com.lumi.dockeditor.ActivityInfo> activities = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        
        try {
            Intent mainIntent = pm.getLaunchIntentForPackage(selectedApp.packageName);
            String mainActivityClassName = (mainIntent != null && mainIntent.getComponent() != null) ? mainIntent.getComponent().getClassName() : null;

            android.content.pm.PackageInfo packageInfo = pm.getPackageInfo(selectedApp.packageName, PackageManager.GET_ACTIVITIES);
            if (packageInfo.activities != null) {
                for (ActivityInfo activityInfo : packageInfo.activities) {
                    if (activityInfo.exported) { 
                        String activityName = activityInfo.name;
                        String displayName = activityName.substring(activityName.lastIndexOf('.') + 1);
                        boolean isMain = activityName.equals(mainActivityClassName);
                        
                        activities.add(new com.lumi.dockeditor.ActivityInfo(
                            activityName,
                            displayName,
                            isMain
                        ));
                    }
                }
            }
            
        } catch (PackageManager.NameNotFoundException e) {
            // Handle error
        }
        
        if (activities.isEmpty()) {
            activities.add(new com.lumi.dockeditor.ActivityInfo(
                "",
                "Default Activity",
                true
            ));
        }
        
        Collections.sort(activities, (a, b) -> Boolean.compare(b.isMainActivity, a.isMainActivity));
        
        return activities;
    }
    
    private static class ActivitySelectionAdapter extends RecyclerView.Adapter<ActivitySelectionAdapter.ViewHolder> {
        private List<com.lumi.dockeditor.ActivityInfo> activities;
        private OnActivitySelectedListener listener;
        private Dialog dialog;
        
        public ActivitySelectionAdapter(List<com.lumi.dockeditor.ActivityInfo> activities, OnActivitySelectedListener listener) {
            this.activities = activities;
            this.listener = listener;
        }
        
        public void setDialog(Dialog dialog) {
            this.dialog = dialog;
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
                if (dialog != null) {
                    dialog.dismiss();
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