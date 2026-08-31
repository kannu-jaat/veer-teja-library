package com.google.android.youtube.pro;

import android.app.Activity;
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

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

public class MainActivity extends Activity {

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

        startNotificationService();

        // Splash screen chalte waqt background me AppFeatures fetch kar lo
        fetchAppFeaturesAndCache();

        // Handler initialize kiya (taki app close hone pe cancel kar sakein)
        splashHandler = new Handler(Looper.getMainLooper());
        splashRunnable = new Runnable() {
            @Override
            public void run() {
                // Firebase check karne se pehle actual internet/ping check karenge
                checkRealInternet();
            }
        };
        splashHandler.postDelayed(splashRunnable, 2500);
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

    // 🔥 NAYA: Real Ping Test - Check karega ki sach me net / recharge hai ya nahi
    private void checkRealInternet() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean hasInternet = false;
                try {
                    // Google DNS ko ping karke check karna (1.5 sec timeout)
                    int timeoutMs = 1500;
                    Socket sock = new Socket();
                    SocketAddress sockaddr = new InetSocketAddress("8.8.8.8", 53);
                    sock.connect(sockaddr, timeoutMs);
                    sock.close();
                    hasInternet = true;
                } catch (IOException e) {
                    hasInternet = false;
                }

                final boolean finalHasInternet = hasInternet;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        checkLoginStatus(finalHasInternet);
                    }
                });
            }
        }).start();
    }

    // 🔥 MODIFIED: Internet status ke hisaab se action lega
    private void checkLoginStatus(boolean isOnline) {
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);
        String savedUsername = prefs.getString("username", "");

        if (isLoggedIn && !savedUsername.isEmpty()) {
            if (isOnline) {
                // Net hai toh Firebase se live status check karo
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
                        goToDashboard(); // Default fallback
                    }
                });
            } else {
                // Net nahi hai (Offline/Bina Recharge) -> Seedha purane cache se dashboard load karo
                Toast.makeText(MainActivity.this, "You are Offline. Loading saved data...", Toast.LENGTH_SHORT).show();
                goToDashboard();
            }
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

    // 🔥 FIX: Lag issue aur Cache clear properly hoga ab
    private void clearDataAndLogout() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear(); // Saara data saaf karega
        editor.commit(); // apply() background me hota hai, commit() instant karta hai jisse lag na aaye

        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        // Ye flags purani Id ki Activity/Memory stack ko puri tarah hta denge, app fresh ho jayegi
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Memory Leak aur crash bachane ke liye (Agar user splash par app back/close kar de)
        if (splashHandler != null && splashRunnable != null) {
            splashHandler.removeCallbacks(splashRunnable);
        }
    }
}
