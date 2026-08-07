package com.google.android.youtube.pro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends Activity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // UI Elements
        TextView splashTitle = findViewById(R.id.splashTitle);

        // 1. AppConfig se naam automatically yahan set hoga
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

    // 🔥 UPDATED: Advanced Login Check with Firebase Approval System
    private void checkLoginStatus() {
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);
        String savedUsername = prefs.getString("username", "");

        if (isLoggedIn && !savedUsername.isEmpty()) {
            // Agar purana user hai, toh Firebase se live status check karo
            DatabaseReference statusRef = FirebaseDatabase.getInstance().getReference("Students").child(savedUsername).child("status");
            
            statusRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String currentStatus = snapshot.getValue(String.class);
                        
                        if (currentStatus != null && currentStatus.equalsIgnoreCase("Approved")) {
                            // Sab theek hai, Dashboard par jaane do
                            goToDashboard();
                        } else {
                            // Agar Pending, Rejected ya Blocked hai
                            Toast.makeText(MainActivity.this, "Your account is " + currentStatus + ". Please contact Admin.", Toast.LENGTH_LONG).show();
                            clearDataAndLogout();
                        }
                    } else {
                        // User database me exist hi nahi karta (Admin ne delete kar diya)
                        Toast.makeText(MainActivity.this, "Account not found. Please contact Admin.", Toast.LENGTH_LONG).show();
                        clearDataAndLogout();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // Agar internet nahi hai, toh purana cache dashboard open hone do
                    goToDashboard();
                }
            });

        } else {
            // Naya user hai toh Login Screen par bhej do
            goToLogin();
        }
    }

    // Helper Methods to keep code clean
    private void goToDashboard() {
        Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
        startActivity(intent);
        finish(); // Splash screen hamesha ke liye band
    }

    private void goToLogin() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        startActivity(intent);
        finish(); // Splash screen hamesha ke liye band
    }

    private void clearDataAndLogout() {
        // Saara offline cache aur login details delete kardo
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();

        // Data delete hone ke baad Login screen dikhao
        goToLogin();
    }
}
