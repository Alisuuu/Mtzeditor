package com.crosspro.noactivity;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.accessibility.AccessibilityEvent;
import android.util.Log;
import android.os.Build;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;

public class HyperThemeService extends AccessibilityService {

    private static final String TAG = "HyperThemeService";
    private static final String MI_CHECK_ACTION = "miui.intent.action.CHECK_TIME_UP";
    private static final String CHANNEL_ID = "hyper_theme_monitor";
    private static final String MIUI_SNAPSHOT_PATH = "/sdcard/Android/data/com.android.thememanager/files/snapshot/snapshot.mtz";

    private volatile boolean isServiceAlive = false;
    private Thread monitorThread;

    private final BroadcastReceiver themeCheckReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && MI_CHECK_ACTION.equals(intent.getAction())) {
                Log.d(TAG, "Intercepted MIUI theme check broadcast.");
                // Some versions of MIUI check for custom themes via this broadcast
                try {
                    if (isOrderedBroadcast()) {
                        abortBroadcast();
                        Log.d(TAG, "Ordered broadcast aborted.");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to abort broadcast", e);
                }
                
                // Even if we can't abort, we trigger a restoration check
                triggerRestoration();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        isServiceAlive = true;
        createNotificationChannel();
        startForeground(1, createNotification());

        IntentFilter filter = new IntentFilter();
        filter.addAction(MI_CHECK_ACTION);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.setPriority(Integer.MAX_VALUE);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(themeCheckReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(themeCheckReceiver, filter);
        }
        
        startMonitoringThread();
        Log.d(TAG, "HyperThemeService Created and Monitoring Thread Started");
    }

    private void startMonitoringThread() {
        monitorThread = new Thread(() -> {
            Log.d(TAG, "Background monitor thread started.");
            while (isServiceAlive) {
                try {
                    // Stay alive and check theme status periodically
                    // This helps the system know we are doing active work
                    checkThemeConsistency();
                    
                    // Sleep for a while. Shorter interval if screen is off? 
                    // Let's use 30 seconds for balance.
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    Log.d(TAG, "Monitor thread interrupted.");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Error in monitor loop", e);
                }
            }
        });
        monitorThread.setName("ThemeMonitorThread");
        monitorThread.start();
    }

    private void checkThemeConsistency() {
        android.content.SharedPreferences prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE);
        boolean autoRestore = prefs.getBoolean("auto_restore_enabled", false);
        String lastApplied = prefs.getString("last_applied_mtz", null);

        if (autoRestore && lastApplied != null) {
            // Check if MIUI's snapshot file exists and is readable
            // We use Shizuku to check because it might be inaccessible otherwise
            String checkCmd = "if [ ! -f " + MIUI_SNAPSHOT_PATH + " ]; then echo 'missing'; fi";
            String result = ShizukuUtils.execShizukuString(checkCmd);
            
            if (result != null && result.contains("missing")) {
                Log.w(TAG, "MIUI Snapshot missing! Attempting restoration...");
                triggerRestoration();
            }
        }
    }

    private void triggerRestoration() {
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.post(() -> {
            android.content.SharedPreferences prefs = getSharedPreferences("theme_prefs", MODE_PRIVATE);
            String lastApplied = prefs.getString("last_applied_mtz", null);
            if (lastApplied != null) {
                new Thread(() -> {
                    Log.d(TAG, "Restoring theme from: " + lastApplied);
                    ShizukuUtils.execShizuku("cp -f " + lastApplied + " " + MIUI_SNAPSHOT_PATH);
                    ShizukuUtils.execShizuku("chmod 666 " + MIUI_SNAPSHOT_PATH);
                }).start();
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Monitor de Temas",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Monitor de Temas")
                .setContentText("O monitor de temas da MIUI está rodando.")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isServiceAlive = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
        try {
            unregisterReceiver(themeCheckReceiver);
        } catch (Exception ignored) {}
        Log.d(TAG, "HyperThemeService Destroyed");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence packageName = event.getPackageName();
            if (packageName != null && "com.android.thememanager".equals(packageName.toString())) {
                Log.d(TAG, "Theme Manager window detected");
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Accessibility Service Connected and Monitoring MIUI Theme Manager");
        // We could show a Toast here to let the user know it's active
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.post(() -> android.widget.Toast.makeText(getApplicationContext(), "Monitor de Temas Ativado!", android.widget.Toast.LENGTH_SHORT).show());
    }
}
