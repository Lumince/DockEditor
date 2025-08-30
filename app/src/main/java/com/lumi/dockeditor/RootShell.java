package com.lumi.dockeditor;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.ArrayList;

public class RootShell {

    private static Process rootProcess;
    private static DataOutputStream rootOutput;
    private static BufferedReader rootStdout;
    private static BufferedReader rootStderr;

    private static String lastCommandOutput = "";

    public static boolean initRootShell(String suCommand) {
        if (rootProcess != null) return true;
        try {
            rootProcess = Runtime.getRuntime().exec(suCommand);
            rootOutput = new DataOutputStream(rootProcess.getOutputStream());
            rootStdout = new BufferedReader(new InputStreamReader(rootProcess.getInputStream()));
            rootStderr = new BufferedReader(new InputStreamReader(rootProcess.getErrorStream()));

            rootOutput.writeBytes("id\n");
            rootOutput.flush();
            String line = rootStdout.readLine();
            if (line != null && line.contains("uid=0")) {
                return true;
            } else {
                shutdown();
                return false;
            }
        } catch (Exception e) {
            lastCommandOutput = "Failed to initialize root shell: " + e.getMessage();
            shutdown();
            return false;
        }
    }

    public static void shutdown() {
        if (rootProcess != null) {
            try {
                rootOutput.writeBytes("exit\n");
                rootOutput.flush();
                rootOutput.close();
                rootStdout.close();
                rootStderr.close();
                rootProcess.waitFor();
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                rootProcess.destroy();
                rootProcess = null;
            }
        }
    }

    public static String executeCommand(String command) {
        List<String> commands = new ArrayList<>();
        commands.add(command);
        return executeCommands(commands);
    }

    public static String executeCommands(List<String> commands) {
        if (rootProcess == null || rootOutput == null || rootStdout == null) {
            lastCommandOutput = "Root shell not initialized.";
            return lastCommandOutput;
        }

        StringBuilder output = new StringBuilder();
        try {
            for (String command : commands) {
                rootOutput.writeBytes(command + " 2>&1\n");
            }
            rootOutput.writeBytes("echo --END_OF_COMMAND--\n");
            rootOutput.flush();

            String line;
            while ((line = rootStdout.readLine()) != null) {
                if (line.contains("--END_OF_COMMAND--")) {
                    break;
                }
                output.append(line).append("\n");
            }
            lastCommandOutput = output.toString();
            return lastCommandOutput;
        } catch (IOException e) {
            lastCommandOutput = "Exception in executeCommands: " + e.getMessage();
            return lastCommandOutput;
        }
    }
    
    public static String getFileContent(String filePath) {
        String command = "cat \"" + filePath + "\"";
        String result = executeCommand(command);
        
        if (result != null && (result.contains("Permission denied") || result.contains("No such file or directory") || result.trim().isEmpty())) {
            lastCommandOutput = result; 
            return null;
        }
        return result != null ? result.trim() : null;
    }
    
    public static boolean writeFileContent(String filePath, String content) {
        String contextResult = executeCommand("ls -Z \"" + filePath + "\"").trim();
        String selinuxContext = null;
        if (!contextResult.isEmpty() && !contextResult.contains("No such file or directory")) {
            selinuxContext = contextResult.split("\\s+")[0];
        }

        List<String> commands = new ArrayList<>();
        commands.add("printf '%s' '" + content.replace("'", "'\"'\"'") + "' > \"" + filePath + "\"");
        
        // ** THIS IS THE CORRECTED LINE **
        commands.add("chmod 666 \"" + filePath + "\"");
        
        commands.add("chown system:system \"" + filePath + "\"");

        if (selinuxContext != null && !selinuxContext.contains("?")) {
            commands.add("chcon '" + selinuxContext + "' \"" + filePath + "\"");
        }

        String result = executeCommands(commands);

        return result == null || (!result.contains("Permission denied") && !result.contains("No such file or directory"));
    }

    public static String getLastCommandOutput() {
        return lastCommandOutput;
    }
}