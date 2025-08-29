package com.lumi.dockeditor;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
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
    
    private static final String TARGET_FILE = "/data/user/0/com.oculus.systemux/shared_prefs/AUI_PREFERENCES.xml";
    private static final String BACKUP_SUBDIR = "backups";
    
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
        
        setupButtons();
        checkRootAccess();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shutdown the persistent root shell when the activity is destroyed
        RootShell.shutdown();
    }
    
    private void setupButtons() {
        binding.loadButton.setOnClickListener(v -> loadAndParseFile());
        binding.backupButton.setOnClickListener(v -> backupFile());
        binding.restoreBackupButton.setOnClickListener(v -> showRestoreBackupDialog());
        binding.restoreDefaultButton.setOnClickListener(v -> showRestoreDefaultDialog());
        
        binding.runShellButton.setOnClickListener(v -> runShellCommand());
    }

    private void checkRootAccess() {
        logToUi("Checking for root access...");
        new Thread(() -> {
            boolean hasRoot = RootShell.initRootShell("su --mount-master");

            // **CHANGE IS HERE**: Backup is now created on the background thread after root is confirmed.
            if (hasRoot) {
                logToUi("Root access granted. Creating automatic startup backup...");
                backupFileInternal();
            }

            runOnUiThread(() -> {
                if (hasRoot) {
                    binding.statusText.setText("Root access granted ✓");
                    binding.backupButton.setEnabled(true);
                    binding.restoreDefaultButton.setEnabled(true);
                    binding.loadButton.setEnabled(true);
                    binding.runShellButton.setEnabled(true);
                    logToUi("Root access granted with --mount-master.");
                    checkSelinuxStatus();
                    checkSelinuxContext();
                    // The backup check is now called from within backupFileInternal,
                    // so we don't need to call it here again.
                } else {
                    binding.statusText.setText("Root access denied ✗");
                    Toast.makeText(this, "Root access is required for this app to function",
                            Toast.LENGTH_LONG).show();
                    logToUi("Root access denied.");
                }
            });
        }).start();
    }
    
    private void loadAndParseFile() {
        logToUi("Loading and parsing file...");
        new Thread(() -> {
            try {
                // **CHANGE IS HERE**: The automatic backup call has been removed from this method.
                logToUi("Reading content from target file " + TARGET_FILE + "...");

                String content = RootShell.getFileContent(TARGET_FILE);
                if (content == null || content.trim().isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to read original file", Toast.LENGTH_SHORT).show());
                    logToUi("Failed to read original file. Output: " + RootShell.getLastCommandOutput());
                    return;
                }

                logToUi("Parsing XML content...");
                ArrayList<AppInfo> parsedAppList = parseAuiPreferences(content);

                runOnUiThread(() -> {
                    Toast.makeText(this, "File loaded successfully, opening editor...", Toast.LENGTH_SHORT).show();
                    logToUi("File loaded and parsed successfully. Launching editor.");
                    
                    Intent intent = new Intent(MainActivity.this, EditPinnedActivity.class);
                    intent.putParcelableArrayListExtra("appList", parsedAppList);
                    startActivity(intent);
                });

            } catch (Exception e) {
                logToUi("Load error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Load error: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    
    private ArrayList<AppInfo> parseAuiPreferences(String xmlContent) {
        ArrayList<AppInfo> localAppList = new ArrayList<>();
        try {
            int startIndex = xmlContent.indexOf("<string name=\"aui_bar_apps_pinned\">") + "<string name=\"aui_bar_apps_pinned\">".length();
            int endIndex = xmlContent.indexOf("</string>", startIndex);

            if (startIndex == -1 || endIndex == -1) {
                throw new Exception("Could not find pinned apps data in file");
            }

            String jsonString = xmlContent.substring(startIndex, endIndex);
            jsonString = jsonString.replace("&quot;", "\"").replace("&amp;", "&");

            JSONArray appsArray = new JSONArray(jsonString);
            logToUi("Found " + appsArray.length() + " pinned apps.");

            for (int i = 0; i < appsArray.length(); i++) {
                JSONObject appObj = appsArray.getJSONObject(i);
                String packageName = appObj.getString("packageName");
                String type = appObj.getString("type");
                String platformName = appObj.getString("platformName");
                String activity = appObj.optString("activity", "");

                localAppList.add(new AppInfo(packageName, type, platformName, activity));
            }
            return localAppList;

        } catch (JSONException e) {
            logToUi("JSON parsing failed: " + e.getMessage());
            throw new RuntimeException("Failed to parse app data", e);
        } catch (Exception e) {
            logToUi("Parsing error: " + e.getMessage());
            throw new RuntimeException(e);
        }
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
                    binding.selinuxStatusText.setText("SELinux: Enforcing ⚠️");
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
                        binding.restoreBackupButton.setEnabled(true);
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
                String backupContent = readFileContent(backupFile);
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
            // This is called from a background thread, so we need to switch to the UI thread
            // to update the backup button's text and state.
            runOnUiThread(this::checkForExistingBackups);
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

    private void logToUi(String message) {
        runOnUiThread(() -> {
            binding.logTextView.append(message + "\n");
            binding.logScrollView.fullScroll(View.FOCUS_DOWN);
        });
    }
}