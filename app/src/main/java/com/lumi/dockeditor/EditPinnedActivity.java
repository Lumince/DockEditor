package com.lumi.dockeditor;

import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lumi.dockeditor.databinding.ActivityEditPinnedBinding;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EditPinnedActivity extends AppCompatActivity {
    private ActivityEditPinnedBinding binding;
    private AppListAdapter adapter;
    private List<AppInfo> appList;
    private ItemTouchHelper itemTouchHelper;

    private static final String TARGET_FILE = "/data/user/0/com.oculus.systemux/shared_prefs/AUI_PREFERENCES.xml";
    private static final int MAX_APPS = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditPinnedBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        appList = getIntent().getParcelableArrayListExtra("appList");
        if (appList == null) {
            appList = new ArrayList<>();
            Toast.makeText(this, "Error: Could not load app list.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupRecyclerView();
        setupButtons();
        updateAddButtonState();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void setupRecyclerView() {
        adapter = new AppListAdapter(appList,
            viewHolder -> itemTouchHelper.startDrag(viewHolder),
            this::onAppClick,
            this::onAppRemove);

        binding.appsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.appsRecyclerView.setAdapter(adapter);

        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                onAppReorder(fromPosition, toPosition);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // Not used
            }
        };

        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(binding.appsRecyclerView);
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
                // First, create the JSONArray as before to ensure data is correct
                JSONArray newAppsArray = new JSONArray();
                for (AppInfo app : appList) {
                    JSONObject appObj = new JSONObject(app.originalJsonString);
                    appObj.put("packageName", app.packageName);
                    JSONObject appPanelData = appObj.optJSONObject("appPanelData");
                    if (appPanelData == null) {
                        appPanelData = new JSONObject();
                    }
                    if (app.componentName != null && !app.componentName.isEmpty()) {
                        appPanelData.put("componentName", app.componentName);
                    } else {
                        appPanelData.remove("componentName");
                    }
                    appObj.put("appPanelData", appPanelData);
                    appObj.remove("activity");
                    newAppsArray.put(appObj);
                }

                // ** NEW LOGIC: Manually build the string to control spacing **
                StringBuilder jsonBuilder = new StringBuilder("[");
                for (int i = 0; i < newAppsArray.length(); i++) {
                    // Get the string for the current app object
                    String appString = newAppsArray.getJSONObject(i).toString();
                    jsonBuilder.append(appString);

                    // Append the correct, inconsistent separator
                    if (i < newAppsArray.length() - 1) {
                        if (i == newAppsArray.length() - 2) {
                            // This is the separator between the 2nd to last and last item
                            jsonBuilder.append(" ,");
                        } else {
                            jsonBuilder.append(", ");
                        }
                    }
                }
                jsonBuilder.append("]");

                String finalJsonString = jsonBuilder.toString();

                String encodedJson = finalJsonString
                        .replace("\\/", "/") // Un-escape slashes
                        .replace("\"", "&quot;"); // Escape quotes for XML

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
                        finish();
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
            Collections.swap(appList, from, to);
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
                AppInfo existingApp = appList.get(editPosition);
                existingApp.packageName = newAppInfo.packageName;
                existingApp.activity = newAppInfo.activity;
                existingApp.componentName = newAppInfo.componentName;
                existingApp.originalJsonString = newAppInfo.originalJsonString;

                adapter.notifyItemChanged(editPosition);
            }
            binding.saveButton.setEnabled(true);
        });
        dialog.show();
    }
}