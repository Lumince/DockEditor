package com.lumi.dockeditor;

import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.lumi.dockeditor.databinding.ActivityMainBinding;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private AppListAdapter adapter;
    private List<AppInfo> appList;

    // Path to the actual AUI_PREFERENCES.xml file
    private static final String TARGET_FILE = "/data/user/0/com.oculus.systemux/shared_prefs/AUI_PREFERENCES.xml";
    private static final String BACKUP_SUBDIR = "backups";
    private static final int MAX_APPS = 5;

    // Default AUI_PREFERENCES.xml content
    private static final String DEFAULT_AUI_PREFERENCES =
            "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
            "<map>\n" +
            "    <string name=\"aui_bar_apps_pinned\">[{&quot;packageName&quot;:&quot;com.oculus.explore&quot;,&quot;type&quot;:&quot;APP&quot;,&quot;platformName&quot;:&quot;ANDROID_6DOF&quot;}, {&quot;packageName&quot;:&quot;com.oculus.store&quot;,&quot;type&quot;:&quot;APP&quot;,&quot;platformName&quot;:&quot;ANDROID_6DOF&quot;}, {&quot;packageName&quot;:&quot;messenger_system_app&quot;,&quot;type&quot;:&quot;APP&quot;,&quot;platformName&quot;:&quot;ANDROID_6DOF&quot;}, {&quot;packageName&quot;:&quot;share_system_app&quot;,&quot;type&quot;:&quot;APP&quot;,&quot;platformName&quot;:&quot;ANDROID_6DOF&quot;}, {&quot;packageName&quot;:&quot;com.oculus.browser&quot;,&quot;type&quot;:&quot;APP&quot;,&quot;platformName&quot;:&quot;ANDROID_6DOF&quot;}]</string>\n" +
            "\t<string name=\"aui_bar_apps_history\">[]</string>\n" +
            "</map>";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        appList = new ArrayList<>();

        setupRecyclerView();
        setupButtons();
        checkRootAccess();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shutdown the persistent root shell when the activity is destroyed
        RootShell.shutdown();
    }

    private void setupRecyclerView() {
        adapter = new AppListAdapter(appList, this::onAppReorder, this::onAppClick, this::onAppRemove);
        binding.appsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.appsRecyclerView.setAdapter(adapter);
    }

    private void setupButtons() {
        binding.loadButton.setOnClickListener(v -> loadAndParseFile());
        binding.saveButton.setOnClickListener(v -> saveChanges());
        binding.backupButton.setOnClickListener(v -> backupFile());
        binding.restoreBackupButton.setOnClickListener(v -> showRestoreBackupDialog());
        binding.restoreDefaultButton.setOnClickListener(v -> showRestoreDefaultDialog());


        binding.addAppButton.setOnClickListener(v -> {
            if (appList.size() < MAX_APPS) {
                showAppSelectionDialog();
            } else {
                Toast.makeText(this, "Maximum of " + MAX_APPS + " apps allowed", Toast.LENGTH_SHORT).show();
            }
        });

        // Shell command button
        binding.runShellButton.setOnClickListener(v -> runShellCommand());
    }

    private void checkRootAccess() {
        logToUi("Checking for root access...");
        new Thread(() -> {
            // Initialize RootShell with --mount-master for a global mount namespace
            boolean hasRoot = RootShell.initRootShell("su --mount-master");
            runOnUiThread(() -> {
                if (hasRoot) {
                    binding.statusText.setText("Root access granted ✓");
                    binding.backupButton.setEnabled(true);
                    binding.restoreBackupButton.setEnabled(true);
                    binding.restoreDefaultButton.setEnabled(true);
                    binding.loadButton.setEnabled(true);
                    binding.runShellButton.setEnabled(true);
                    logToUi("Root access granted with --mount-master.");
                    checkSelinuxStatus();
                    checkSelinuxContext();
                    checkForExistingBackups();
                } else {
                    binding.statusText.setText("Root access denied ✗");
                    Toast.makeText(this, "Root access is required for this app to function",
                            Toast.LENGTH_LONG).show();
                    logToUi("Root access denied.");
                }
            });
        }).start();
    }

    private void runShellCommand() {
        String command = binding.commandEditText.getText().toString().trim();
        if (command.isEmpty()) {
            Toast.makeText(this, "Please enter a command", Toast.LENGTH_SHORT).show();
            return;
        }

        logToUi("Executing: " + command);
        new Thread(() -> {
            String output = RootShell.executeCommand(command);
            runOnUiThread(() -> {
                binding.logTextView.append("Output:\n" + output + "\n");
                binding.logScrollView.fullScroll(View.FOCUS_DOWN);
            });
        }).start();
    }

    private void checkSelinuxStatus() {
        logToUi("Checking SELinux status...");
        new Thread(() -> {
            String output = RootShell.executeCommand("getenforce").trim();
            runOnUiThread(() -> {
                if ("Enforcing".equalsIgnoreCase(output)) {
                    binding.selinuxStatusText.setText("SELinux: Enforcing ⚠️"); // Green checkmark assuming it's correctly enforcing
                } else if ("Permissive".equalsIgnoreCase(output)) {
                    binding.selinuxStatusText.setText("SELinux: Permissive ✓");
                } else {
                    binding.selinuxStatusText.setText("SELinux: " + output + " ?");
                }
                logToUi("SELinux Status: " + output);
            });
        }).start();
    }

    private void checkSelinuxContext() {
        logToUi("Checking SELinux context...");
        new Thread(() -> {
            String output = RootShell.executeCommand("id -Z").trim();
            runOnUiThread(() -> {
                binding.selinuxContextText.setText("Context: " + output);
                logToUi("SELinux Context: " + output);
            });
        }).start();
    }


    private void checkForExistingBackups() {
        logToUi("Checking for existing backups...");
        new Thread(() -> {
            File backupDir = new File(getCacheDir(), BACKUP_SUBDIR);
            if (backupDir.exists()) {
                File[] backupFiles = backupDir.listFiles((dir, name) -> name.endsWith(".xml"));
                final boolean hasBackups = backupFiles != null && backupFiles.length > 0;

                runOnUiThread(() -> {
                    if (!hasBackups) {
                        binding.restoreBackupButton.setText("No Backups Found");
                        binding.restoreBackupButton.setEnabled(false);
                        logToUi("No backups found.");
                    } else {
                        binding.restoreBackupButton.setText("Restore Backup (" + backupFiles.length + ")");
                        logToUi(backupFiles.length + " backups found.");
                    }
                });
            } else {
                runOnUiThread(() -> {
                    logToUi("Backup directory does not exist.");
                    binding.restoreBackupButton.setText("No Backups Found");
                    binding.restoreBackupButton.setEnabled(false);
                });
            }
        }).start();
    }

    private void showRestoreBackupDialog() {
        logToUi("Showing restore backup dialog...");
        new Thread(() -> {
            File backupDir = new File(getCacheDir(), BACKUP_SUBDIR);
            if (!backupDir.exists()) {
                runOnUiThread(() -> Toast.makeText(this, "No backup directory found", Toast.LENGTH_SHORT).show());
                logToUi("Error: No backup directory found.");
                return;
            }

            File[] backupFiles = backupDir.listFiles((dir, name) -> name.endsWith(".xml"));
            if (backupFiles == null || backupFiles.length == 0) {
                runOnUiThread(() -> Toast.makeText(this, "No backup files found", Toast.LENGTH_SHORT).show());
                logToUi("Error: No backup files found.");
                return;
            }

            // Sort by date (newest first)
            java.util.Arrays.sort(backupFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

            String[] backupNames = new String[backupFiles.length];
            for (int i = 0; i < backupFiles.length; i++) {
                backupNames[i] = backupFiles[i].getName() + "\n" +
                        new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
                                .format(new java.util.Date(backupFiles[i].lastModified()));
            }

            runOnUiThread(() -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Select Backup to Restore")
                        .setItems(backupNames, (dialog, which) -> {
                            restoreFromBackup(backupFiles[which]);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }).start();
    }

    private void showRestoreDefaultDialog() {
        logToUi("Showing restore default dialog...");
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Restore Default Configuration")
                .setMessage("This will restore the default Oculus dock configuration:\n\n" +
                        "• Oculus Explore\n" +
                        "• Oculus Store\n" +
                        "• Messenger\n" +
                        "• Share\n" +
                        "• Oculus Browser\n\n" +
                        "This will overwrite your current configuration. Continue?")
                .setPositiveButton("Restore Defaults", (dialog, which) -> restoreDefaults())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void restoreFromBackup(File backupFile) {
        logToUi("Restoring from backup: " + backupFile.getName());
        new Thread(() -> {
            try {
                String backupContent = readFileContent(backupFile); // Read from app's local cache
                if (backupContent == null) {
                    runOnUiThread(() -> Toast.makeText(this, "Restore failed: Could not read backup file.", Toast.LENGTH_SHORT).show());
                    logToUi("Restore failed: Could not read backup file.");
                    return;
                }

                logToUi("Writing content to target file...");
                boolean success = RootShell.writeFileContent(TARGET_FILE, backupContent);

                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, "Backup restored successfully!\nRestart Oculus system to see changes.",
                                Toast.LENGTH_LONG).show();
                        if (binding.appsRecyclerView.getVisibility() == View.VISIBLE) {
                            loadAndParseFile(); // Reload UI if visible
                        }
                        logToUi("Restore successful.");
                    } else {
                        Toast.makeText(this, "Restore failed. Check log for details.", Toast.LENGTH_SHORT).show();
                        logToUi("Restore failed. Last command output: " + RootShell.getLastCommandOutput());
                    }
                });
            } catch (Exception e) {
                logToUi("Restore error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Restore error: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void restoreDefaults() {
        logToUi("Restoring default configuration...");
        new Thread(() -> {
            try {
                logToUi("Writing default configuration to target file...");
                boolean success = RootShell.writeFileContent(TARGET_FILE, DEFAULT_AUI_PREFERENCES);

                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, "Default configuration restored successfully!\nRestart Oculus system to see changes.",
                                Toast.LENGTH_LONG).show();
                        if (binding.appsRecyclerView.getVisibility() == View.VISIBLE) {
                            loadAndParseFile(); // Reload UI if visible
                        }
                        logToUi("Default configuration restored successfully.");
                    } else {
                        Toast.makeText(this, "Restore failed. Check log for details.", Toast.LENGTH_SHORT).show();
                        logToUi("Restore failed. Last command output: " + RootShell.getLastCommandOutput());
                    }
                });
            } catch (Exception e) {
                logToUi("Restore defaults error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Restore defaults error: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void backupFile() {
        logToUi("Starting backup process...");
        new Thread(() -> {
            try {
                logToUi("Reading content from target file " + TARGET_FILE + "...");
                String content = RootShell.getFileContent(TARGET_FILE);
                if (content == null || content.trim().isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this, "Backup failed: Could not read original file.", Toast.LENGTH_SHORT).show());
                    logToUi("Backup failed: Could not read original file. Output: " + RootShell.getLastCommandOutput());
                    return;
                }

                File backupDir = new File(getCacheDir(), BACKUP_SUBDIR);
                if (!backupDir.exists()) {
                    logToUi("Creating backup directory...");
                    backupDir.mkdirs();
                }

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        .format(new Date());
                String backupFileName = "AUI_PREFERENCES_" + timestamp + ".xml";
                File backupFile = new File(backupDir, backupFileName);
                logToUi("Saving backup to " + backupFile.getAbsolutePath());

                FileOutputStream fos = new FileOutputStream(backupFile);
                fos.write(content.getBytes("UTF-8"));
                fos.close();

                runOnUiThread(() -> {
                    Toast.makeText(this, "Backup created: " + backupFileName, Toast.LENGTH_LONG).show();
                    checkForExistingBackups();
                    logToUi("Backup created successfully.");
                });

            } catch (Exception e) {
                logToUi("Backup error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Backup error: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }


    private void loadAndParseFile() {
        logToUi("Loading and parsing file...");
        new Thread(() -> {
            try {
                // First, create a backup of the current file before loading
                backupFileInternal(); // Internal backup without Toast feedback
                logToUi("Reading content from target file " + TARGET_FILE + "...");
                
                String content = RootShell.getFileContent(TARGET_FILE);
                if (content == null || content.trim().isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to read original file", Toast.LENGTH_SHORT).show());
                    logToUi("Failed to read original file. Output: " + RootShell.getLastCommandOutput());
                    return;
                }

                logToUi("Parsing XML content...");
                parseAuiPreferences(content);

                runOnUiThread(() -> {
                    binding.appsRecyclerView.setVisibility(View.VISIBLE);
                    binding.saveButton.setVisibility(View.VISIBLE);
                    binding.addAppButton.setVisibility(View.VISIBLE);
                    binding.saveButton.setEnabled(true);
                    updateAddButtonState();
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "File loaded successfully", Toast.LENGTH_SHORT).show();
                    logToUi("File loaded and parsed successfully.");
                });

            } catch (Exception e) {
                logToUi("Load error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Load error: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // Helper for internal backup without UI toast
    private void backupFileInternal() {
        try {
            String content = RootShell.getFileContent(TARGET_FILE);
            if (content == null || content.trim().isEmpty()) {
                logToUi("Internal backup failed: Could not read original file. Output: " + RootShell.getLastCommandOutput());
                return;
            }

            File backupDir = new File(getCacheDir(), BACKUP_SUBDIR);
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            String backupFileName = "AUI_PREFERENCES_" + timestamp + ".xml";
            File backupFile = new File(backupDir, backupFileName);

            FileOutputStream fos = new FileOutputStream(backupFile);
            fos.write(content.getBytes("UTF-8"));
            fos.close();
            logToUi("Internal backup created: " + backupFileName);
            checkForExistingBackups(); // Update backup count
        } catch (Exception e) {
            logToUi("Internal backup error: " + e.getMessage());
        }
    }


    private String readFileContent(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();
        return new String(data, "UTF-8");
    }

    private void parseAuiPreferences(String xmlContent) {
        try {
            int startIndex = xmlContent.indexOf("<string name=\"aui_bar_apps_pinned\">") + "<string name=\"aui_bar_apps_pinned\">".length();
            int endIndex = xmlContent.indexOf("</string>", startIndex);

            if (startIndex == -1 || endIndex == -1) {
                throw new Exception("Could not find pinned apps data in file");
            }

            String jsonString = xmlContent.substring(startIndex, endIndex);
            jsonString = jsonString.replace("&quot;", "\"").replace("&amp;", "&");

            JSONArray appsArray = new JSONArray(jsonString);
            appList.clear();
            logToUi("Found " + appsArray.length() + " pinned apps.");

            for (int i = 0; i < appsArray.length(); i++) {
                JSONObject appObj = appsArray.getJSONObject(i);
                String packageName = appObj.getString("packageName");
                String type = appObj.getString("type");
                String platformName = appObj.getString("platformName");
                String activity = appObj.optString("activity", "");

                appList.add(new AppInfo(packageName, type, platformName, activity));
            }

        } catch (JSONException e) {
            logToUi("JSON parsing failed: " + e.getMessage());
            throw new RuntimeException("Failed to parse app data", e);
        } catch (Exception e) {
            logToUi("Parsing error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void onAppReorder(int from, int to) {
        if (from != to) {
            AppInfo app = appList.remove(from);
            appList.add(to, app);
            adapter.notifyItemMoved(from, to);
            binding.saveButton.setEnabled(true);
            logToUi("Reordered app from position " + (from + 1) + " to " + (to + 1));
        }
    }

    private void onAppClick(int position) {
        logToUi("Tapped on app at position " + (position + 1));
        showAppSelectionDialog(position);
    }

    private void onAppRemove(int position) {
        logToUi("Removing app from position " + (position + 1));
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
        logToUi("Showing app selection dialog...");
        AppSelectionDialog dialog = new AppSelectionDialog(this, (selectedApp) -> {
            showActivitySelectionDialog(selectedApp, editPosition);
        });
        dialog.show();
    }

    private void showActivitySelectionDialog(InstalledAppInfo selectedApp, int editPosition) {
        logToUi("Showing activity selection dialog for " + selectedApp.appName);
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
                    logToUi("Added new app: " + selectedApp.appName);
                    updateAddButtonState();
                }
            } else {
                appList.set(editPosition, newAppInfo);
                adapter.notifyItemChanged(editPosition);
                logToUi("Updated app at position " + (editPosition + 1) + ": " + selectedApp.appName);
            }

            binding.saveButton.setEnabled(true);
        });
        dialog.show();
    }

    private void saveChanges() {
        logToUi("Starting save process...");
        new Thread(() -> {
            try {
                logToUi("Converting app list to JSON...");
                JSONArray newAppsArray = new JSONArray();
                for (AppInfo app : appList) {
                    JSONObject appObj = new JSONObject();
                    appObj.put("packageName", app.packageName);
                    appObj.put("type", app.type);
                    appObj.put("platformName", app.platformName);
                    if (!app.activity.isEmpty()) {
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
                logToUi("Writing changes to target file...");
                boolean success = RootShell.writeFileContent(TARGET_FILE, newXmlContent);

                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, "Changes saved successfully!\nRestart Oculus system to see changes.",
                                Toast.LENGTH_LONG).show();
                        binding.saveButton.setEnabled(false);
                        logToUi("Changes saved successfully.");
                    } else {
                        Toast.makeText(this, "Save failed: check log for details.", Toast.LENGTH_SHORT).show();
                        logToUi("Save failed. Last command output: " + RootShell.getLastCommandOutput());
                    }
                });

            } catch (Exception e) {
                logToUi("Save error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Save error: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // New method to log messages to the UI and console
    private void logToUi(String message) {
        runOnUiThread(() -> {
            binding.logTextView.append(message + "\n");
            binding.logScrollView.fullScroll(View.FOCUS_DOWN);
        });
    }
}