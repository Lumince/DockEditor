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

    /**
     * Initializes a persistent root shell.
     * Call this before running any commands.
     * @param suCommand The command to use for obtaining root, e.g., "su" or "su --mount-master".
     */
    public static boolean initRootShell(String suCommand) {
        if (rootProcess != null) return true; // already initialized
        try {
            rootProcess = Runtime.getRuntime().exec(suCommand);
            rootOutput = new DataOutputStream(rootProcess.getOutputStream());
            rootStdout = new BufferedReader(new InputStreamReader(rootProcess.getInputStream()));
            rootStderr = new BufferedReader(new InputStreamReader(rootProcess.getErrorStream()));

            // Verify root access by running "id"
            rootOutput.writeBytes("id\n");
            rootOutput.flush();
            String line = rootStdout.readLine(); // Read initial output
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

    /**
     * Shuts down the persistent root shell.
     */
    public static void shutdown() {
        if (rootProcess != null) {
            try {
                rootOutput.writeBytes("exit\n");
                rootOutput.flush();
                rootOutput.close();
                rootStdout.close();
                rootStderr.close();
                rootProcess.waitFor();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore the interrupted status
            } finally {
                rootProcess.destroy();
                rootProcess = null;
                rootOutput = null;
                rootStdout = null;
                rootStderr = null;
            }
        }
    }

    /**
     * Executes a single command in the persistent root shell.
     * Returns the output from stdout and stderr.
     */
    public static String executeCommand(String command) {
        List<String> commands = new ArrayList<>();
        commands.add(command);
        return executeCommands(commands);
    }

    /**
     * Executes multiple commands in the same persistent root shell session.
     * Returns the aggregated output from stdout and stderr.
     */
    public static String executeCommands(List<String> commands) {
        if (rootProcess == null || rootOutput == null || rootStdout == null || rootStderr == null) {
            lastCommandOutput = "Root shell not initialized or streams are null.";
            return lastCommandOutput;
        }

        StringBuilder output = new StringBuilder();
        try {
            for (String command : commands) {
                rootOutput.writeBytes(command + "\n");
            }
            // Add a unique marker to indicate the end of command output
            rootOutput.writeBytes("echo --END_OF_COMMAND--\n");
            rootOutput.flush();

            String line;
            while ((line = rootStdout.readLine()) != null) {
                if (line.equals("--END_OF_COMMAND--")) {
                    break;
                }
                output.append(line).append("\n");
            }

            // Read any remaining stderr
            while (rootStderr.ready() && (line = rootStderr.readLine()) != null) {
                output.append("STDERR: ").append(line).append("\n");
            }

            lastCommandOutput = output.toString();
            return lastCommandOutput;
        } catch (IOException e) {
            lastCommandOutput = "Exception in executeCommands: " + e.getMessage();
            return lastCommandOutput;
        }
    }
    
    /**
     * Reads a file's contents with direct root access.
     */
    public static String getFileContent(String filePath) {
        String command = "cat \"" + filePath + "\"";
        String result = executeCommand(command);
        
        // Check for error messages that might indicate permission issues or file not found
        if (result != null && (result.contains("Permission denied") || result.contains("No such file or directory") || result.trim().isEmpty())) {
            // Log the full output for debugging
            lastCommandOutput = result; 
            return null; // Return null to indicate failure to read content
        }
        return result != null ? result.trim() : null;
    }
    
    /**
     * Writes content to a file safely with direct root access.
     */
    public static boolean writeFileContent(String filePath, String content) {
        List<String> commands = new ArrayList<>();
        // Use printf for better handling of multi-line content and special characters
        commands.add("printf '%s' '" + content.replace("'", "'\"'\"'") + "' > \"" + filePath + "\"");
        commands.add("chmod 660 \"" + filePath + "\"");
        commands.add("chown system:system \"" + filePath + "\"");
        String result = executeCommands(commands);

        // Check for error messages
        if (result != null && (result.contains("Permission denied") || result.contains("No such file or directory"))) {
            lastCommandOutput = result;
            return false;
        }
        return true;
    }

    /**
     * Copy file from one location to another
     */
    public static boolean copyFile(String sourcePath, String destPath) {
        String result = executeCommand("cp \"" + sourcePath + "\" \"" + destPath + "\"");
        return result != null && !result.contains("Permission denied") && !result.contains("No such file or directory");
    }

    /**
     * Returns the output of the last executed command.
     */
    public static String getLastCommandOutput() {
        return lastCommandOutput;
    }
}