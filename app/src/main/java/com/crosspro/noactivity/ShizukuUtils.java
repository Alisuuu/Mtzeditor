package com.crosspro.noactivity;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

public class ShizukuUtils {

    private static final String TAG = "ShizukuUtils";

    public static boolean isShizukuAvailable() {
        return Shizuku.pingBinder();
    }

    public static boolean hasPermission() {
        if (!isShizukuAvailable()) return false;
        return Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    public static boolean execShizuku(String command) {
        try {
            if (!hasPermission()) return false;
            Log.d(TAG, "Executing: " + command);
            String[] cmd = {"sh", "-c", command};
            
            java.lang.reflect.Method newProcessMethod = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            newProcessMethod.setAccessible(true);
            ShizukuRemoteProcess process = (ShizukuRemoteProcess) newProcessMethod.invoke(null, cmd, null, null);
            
            // Read error stream
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line;
            while ((line = errorReader.readLine()) != null) {
                Log.e(TAG, "Shizuku Shell Error: " + line);
            }

            int exitCode = process.waitFor();
            Log.d(TAG, "Command exited with code: " + exitCode);
            return exitCode == 0;
        } catch (Exception e) {
            Log.e(TAG, "Error executing Shizuku command: " + command, e);
            return false;
        }
    }

    public static String execShizukuString(String command) {
        try {
            if (!hasPermission()) return null;
            String[] cmd = {"sh", "-c", command};
            
            java.lang.reflect.Method newProcessMethod = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            newProcessMethod.setAccessible(true);
            ShizukuRemoteProcess process = (ShizukuRemoteProcess) newProcessMethod.invoke(null, cmd, null, null);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();
            return output.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error executing Shizuku command: " + command, e);
            return null;
        }
    }
}
