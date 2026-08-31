package com.google.android.youtube.pro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends Activity {

    // ============================================================
    // EXISTING STRUCTURE - DO NOT CHANGE
    // ============================================================

    private static final String PREF_NAME = "LibraryApp";
    private static final String KEY_LOGIN = "isLoggedIn";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_FEATURE_IP = "feature_ip";
    private static final String KEY_FEATURE_QR = "feature_qr_upload";
    private static final String KEY_LAST_SERVER_VERIFICATION = "last_server_verification";

    private static final long MIN_SPLASH_DURATION_MS = 2500L;
    private static final long STATUS_CHECK_TIMEOUT_MS = 6000L;

    private SharedPreferences prefs;
    private Handler mainHandler;
    private Runnable navigationRunnable;
    private Runnable statusTimeoutRunnable;
    private DatabaseReference statusRef;
    private ValueEventListener statusListener;

    // Added 'volatile' for thread safety across background/main threads
    private volatile boolean navigationStarted = false;
    private volatile boolean statusCheckFinished = false;

    private long splashStartTime;

    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        splashStartTime = System.currentTimeMillis();
        mainHandler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        setupSplash();
        startNotificationService();
        fetchAppFeaturesAndCache();
        checkRealInternet();
    }

    // ============================================================
    // SPLASH WITH YOUTUBE STYLE PROGRESS BAR
    // ============================================================

    private void setupSplash() {
        TextView splashTitle = findViewById(R.id.splashTitle);
        ProgressBar splashProgress = findViewById(R.id.splashProgress); // Add in XML

        if (splashTitle != null) {
            splashTitle.setText(AppConfig.LIBRARY_NAME);
            splashTitle.setAlpha(0f);
            splashTitle.setTranslationY(50f);
            splashTitle.animate().alpha(1f).translationY(0f).setDuration(1500).start();
        }

        // Animating the YouTube-style horizontal progress bar
        if (splashProgress != null) {
            splashProgress.setAlpha(0f);
            splashProgress.animate().alpha(1f).setDuration(1500).start();
        }
    }

    // ============================================================
    // NOTIFICATION SERVICE
    // ============================================================

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

    // ============================================================
    // APP FEATURES CACHE
    // ============================================================

    private void fetchAppFeaturesAndCache() {
        try {
            DatabaseReference featuresRef = FirebaseDatabase.getInstance().getReference("AppFeatures");
            featuresRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!snapshot.exists()) return;

                    String ipAuth = snapshot.child("Ip").getValue(String.class);
                    String qrUpload = snapshot.child("QrUpload").getValue(String.class);

                    SharedPreferences.Editor editor = prefs.edit();
                    if (ipAuth != null) editor.putString(KEY_FEATURE_IP, ipAuth);
                    if (qrUpload != null) editor.putString(KEY_FEATURE_QR, qrUpload);
                    editor.apply();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    error.toException().printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ============================================================
    // INTERNET / NETWORK CHECK (IMPROVED VALIDATION)
    // ============================================================

    private void checkRealInternet() {
        new Thread(() -> {
            boolean hasNetwork = false;
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Network network = cm.getActiveNetwork();
                        if (network != null) {
                            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                            if (capabilities != null) {
                                // Checking both INTERNET and VALIDATED (Actual working internet)
                                hasNetwork = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                          && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                            }
                        }
                    } else {
                        NetworkInfo info = cm.getActiveNetworkInfo();
                        hasNetwork = info != null && info.isConnected();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                hasNetwork = false;
            }

            final boolean result = hasNetwork;
            runOnUiThread(() -> {
                if (isActivityDead()) return;
                checkLoginStatus(result);
            });
        }).start();
    }

    // ============================================================
    // LOGIN STATUS
    // ============================================================

    private void checkLoginStatus(boolean isOnline) {
        if (navigationStarted) return;

        boolean isLoggedIn = prefs.getBoolean(KEY_LOGIN, false);
        String username = prefs.getString(KEY_USERNAME, "");
        username = (username == null) ? "" : username.trim();

        if (!isLoggedIn || username.isEmpty()) {
            navigateAfterSplash(false);
            return;
        }

        if (!isOnline) {
            showCustomToast("Offline Mode\nLoading saved data...", false);
            navigateAfterSplash(true);
            return;
        }

        verifyStudentStatusOnline(username);
    }

    // ============================================================
    // FIREBASE LIVE STATUS CHECK
    // ============================================================

    private void verifyStudentStatusOnline(final String username) {
        if (navigationStarted) return;

        statusCheckFinished = false;
        statusRef = FirebaseDatabase.getInstance().getReference("Students").child(username).child("status");

        statusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (statusCheckFinished || navigationStarted) return;
                statusCheckFinished = true;
                cancelStatusTimeout();

                if (snapshot.exists()) {
                    String currentStatus = snapshot.getValue(String.class);

                    if (currentStatus != null && currentStatus.equalsIgnoreCase("Approved")) {
                        saveLastServerVerification();
                        navigateAfterSplash(true);
                        return;
                    }

                    String statusText = (currentStatus == null) ? "Unknown" : currentStatus;
                    showCustomToast("Your account is " + statusText + ". Please contact Admin.", true);
                    clearDataAndLogout();
                } else {
                    showCustomToast("Account not found.\nPlease contact Admin.", true);
                    clearDataAndLogout();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (statusCheckFinished || navigationStarted) return;
                statusCheckFinished = true;
                cancelStatusTimeout();

                showCustomToast("Server unavailable\nLoading saved data...", false);
                navigateAfterSplash(true);
            }
        };

        statusRef.addListenerForSingleValueEvent(statusListener);

        statusTimeoutRunnable = () -> {
            if (statusCheckFinished || navigationStarted) return;
            statusCheckFinished = true;

            showCustomToast("Server response slow\nLoading saved data...", false);
            navigateAfterSplash(true);
        };

        mainHandler.postDelayed(statusTimeoutRunnable, STATUS_CHECK_TIMEOUT_MS);
    }

    private void saveLastServerVerification() {
        try {
            prefs.edit().putLong(KEY_LAST_SERVER_VERIFICATION, System.currentTimeMillis()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ============================================================
    // SPLASH -> NAVIGATION
    // ============================================================

    private void navigateAfterSplash(final boolean openDashboard) {
        if (navigationStarted) return;

        long elapsed = System.currentTimeMillis() - splashStartTime;
        long remaining = MIN_SPLASH_DURATION_MS - elapsed;

        if (remaining <= 0) {
            doNavigation(openDashboard);
            return;
        }

        if (navigationRunnable != null) {
            mainHandler.removeCallbacks(navigationRunnable);
        }

        navigationRunnable = () -> {
            if (!navigationStarted) {
                doNavigation(openDashboard);
            }
        };

        mainHandler.postDelayed(navigationRunnable, remaining);
    }

    private void doNavigation(boolean openDashboard) {
        if (navigationStarted || isActivityDead()) return;
        navigationStarted = true;

        cancelNavigationCallback();
        cancelStatusTimeout();

        if (openDashboard) {
            goToDashboard();
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
        navigationStarted = true;
        cancelAllPendingCallbacks();

        String cachedIp = prefs.getString(KEY_FEATURE_IP, null);
        String cachedQr = prefs.getString(KEY_FEATURE_QR, null);

        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();

        if (cachedIp != null) editor.putString(KEY_FEATURE_IP, cachedIp);
        if (cachedQr != null) editor.putString(KEY_FEATURE_QR, cachedQr);
        
        editor.apply(); // Changed commit() to apply() as it runs asynchronously and is faster

        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ============================================================
    // SNACKBAR (REPLACED CUSTOM TOAST)
    // ============================================================

    private void showCustomToast(String message, boolean isError) {
        try {
            // Find root view of the activity for Snackbar
            View rootView = findViewById(android.R.id.content);
            if (rootView != null) {
                Snackbar snackbar = Snackbar.make(rootView, message, 
                        isError ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT);
                
                // Set Background Color
                snackbar.getView().setBackgroundColor(isError ? Color.rgb(185, 45, 45) : Color.rgb(35, 35, 35));
                
                // Set Text Color to White
                snackbar.setTextColor(Color.WHITE);
                snackbar.show();
            } else {
                // Fallback to standard Toast if root view is somehow null
                Toast.makeText(this, message, isError ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            // Fallback for safety to prevent crash
            Toast.makeText(this, message, isError ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        }
    }

    // ============================================================
    // CALLBACK CLEANUP
    // ============================================================

    private void cancelNavigationCallback() {
        if (mainHandler != null && navigationRunnable != null) {
            mainHandler.removeCallbacks(navigationRunnable);
            navigationRunnable = null;
        }
    }

    private void cancelStatusTimeout() {
        if (mainHandler != null && statusTimeoutRunnable != null) {
            mainHandler.removeCallbacks(statusTimeoutRunnable);
            statusTimeoutRunnable = null;
        }
    }

    private void cancelFirebaseListener() {
        if (statusRef != null && statusListener != null) {
            try {
                statusRef.removeEventListener(statusListener);
            } catch (Exception e) {
                e.printStackTrace();
            }
            statusRef = null;
            statusListener = null;
        }
    }

    private void cancelAllPendingCallbacks() {
        cancelNavigationCallback();
        cancelStatusTimeout();
        cancelFirebaseListener();
    }

    // ============================================================
    // ACTIVITY STATE
    // ============================================================

    private boolean isActivityDead() {
        if (isFinishing()) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return isDestroyed();
        }
        return false;
    }

    // ============================================================
    // ON DESTROY
    // ============================================================

    @Override
    protected void onDestroy() {
        cancelAllPendingCallbacks();
        super.onDestroy();
    }
}
