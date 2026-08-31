package com.google.android.youtube.pro;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity; // Activity ki jagah AppCompatActivity use karein

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private Handler splashHandler;
    private Runnable splashRunnable;

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

        // App Features Firebase background me load karega, app hang nahi hogi
        fetchAppFeaturesAndCache();

        // Handler ko class level pe rakha hai taaki crash na ho
        splashHandler = new Handler(Looper.getMainLooper());
        splashRunnable = new Runnable() {
            @Override
            public void run() {
                checkInternetAndProceed();
            }
        };
        splashHandler.postDelayed(splashRunnable, 2500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Memory leak bachane ke liye handler cancel karna zaroori hai
        if (splashHandler != null && splashRunnable != null) {
            splashHandler.removeCallbacks(splashRunnable);
        }
    }

    private void fetchAppFeaturesAndCache() {
        DatabaseReference featuresRef = FirebaseDatabase.getInstance().getReference("AppFeatures");
        featuresRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String ipAuth = snapshot.child("Ip").getValue(String.class);
                    String qrUpload = snapshot.child("QrUpload").getValue(String.class);

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

    // 🔥 FIX 1: Real Internet (Ping) Check
    private void checkInternetAndProceed() {
        new Thread(() -> {
            boolean hasInternet = isInternetAvailable();
            runOnUiThread(() -> {
                if (!hasInternet) {
                    Toast.makeText(MainActivity.this, "Offline Mode: Loading saved data...", Toast.LENGTH_SHORT).show();
                }
                checkLoginStatus(hasInternet);
            });
        }).start();
    }

    // Ping check: Google DNS (8.8.8.8) ko check karega ki net chal raha hai ya nahi
    private boolean isInternetAvailable() {
        try {
            int timeoutMs = 1500;
            Socket sock = new Socket();
            SocketAddress sockaddr = new InetSocketAddress("8.8.8.8", 53);
            sock.connect(sockaddr, timeoutMs);
            sock.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // 🔥 FIX 2: Offline me Cache Data se Login
    private void checkLoginStatus(boolean isOnline) {
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);
        String savedUsername = prefs.getString("username", "");

        if (isLoggedIn && !savedUsername.isEmpty()) {
            if (isOnline) {
                DatabaseReference statusRef = FirebaseDatabase.getInstance().getReference("Students").child(savedUsername).child("status");
                statusRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String currentStatus = snapshot.getValue(String.class);
                            if (currentStatus != null && currentStatus.equalsIgnoreCase("Approved")) {
                                
                                // ✅ ONLINE: Status cache me save kar lo taki offline pe kaam aaye
                                prefs.edit().putString("cached_status", currentStatus).apply();
                                
                                startNotificationService();
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
                        // Error aaye toh offline wala logic chala do
                        handleOfflineLogin();
                    }
                });
            } else {
                // User offline hai, Firebase skip karke seedha cache check karo
                handleOfflineLogin();
            }
        } else {
            goToLogin();
        }
    }

    private void handleOfflineLogin() {
        String cachedStatus = prefs.getString("cached_status", "");
        if (cachedStatus.equalsIgnoreCase("Approved")) {
            startNotificationService();
            goToDashboard();
        } else {
            Toast.makeText(this, "Please connect to the internet to verify your account first time.", Toast.LENGTH_LONG).show();
            // Optional: goToLogin();
        }
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

    // 🔥 FIX 3: Logout Cache, Service aur Lag Problem Fix
    public void clearDataAndLogout() {
        // 1. Purani chal rahi Foreground Service ko force stop karein (Isse RAM free hogi)
        Intent serviceIntent = new Intent(this, ForegroundService.class);
        stopService(serviceIntent);

        // 2. SharedPreferences pura clean karein
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();

        // 3. Nayi Activity kholte waqt Flags use karein taki purani Activity background se clear ho jaye (Lag hatane ke liye best)
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
