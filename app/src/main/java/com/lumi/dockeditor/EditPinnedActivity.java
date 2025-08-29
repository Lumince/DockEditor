package com.lumi.dockeditor;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.lumi.dockeditor.databinding.ActivityEditPinnedBinding;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class EditPinnedActivity extends AppCompatActivity {
    private ActivityEditPinnedBinding binding;
    private AppListAdapter adapter;
    private List<AppInfo> appList;

    private static final String TARGET_FILE = "/data/user/0/com.oculus.systemux/shared_prefs/AUI_PREFERENCES.xml";
    private static final int MAX_APPS = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditPinnedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Add this line to enable the back arrow in the action bar
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Get the list of apps passed from MainActivity
        appList = getIntent().getParcelableArrayListExtra("appList");
        if (appList == null) {
            appList = new ArrayList<>();
            Toast.makeText(this, "Error: Could not load app list.", Toast.LENGTH_LONG).show();
            finish(); // Close activity if data is missing
            return;
        }

        setupRecyclerView();
        setupButtons();
        updateAddButtonState();
    }

    private void setupRecyclerView() {
        adapter = new AppListAdapter(appList, this::onAppReorder, this::onAppClick, this::onAppRemove);
        binding.appsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.appsRecyclerView.setAdapter(adapter);
    }

    private void setupButtons() {
        binding.saveButton.setOnClickListener(v -> saveChanges());
        binding.addAppButton.setOnClickListener(v -> {
            if (appList.size() < MAX_APPS) {
                showAppSelectionDialog();
            } else {
                Toast.makeText(this, "Maximum of " + MAX_APPS + " apps allowed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveChanges() {
        new Thread(() -> {
            try {
                JSONArray newAppsArray = new JSONArray();
                for (AppInfo app : appList) {
                    JSONObject appObj = new JSONObject();
                    appObj.put("packageName", app.packageName);
                    appObj.put("type", app.type);
                    appObj.put("platformName", app.platformName);
                    if (app.activity != null && !app.activity.isEmpty()) {
                        appObj.put("activity", app.activity);
                    }
                    newAppsArray.put(appObj);
                }

                String encodedJson = newAppsArray.toString()
                        .replace("\"", "&quot;")
                        .replace("&", "&amp;");

                String newXmlContent = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
                        "<map>\n" +
                        "    <string name=\"aui_bar_apps_pinned\">" + encodedJson + "</string>\n" +
                        "\t<string name=\"aui_bar_apps_history\">[]</string>\n" +
                        "</map>";

                boolean success = RootShell.writeFileContent(TARGET_FILE, newXmlContent);

                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, "Changes saved successfully!\nRestart Oculus system to see changes.",
                                Toast.LENGTH_LONG).show();
                        binding.saveButton.setEnabled(false);
                        finish(); // Close the activity after saving
                    } else {
                        Toast.makeText(this, "Save failed: check log for details.", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Save error: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void onAppReorder(int from, int to) {
        if (from != to) {
            AppInfo app = appList.remove(from);
            appList.add(to, app);
            adapter.notifyItemMoved(from, to);
            binding.saveButton.setEnabled(true);
        }
    }

    private void onAppClick(int position) {
        showAppSelectionDialog(position);
    }

    private void onAppRemove(int position) {
        appList.remove(position);
        adapter.notifyItemRemoved(position);
        adapter.notifyItemRangeChanged(position, appList.size());
        updateAddButtonState();
        binding.saveButton.setEnabled(true);
    }

    private void updateAddButtonState() {
        binding.addAppButton.setEnabled(appList.size() < MAX_APPS);
        binding.addAppButton.setText(appList.size() >= MAX_APPS ?
                "Max Apps Reached" : "Add App (" + appList.size() + "/" + MAX_APPS + ")");
    }

    private void showAppSelectionDialog() {
        showAppSelectionDialog(-1);
    }

    private void showAppSelectionDialog(int editPosition) {
        AppSelectionDialog dialog = new AppSelectionDialog(this, (selectedApp) -> {
            showActivitySelectionDialog(selectedApp, editPosition);
        });
        dialog.show();
    }

    private void showActivitySelectionDialog(InstalledAppInfo selectedApp, int editPosition) {
        ActivitySelectionDialog dialog = new ActivitySelectionDialog(this, selectedApp, (activity) -> {
            AppInfo newAppInfo = new AppInfo(
                    selectedApp.packageName,
                    "APP",
                    "ANDROID_6DOF",
                    activity.name
            );

            if (editPosition == -1) {
                if (appList.size() < MAX_APPS) {
                    appList.add(newAppInfo);
                    adapter.notifyItemInserted(appList.size() - 1);
                    updateAddButtonState();
                }
            } else {
                appList.set(editPosition, newAppInfo);
                adapter.notifyItemChanged(editPosition);
            }
            binding.saveButton.setEnabled(true);
        });
        dialog.show();
    }
}