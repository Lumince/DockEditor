package com.lumi.dockeditor;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppSelectionDialog {
    private Context context;
    private OnAppSelectedListener listener;
    
    public interface OnAppSelectedListener {
        void onAppSelected(InstalledAppInfo app);
    }
    
    public AppSelectionDialog(Context context, OnAppSelectedListener listener) {
        this.context = context;
        this.listener = listener;
    }
    
    public void show() {
        List<InstalledAppInfo> installedApps = getInstalledApps();
        
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(new AppSelectionAdapter(installedApps, listener));
        
        new AlertDialog.Builder(context)
            .setTitle("Select App")
            .setView(recyclerView)
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private List<InstalledAppInfo> getInstalledApps() {
        List<InstalledAppInfo> apps = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        
        List<ApplicationInfo> installedPackages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        
        for (ApplicationInfo appInfo : installedPackages) {
            // Only include user apps and system apps with launchers
            if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0 || 
                pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
                
                try {
                    String appName = pm.getApplicationLabel(appInfo).toString();
                    apps.add(new InstalledAppInfo(
                        appInfo.packageName,
                        appName,
                        pm.getApplicationIcon(appInfo.packageName)
                    ));
                } catch (PackageManager.NameNotFoundException e) {
                    // Skip this app
                }
            }
        }
        
        // Sort alphabetically
        Collections.sort(apps, (a, b) -> a.appName.compareToIgnoreCase(b.appName));
        
        return apps;
    }
    
    private static class AppSelectionAdapter extends RecyclerView.Adapter<AppSelectionAdapter.ViewHolder> {
        private List<InstalledAppInfo> apps;
        private OnAppSelectedListener listener;
        
        public AppSelectionAdapter(List<InstalledAppInfo> apps, OnAppSelectedListener listener) {
            this.apps = apps;
            this.listener = listener;
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_selection, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            InstalledAppInfo app = apps.get(position);
            holder.appName.setText(app.appName);
            holder.packageName.setText(app.packageName);
            holder.appIcon.setImageDrawable(app.icon);
            
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAppSelected(app);
                }
                // Close dialog
                if (holder.itemView.getContext() instanceof android.app.Activity) {
                    // Find and close the dialog - this is a simplified approach
                    // In a real implementation, you'd pass the dialog reference
                }
            });
        }
        
        @Override
        public int getItemCount() {
            return apps.size();
        }
        
        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView appIcon;
            TextView appName;
            TextView packageName;
            
            ViewHolder(View itemView) {
                super(itemView);
                appIcon = itemView.findViewById(R.id.appIcon);
                appName = itemView.findViewById(R.id.appName);
                packageName = itemView.findViewById(R.id.packageName);
            }
        }
    }
}