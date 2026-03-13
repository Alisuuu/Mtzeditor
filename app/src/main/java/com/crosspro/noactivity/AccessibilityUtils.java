package com.crosspro.noactivity;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AccessibilityUtils {

    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> service) {
        String pref = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (pref == null) return false;
        return pref.contains(service.getName());
    }

    public static boolean enableAccessibilityService(String serviceName) {
        // This requires Shizuku or Root to execute 'settings put secure ...'
        try {
            String enabledServices = ShizukuUtils.execShizukuString("settings get secure enabled_accessibility_services");
            if (enabledServices == null) enabledServices = "";
            enabledServices = enabledServices.trim();

            if (enabledServices.contains(serviceName)) return true;

            List<String> services = new ArrayList<>();
            if (!enabledServices.isEmpty() && !enabledServices.equals("null")) {
                services.addAll(Arrays.asList(enabledServices.split(":")));
            }
            services.add(serviceName);
            String newServices = TextUtils.join(":", services);

            return ShizukuUtils.execShizuku("settings put secure enabled_accessibility_services " + newServices) &&
                   ShizukuUtils.execShizuku("settings put secure accessibility_enabled 1");
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
