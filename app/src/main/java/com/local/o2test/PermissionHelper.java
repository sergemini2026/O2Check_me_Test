package com.local.o2test;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

public class PermissionHelper {

    public static final int PERMISSION_REQUEST_CODE = 101;

    public static boolean checkAndRequestPermissions(Activity activity) {
        List<String> permissions = new ArrayList<>();

        // Проверка разрешений на Bluetooth (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }
        
        // Проверка геолокации (обязательна для сканирования BLE на Android)
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        // Проверка разрешений на хранилище для старых версий Android
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        if (!permissions.isEmpty()) {
            activity.requestPermissions(permissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            return false;
        }

        // Для Android 11+ проверяем специальный доступ к управлению всеми файлами (для импорта/экспорта бэкапов в Documents/O2Test)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    activity.startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(intent);
                }
                return false;
            }
        }

        return true;
    }
}
