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

        TextView splashTitle = findViewById(R.id.splashTitle);
        if (splashTitle != null) {
            splashTitle.setText(AppConfig.LIBRARY_NAME);
            splashTitle.setAlpha(0f); 
            splashTitle.setTranslationY(50); 
            splashTitle.animate().alpha(1f).translationY(0).setDuration(1500).start();
        }

        prefs = getSharedPreferences("LibraryApp", Context.MODE_PRIVATE);

        startNotificationService();

        // 🔥 NAYA: Splash screen chalte waqt background me AppFeatures fetch kar lo
        fetchAppFeaturesAndCache();

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
            e.printStackTrace(); 
        }
    }

    // 🔥 NAYA METHOD: Firebase se AppFeatures fetch karna
    private void fetchAppFeaturesAndCache() {
        DatabaseReference featuresRef = FirebaseDatabase.getInstance().getReference("AppFeatures");
        featuresRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String ipAuth = snapshot.child("Ip").getValue(String.class);
                    String qrUpload = snapshot.child("QrUpload").getValue(String.class);
                    
                    // Permanent Cache Save (Jab tak user clear data na kare)
                    SharedPreferences.Editor editor = prefs.edit();
                    if (ipAuth != null) editor.putString("feature_ip", ipAuth);
                    if (qrUpload != null) editor.putString("feature_qr_upload", qrUpload);
                    editor.apply();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void checkLoginStatus() {
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);
        String savedUsername = prefs.getString("username", "");

        if (isLoggedIn && !savedUsername.isEmpty()) {
            DatabaseReference statusRef = FirebaseDatabase.getInstance().getReference("Students").child(savedUsername).child("status");
            
            statusRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String currentStatus = snapshot.getValue(String.class);
                        if (currentStatus != null && currentStatus.equalsIgnoreCase("Approved")) {
                            goToDashboard();
                        } else {
                            Toast.makeText(MainActivity.this, "Your account is " + currentStatus + ". Please contact Admin.", Toast.LENGTH_LONG).show();
                            clearDataAndLogout();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Account not found. Please contact Admin.", Toast.LENGTH_LONG).show();
                        clearDataAndLogout();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    goToDashboard();
                }
            });
        } else {
            goToLogin();
        }
    }

    private void goToDashboard() {
        Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
        startActivity(intent);
        finish(); 
    }

    private void goToLogin() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        startActivity(intent);
        finish(); 
    }

    private void clearDataAndLogout() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
        goToLogin();
    }
}
