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

        // Animation ka Jadoo
        TextView splashTitle = findViewById(R.id.splashTitle);
        splashTitle.setAlpha(0f); // Pehle chhupa do
        splashTitle.setTranslationY(50); // Thoda niche set karo
        
        // 1.5 seconds me upar aakar dikhega
        splashTitle.animate().alpha(1f).translationY(0).setDuration(1500).start();

        prefs = getSharedPreferences("LibraryApp", Context.MODE_PRIVATE);
        startNotificationService();

        // 2.5 Second ka timer (2500 milliseconds)
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                checkLoginStatus();
            }
        }, 2500); 
    }

    private void startNotificationService() {
        Intent serviceIntent = new Intent(this, ForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void checkLoginStatus() {
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);
        
        if (isLoggedIn) {
            // Agar pehle se login hai toh Dashboard par jayega (Aage banayenge)
            // Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
            // startActivity(intent);
        } else {
            // Naya user hai toh Login Screen par bhej do
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
        }
        finish(); // Splash screen ko hamesha ke liye band kar do taaki back dabane par wapas na aaye
    }
}
