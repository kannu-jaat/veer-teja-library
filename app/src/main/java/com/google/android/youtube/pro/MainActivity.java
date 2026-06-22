package com.google.android.youtube.pro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

public class MainActivity extends Activity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // UI Elements
        TextView splashTitle = findViewById(R.id.splashTitle);
        
        // 1. AppConfig se naam automatically yahan set hoga (Ek file, poora app update)
        if (splashTitle != null) {
            splashTitle.setText(AppConfig.LIBRARY_NAME);

            // Animation ka Jadoo
            splashTitle.setAlpha(0f); 
            splashTitle.setTranslationY(50); 
            splashTitle.animate().alpha(1f).translationY(0).setDuration(1500).start();
        }

        prefs = getSharedPreferences("LibraryApp", Context.MODE_PRIVATE);
        
        // Background notification service start
        startNotificationService();

        // 2.5 Second ka timer
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                checkLoginStatus();
            }
        }, 2500); 
    }

    private void startNotificationService() {
        try {
            Intent serviceIntent = new Intent(this, ForegroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            e.printStackTrace(); // Safe catch jisse background restriction se crash na ho
        }
    }

    private void checkLoginStatus() {
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);

        if (isLoggedIn) {
            // ✅ CRASH FIXED: Ab agar login hai, toh sidha Dashboard jayega (Comment hata diya hai)
            Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
            startActivity(intent);
        } else {
            // Naya user hai toh Login Screen par bhej do
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
        }
        
        // Splash screen ko hamesha ke liye band kar do taaki back dabane par wapas na aaye
        finish(); 
    }
}
