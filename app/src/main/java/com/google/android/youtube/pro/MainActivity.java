package com.google.android.youtube.pro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
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

public class MainActivity extends Activity {

    private static final String PREF_NAME = "LibraryApp";

    // Existing keys - same structure
    private static final String KEY_LOGIN = "isLoggedIn";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_FEATURE_IP = "feature_ip";
    private static final String KEY_FEATURE_QR = "feature_qr_upload";

    // New key - same SharedPreferences file
    private static final String KEY_LAST_SERVER_VERIFICATION =
            "last_server_verification";

    // Offline access allowed for maximum 3 days after last successful
    // Firebase "Approved" verification.
    private static final long OFFLINE_GRACE_PERIOD_MS =
            3L * 24L * 60L * 60L * 1000L;

    // Firebase status request timeout
    private static final long STATUS_CHECK_TIMEOUT_MS = 6000L;

    // Minimum splash duration
    private static final long MIN_SPLASH_DURATION_MS = 2500L;

    private SharedPreferences prefs;

    private Handler splashHandler;
    private Runnable splashRunnable;

    private Handler timeoutHandler;
    private Runnable statusTimeoutRunnable;

    private long splashStartTime;

    // Prevent multiple navigation calls
    private boolean navigationStarted = false;

    // Prevent duplicate status verification callbacks
    private boolean statusCheckFinished = false;

    private DatabaseReference currentStatusRef;
    private ValueEventListener currentStatusListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        splashStartTime = System.currentTimeMillis();

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        setupSplashAnimation();

        // Existing notification service
        startNotificationService();

        // Background feature cache update
        fetchAppFeaturesAndCache();

        /*
         * IMPORTANT:
         * Pehle 2.5 sec wait karke internet check nahi karenge.
         * Internet/session checking immediately start hogi.
         * Navigation minimum 2.5 sec splash ke baad hi hogi.
         */
        splashHandler = new Handler(Looper.getMainLooper());

        checkRealInternet();
    }

    private void setupSplashAnimation() {
        TextView splashTitle = findViewById(R.id.splashTitle);

        if (splashTitle != null) {
            splashTitle.setText(AppConfig.LIBRARY_NAME);

            splashTitle.setAlpha(0f);
            splashTitle.setTranslationY(50f);

            splashTitle.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(1500)
                    .start();
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

    /**
     * Existing AppFeatures structure preserved:
     *
     * AppFeatures
     *   Ip
     *   QrUpload
     */
    private void fetchAppFeaturesAndCache() {

        try {

            DatabaseReference featuresRef =
                    FirebaseDatabase.getInstance()
                            .getReference("AppFeatures");

            featuresRef.addListenerForSingleValueEvent(
                    new ValueEventListener() {

                        @Override
                        public void onDataChange(
                                @NonNull DataSnapshot snapshot) {

                            if (!snapshot.exists()) {
                                return;
                            }

                            String ipAuth =
                                    snapshot.child("Ip")
                                            .getValue(String.class);

                            String qrUpload =
                                    snapshot.child("QrUpload")
                                            .getValue(String.class);

                            SharedPreferences.Editor editor =
                                    prefs.edit();

                            if (ipAuth != null) {
                                editor.putString(
                                        KEY_FEATURE_IP,
                                        ipAuth
                                );
                            }

                            if (qrUpload != null) {
                                editor.putString(
                                        KEY_FEATURE_QR,
                                        qrUpload
                                );
                            }

                            editor.apply();
                        }

                        @Override
                        public void onCancelled(
                                @NonNull DatabaseError error) {

                            // Important:
                            // AppFeatures failure should NOT block app startup.
                            error.toException().printStackTrace();
                        }
                    }
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Android network status check.
     *
     * Old implementation used:
     * 8.8.8.8:53 socket
     *
     * That could produce false Offline results.
     */
    private void checkRealInternet() {

        new Thread(new Runnable() {

            @Override
            public void run() {

                boolean hasInternet = false;

                try {

                    ConnectivityManager connectivityManager =
                            (ConnectivityManager)
                                    getSystemService(
                                            Context.CONNECTIVITY_SERVICE
                                    );

                    if (connectivityManager != null) {

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                            Network network =
                                    connectivityManager
                                            .getActiveNetwork();

                            if (network != null) {

                                NetworkCapabilities capabilities =
                                        connectivityManager
                                                .getNetworkCapabilities(
                                                        network
                                                );

                                if (capabilities != null) {

                                    boolean hasInternetCapability =
                                            capabilities.hasCapability(
                                                    NetworkCapabilities
                                                            .NET_CAPABILITY_INTERNET
                                            );

                                    boolean isValidated =
                                            capabilities.hasCapability(
                                                    NetworkCapabilities
                                                            .NET_CAPABILITY_VALIDATED
                                            );

                                    /*
                                     * VALIDATED is the strongest signal.
                                     *
                                     * INTERNET is also accepted in case
                                     * validation is temporarily unavailable.
                                     */
                                    hasInternet =
                                            hasInternetCapability
                                                    && (
                                                    isValidated
                                                            || capabilities
                                                            .hasTransport(
                                                                    NetworkCapabilities
                                                                            .TRANSPORT_WIFI
                                                            )
                                                            || capabilities
                                                            .hasTransport(
                                                                    NetworkCapabilities
                                                                            .TRANSPORT_CELLULAR
                                                            )
                                            );
                                }
                            }

                        } else {

                            // Compatibility for old Android
                            NetworkInfo networkInfo =
                                    connectivityManager
                                            .getActiveNetworkInfo();

                            hasInternet =
                                    networkInfo != null
                                            && networkInfo.isConnected();
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    hasInternet = false;
                }

                final boolean finalHasInternet = hasInternet;

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        if (isFinishing()
                                || (Build.VERSION.SDK_INT >= 17
                                && isDestroyed())) {
                            return;
                        }

                        checkLoginStatus(finalHasInternet);
                    }
                });

            }

        }).start();
    }

    private void checkLoginStatus(boolean isOnline) {

        if (navigationStarted) {
            return;
        }

        boolean isLoggedIn =
                prefs.getBoolean(KEY_LOGIN, false);

        String savedUsername =
                prefs.getString(KEY_USERNAME, "");

        if (!isLoggedIn || savedUsername.trim().isEmpty()) {

            navigateAfterSplash(false, null);

            return;
        }

        savedUsername = savedUsername.trim();

        if (isOnline) {

            verifyStudentStatusOnline(savedUsername);

        } else {

            /*
             * No internet:
             * use controlled offline verification.
             */
            handleOfflineAccess();
        }
    }

    /**
     * Live Firebase account verification.
     *
     * Existing path preserved:
     *
     * Students
     *   username
     *      status
     */
    private void verifyStudentStatusOnline(final String username) {

        statusCheckFinished = false;

        currentStatusRef =
                FirebaseDatabase.getInstance()
                        .getReference("Students")
                        .child(username)
                        .child("status");

        currentStatusListener =
                new ValueEventListener() {

                    @Override
                    public void onDataChange(
                            @NonNull DataSnapshot snapshot) {

                        if (statusCheckFinished) {
                            return;
                        }

                        statusCheckFinished = true;
                        cancelStatusTimeout();

                        if (snapshot.exists()) {

                            String currentStatus =
                                    snapshot.getValue(String.class);

                            if (currentStatus != null
                                    && currentStatus.equalsIgnoreCase(
                                    "Approved"
                            )) {

                                /*
                                 * Successful server verification.
                                 * Store current timestamp for offline mode.
                                 */
                                saveLastServerVerification();

                                navigateAfterSplash(
                                        true,
                                        null
                                );

                            } else {

                                String statusText =
                                        currentStatus == null
                                                ? "Unknown"
                                                : currentStatus;

                                showToast(
                                        "Your account is "
                                                + statusText
                                                + ". Please contact Admin.",
                                        Toast.LENGTH_LONG
                                );

                                clearDataAndLogout();
                            }

                        } else {

                            showToast(
                                    "Account not found. Please contact Admin.",
                                    Toast.LENGTH_LONG
                            );

                            clearDataAndLogout();
                        }
                    }

                    @Override
                    public void onCancelled(
                            @NonNull DatabaseError error) {

                        if (statusCheckFinished) {
                            return;
                        }

                        statusCheckFinished = true;
                        cancelStatusTimeout();

                        handleFirebaseStatusError(error);
                    }
                };

        currentStatusRef.addListenerForSingleValueEvent(
                currentStatusListener
        );

        /*
         * If Firebase request takes unusually long,
         * don't keep the splash stuck forever.
         */
        timeoutHandler = new Handler(Looper.getMainLooper());

        statusTimeoutRunnable = new Runnable() {

            @Override
            public void run() {

                if (statusCheckFinished) {
                    return;
                }

                statusCheckFinished = true;

                /*
                 * We don't need to manually remove the single-value
                 * listener in normal use; timeout is only our UI fallback.
                 */
                handleStatusCheckTimeout();
            }
        };

        timeoutHandler.postDelayed(
                statusTimeoutRunnable,
                STATUS_CHECK_TIMEOUT_MS
        );
    }

    private void handleFirebaseStatusError(DatabaseError error) {

        int errorCode = error.getCode();

        /*
         * Permission denied is an account/database permission problem,
         * NOT simply "offline".
         */
        if (errorCode == DatabaseError.PERMISSION_DENIED) {

            showToast(
                    "Account verification failed. Please contact Admin.",
                    Toast.LENGTH_LONG
            );

            clearDataAndLogout();

            return;
        }

        /*
         * Temporary/network related Firebase failure:
         * try controlled offline access.
         */
        if (errorCode == DatabaseError.NETWORK_ERROR
                || errorCode == DatabaseError.DISCONNECTED) {

            handleOfflineAccess();
            return;
        }

        /*
         * Unknown temporary Firebase problem.
         *
         * If a valid offline verification exists, allow offline mode.
         * Otherwise ask the user to login/verify online.
         */
        if (isOfflineVerificationValid()) {

            showToast(
                    "Server unavailable. Loading saved data...",
                    Toast.LENGTH_SHORT
            );

            navigateAfterSplash(
                    true,
                    null
            );

        } else {

            showToast(
                    "Unable to verify account. Internet connection required.",
                    Toast.LENGTH_LONG
            );

            forceSessionLogout();
        }
    }

    private void handleStatusCheckTimeout() {

        /*
         * Firebase verification took too long.
         *
         * Do NOT blindly open Dashboard.
         * Only allow it if a recent successful verification exists.
         */
        if (isOfflineVerificationValid()) {

            showToast(
                    "Server is taking too long. Loading saved data...",
                    Toast.LENGTH_SHORT
            );

            navigateAfterSplash(
                    true,
                    null
            );

        } else {

            showToast(
                    "Account verification timed out. Internet connection required.",
                    Toast.LENGTH_LONG
            );

            forceSessionLogout();
        }
    }

    /**
     * Controlled offline access.
     *
     * Old behaviour:
     *     Offline -> Dashboard always
     *
     * New behaviour:
     *     Offline + last Approved verification <= 3 days
     *          -> Dashboard
     *
     *     Offline + no verification / older than 3 days
     *          -> Login
     */
    private void handleOfflineAccess() {

        if (isOfflineVerificationValid()) {

            showToast(
                    "You are Offline. Loading saved data...",
                    Toast.LENGTH_SHORT
            );

            navigateAfterSplash(
                    true,
                    null
            );

        } else {

            showToast(
                    "Internet required. Please connect to verify your account.",
                    Toast.LENGTH_LONG
            );

            forceSessionLogout();
        }
    }

    /**
     * Save last successful Firebase Approved verification.
     */
    private void saveLastServerVerification() {

        prefs.edit()
                .putLong(
                        KEY_LAST_SERVER_VERIFICATION,
                        System.currentTimeMillis()
                )
                .apply();
    }

    /**
     * Check if the cached server verification is still usable.
     */
    private boolean isOfflineVerificationValid() {

        long lastVerified =
                prefs.getLong(
                        KEY_LAST_SERVER_VERIFICATION,
                        0L
                );

        if (lastVerified <= 0L) {
            return false;
        }

        long currentTime =
                System.currentTimeMillis();

        long elapsed =
                currentTime - lastVerified;

        /*
         * Clock moved backwards:
         * don't unnecessarily reject session.
         */
        if (elapsed < 0L) {
            return true;
        }

        return elapsed <= OFFLINE_GRACE_PERIOD_MS;
    }

    /**
     * Navigation is always held until minimum splash duration completes.
     */
    private void navigateAfterSplash(
            boolean dashboard,
            String ignored
    ) {

        if (navigationStarted) {
            return;
        }

        long elapsed =
                System.currentTimeMillis()
                        - splashStartTime;

        long remaining =
                MIN_SPLASH_DURATION_MS - elapsed;

        if (remaining <= 0L) {

            doNavigation(dashboard);

            return;
        }

        if (splashHandler == null) {
            splashHandler =
                    new Handler(Looper.getMainLooper());
        }

        if (splashRunnable != null) {
            splashHandler.removeCallbacks(
                    splashRunnable
            );
        }

        final boolean openDashboard = dashboard;

        splashRunnable = new Runnable() {

            @Override
            public void run() {

                if (navigationStarted) {
                    return;
                }

                doNavigation(openDashboard);
            }
        };

        splashHandler.postDelayed(
                splashRunnable,
                remaining
        );
    }

    private void doNavigation(boolean dashboard) {

        if (navigationStarted) {
            return;
        }

        if (isFinishing()
                || (Build.VERSION.SDK_INT >= 17
                && isDestroyed())) {
            return;
        }

        navigationStarted = true;

        if (splashHandler != null
                && splashRunnable != null) {

            splashHandler.removeCallbacks(
                    splashRunnable
            );
        }

        if (dashboard) {
            goToDashboard();
        } else {
            goToLogin();
        }
    }

    private void goToDashboard() {

        Intent intent =
                new Intent(
                        MainActivity.this,
                        DashboardActivity.class
                );

        startActivity(intent);
        finish();
    }

    private void goToLogin() {

        Intent intent =
                new Intent(
                        MainActivity.this,
                        LoginActivity.class
                );

        startActivity(intent);
        finish();
    }

    /**
     * Used when server explicitly rejects/deletes the account.
     *
     * Existing app behaviour preserved:
     * clear the complete preference file.
     */
    private void clearDataAndLogout() {

        navigationStarted = true;

        cancelAllPendingCallbacks();

        SharedPreferences.Editor editor =
                prefs.edit();

        editor.clear();

        /*
         * Keep AppFeatures cache, so next startup does not necessarily
         * need fresh feature data just to open the app.
         *
         * We are intentionally restoring the two existing global cache keys.
         */
        String cachedIp =
                prefs.getString(
                        KEY_FEATURE_IP,
                        null
                );

        String cachedQr =
                prefs.getString(
                        KEY_FEATURE_QR,
                        null
                );

        /*
         * Because clear() has been called, old values were captured above.
         */
        editor.clear();

        if (cachedIp != null) {
            editor.putString(
                    KEY_FEATURE_IP,
                    cachedIp
            );
        }

        if (cachedQr != null) {
            editor.putString(
                    KEY_FEATURE_QR,
                    cachedQr
            );
        }

        editor.apply();

        goToLoginWithClearTask();
    }

    /**
     * Offline verification expired.
     *
     * Don't destroy global AppFeatures.
     * Only remove login/session information.
     */
    private void forceSessionLogout() {

        navigationStarted = true;

        cancelAllPendingCallbacks();

        prefs.edit()
                .remove(KEY_LOGIN)
                .remove(KEY_USERNAME)
                .remove(KEY_LAST_SERVER_VERIFICATION)
                .apply();

        goToLoginWithClearTask();
    }

    private void goToLoginWithClearTask() {

        Intent intent =
                new Intent(
                        MainActivity.this,
                        LoginActivity.class
                );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }

    private void cancelStatusTimeout() {

        if (timeoutHandler != null
                && statusTimeoutRunnable != null) {

            timeoutHandler.removeCallbacks(
                    statusTimeoutRunnable
            );
        }

        timeoutHandler = null;
        statusTimeoutRunnable = null;
    }

    private void cancelAllPendingCallbacks() {

        if (splashHandler != null
                && splashRunnable != null) {

            splashHandler.removeCallbacks(
                    splashRunnable
            );
        }

        cancelStatusTimeout();

        if (currentStatusRef != null
                && currentStatusListener != null) {

            try {
                currentStatusRef.removeEventListener(
                        currentStatusListener
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void showToast(
            String message,
            int duration
    ) {

        if (isFinishing()) {
            return;
        }

        Toast.makeText(
                MainActivity.this,
                message,
                duration
        ).show();
    }

    @Override
    protected void onDestroy() {

        cancelAllPendingCallbacks();

        super.onDestroy();
    }
}