package com.google.android.youtube.pro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

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

    // New internal key
    private static final String KEY_LAST_SERVER_VERIFICATION =
            "last_server_verification";

    // Splash minimum duration
    private static final long MIN_SPLASH_DURATION_MS = 2500L;

    // Firebase timeout
    private static final long STATUS_CHECK_TIMEOUT_MS = 6000L;

    private SharedPreferences prefs;

    private Handler mainHandler;

    private Runnable navigationRunnable;

    private Runnable statusTimeoutRunnable;

    private DatabaseReference statusRef;

    private ValueEventListener statusListener;

    private boolean navigationStarted = false;

    private boolean statusCheckFinished = false;

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

        prefs = getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
        );

        setupSplash();

        // Existing notification service
        startNotificationService();

        // Fetch AppFeatures in background
        fetchAppFeaturesAndCache();

        /*
         * Internet check immediately start hoga.
         * Navigation minimum 2.5 sec ke baad hi hogi.
         */
        checkRealInternet();
    }


    // ============================================================
    // SPLASH
    // ============================================================

    private void setupSplash() {

        TextView splashTitle = findViewById(R.id.splashTitle);

        if (splashTitle == null) {
            return;
        }

        splashTitle.setText(
                AppConfig.LIBRARY_NAME
        );

        splashTitle.setAlpha(0f);

        splashTitle.setTranslationY(50f);

        splashTitle.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(1500)
                .start();
    }


    // ============================================================
    // NOTIFICATION SERVICE
    // ============================================================

    private void startNotificationService() {

        try {

            Intent serviceIntent =
                    new Intent(
                            this,
                            ForegroundService.class
                    );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                startForegroundService(
                        serviceIntent
                );

            } else {

                startService(
                        serviceIntent
                );
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    // ============================================================
    // APP FEATURES CACHE
    //
    // Existing Firebase structure:
    //
    // AppFeatures
    //     Ip
    //     QrUpload
    //
    // ============================================================

    private void fetchAppFeaturesAndCache() {

        try {

            DatabaseReference featuresRef =
                    FirebaseDatabase
                            .getInstance()
                            .getReference("AppFeatures");

            featuresRef.addListenerForSingleValueEvent(
                    new ValueEventListener() {

                        @Override
                        public void onDataChange(
                                @NonNull DataSnapshot snapshot
                        ) {

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
                                @NonNull DatabaseError error
                        ) {

                            // AppFeatures failure should not block startup.
                            error.toException()
                                    .printStackTrace();
                        }
                    }
            );

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    // ============================================================
    // INTERNET / NETWORK CHECK
    // ============================================================

    private void checkRealInternet() {

        new Thread(
                new Runnable() {

                    @Override
                    public void run() {

                        boolean hasNetwork =
                                false;

                        try {

                            ConnectivityManager cm =
                                    (ConnectivityManager)
                                            getSystemService(
                                                    Context.CONNECTIVITY_SERVICE
                                            );

                            if (cm != null) {

                                if (Build.VERSION.SDK_INT
                                        >= Build.VERSION_CODES.M) {

                                    Network network =
                                            cm.getActiveNetwork();

                                    if (network != null) {

                                        NetworkCapabilities capabilities =
                                                cm.getNetworkCapabilities(
                                                        network
                                                );

                                        if (capabilities != null) {

                                            hasNetwork =
                                                    capabilities.hasCapability(
                                                            NetworkCapabilities
                                                                    .NET_CAPABILITY_INTERNET
                                                    );
                                        }
                                    }

                                } else {

                                    NetworkInfo info =
                                            cm.getActiveNetworkInfo();

                                    hasNetwork =
                                            info != null
                                                    && info.isConnected();
                                }
                            }

                        } catch (Exception e) {

                            e.printStackTrace();

                            hasNetwork = false;
                        }

                        final boolean result =
                                hasNetwork;

                        runOnUiThread(
                                new Runnable() {

                                    @Override
                                    public void run() {

                                        if (isActivityDead()) {
                                            return;
                                        }

                                        checkLoginStatus(
                                                result
                                        );
                                    }
                                }
                        );
                    }
                }
        ).start();
    }


    // ============================================================
    // LOGIN STATUS
    // ============================================================

    private void checkLoginStatus(
            boolean isOnline
    ) {

        if (navigationStarted) {
            return;
        }

        boolean isLoggedIn =
                prefs.getBoolean(
                        KEY_LOGIN,
                        false
                );

        String username =
                prefs.getString(
                        KEY_USERNAME,
                        ""
                );

        username =
                username == null
                        ? ""
                        : username.trim();


        // --------------------------------------------------------
        // NOT LOGGED IN
        // --------------------------------------------------------

        if (!isLoggedIn || username.isEmpty()) {

            navigateAfterSplash(
                    false
            );

            return;
        }


        // --------------------------------------------------------
        // ALREADY LOGGED IN
        // --------------------------------------------------------

        if (!isOnline) {

            /*
             * IMPORTANT:
             *
             * User already logged in hai.
             * Internet nahi hai to LOGOUT NAHI karna.
             *
             * Direct Dashboard.
             */

            showCustomToast(
                    "Offline Mode\nLoading saved data...",
                    false
            );

            navigateAfterSplash(
                    true
            );

            return;
        }


        // --------------------------------------------------------
        // ONLINE -> LIVE FIREBASE STATUS CHECK
        // --------------------------------------------------------

        verifyStudentStatusOnline(
                username
        );
    }


    // ============================================================
    // FIREBASE LIVE STATUS CHECK
    //
    // Existing path preserved:
    //
    // Students
    //     username
    //         status
    //
    // ============================================================

    private void verifyStudentStatusOnline(
            final String username
    ) {

        if (navigationStarted) {
            return;
        }

        statusCheckFinished = false;

        statusRef =
                FirebaseDatabase
                        .getInstance()
                        .getReference("Students")
                        .child(username)
                        .child("status");


        statusListener =
                new ValueEventListener() {

                    @Override
                    public void onDataChange(
                            @NonNull DataSnapshot snapshot
                    ) {

                        if (statusCheckFinished
                                || navigationStarted) {
                            return;
                        }

                        statusCheckFinished = true;

                        cancelStatusTimeout();


                        // Account exists
                        if (snapshot.exists()) {

                            String currentStatus =
                                    snapshot.getValue(
                                            String.class
                                    );


                            // ------------------------------------------------
                            // APPROVED
                            // ------------------------------------------------

                            if (currentStatus != null
                                    && currentStatus.equalsIgnoreCase(
                                    "Approved"
                            )) {

                                /*
                                 * Successful live verification.
                                 */
                                saveLastServerVerification();

                                navigateAfterSplash(
                                        true
                                );

                                return;
                            }


                            // ------------------------------------------------
                            // NOT APPROVED
                            // ------------------------------------------------

                            String statusText =
                                    currentStatus == null
                                            ? "Unknown"
                                            : currentStatus;

                            showCustomToast(
                                    "Your account is "
                                            + statusText
                                            + ". Please contact Admin.",
                                    true
                            );

                            clearDataAndLogout();

                        } else {

                            // ------------------------------------------------
                            // ACCOUNT NOT FOUND
                            // ------------------------------------------------

                            showCustomToast(
                                    "Account not found.\nPlease contact Admin.",
                                    true
                            );

                            clearDataAndLogout();
                        }
                    }


                    @Override
                    public void onCancelled(
                            @NonNull DatabaseError error
                    ) {

                        if (statusCheckFinished
                                || navigationStarted) {
                            return;
                        }

                        statusCheckFinished = true;

                        cancelStatusTimeout();


                        /*
                         * IMPORTANT:
                         *
                         * Firebase error ka matlab zaroori nahi
                         * ki account blocked hai.
                         *
                         * Network/server error mein user already
                         * logged in hai, isliye Dashboard fallback.
                         *
                         * Explicit "Blocked/Pending..." status
                         * onDataChange() mein already handle ho raha hai.
                         */

                        showCustomToast(
                                "Server unavailable\nLoading saved data...",
                                false
                        );

                        navigateAfterSplash(
                                true
                        );
                    }
                };


        statusRef.addListenerForSingleValueEvent(
                statusListener
        );


        // --------------------------------------------------------
        // Firebase timeout
        // --------------------------------------------------------

        statusTimeoutRunnable =
                new Runnable() {

                    @Override
                    public void run() {

                        if (statusCheckFinished
                                || navigationStarted) {
                            return;
                        }

                        statusCheckFinished = true;


                        /*
                         * Request timeout hua.
                         *
                         * User already logged in hai,
                         * so don't logout.
                         */
                        showCustomToast(
                                "Server response slow\nLoading saved data...",
                                false
                        );

                        navigateAfterSplash(
                                true
                        );
                    }
                };


        mainHandler.postDelayed(
                statusTimeoutRunnable,
                STATUS_CHECK_TIMEOUT_MS
        );
    }


    // ============================================================
    // SAVE LAST SUCCESSFUL FIREBASE VERIFICATION
    // ============================================================

    private void saveLastServerVerification() {

        try {

            prefs.edit()
                    .putLong(
                            KEY_LAST_SERVER_VERIFICATION,
                            System.currentTimeMillis()
                    )
                    .apply();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    // ============================================================
    // SPLASH -> NAVIGATION
    // ============================================================

    private void navigateAfterSplash(
            final boolean openDashboard
    ) {

        if (navigationStarted) {
            return;
        }

        long elapsed =
                System.currentTimeMillis()
                        - splashStartTime;

        long remaining =
                MIN_SPLASH_DURATION_MS
                        - elapsed;


        // --------------------------------------------------------
        // Splash time already completed
        // --------------------------------------------------------

        if (remaining <= 0) {

            doNavigation(
                    openDashboard
            );

            return;
        }


        // --------------------------------------------------------
        // Wait remaining splash time
        // --------------------------------------------------------

        if (navigationRunnable != null) {

            mainHandler.removeCallbacks(
                    navigationRunnable
            );
        }


        navigationRunnable =
                new Runnable() {

                    @Override
                    public void run() {

                        if (navigationStarted) {
                            return;
                        }

                        doNavigation(
                                openDashboard
                        );
                    }
                };


        mainHandler.postDelayed(
                navigationRunnable,
                remaining
        );
    }


    // ============================================================
    // ACTUAL NAVIGATION
    // ============================================================

    private void doNavigation(
            boolean openDashboard
    ) {

        if (navigationStarted) {
            return;
        }

        if (isActivityDead()) {
            return;
        }

        navigationStarted = true;

        cancelNavigationCallback();

        cancelStatusTimeout();


        if (openDashboard) {

            goToDashboard();

        } else {

            goToLogin();
        }
    }


    // ============================================================
    // DASHBOARD
    // ============================================================

    private void goToDashboard() {

        Intent intent =
                new Intent(
                        MainActivity.this,
                        DashboardActivity.class
                );

        startActivity(intent);

        finish();
    }


    // ============================================================
    // LOGIN
    // ============================================================

    private void goToLogin() {

        Intent intent =
                new Intent(
                        MainActivity.this,
                        LoginActivity.class
                );

        startActivity(intent);

        finish();
    }


    // ============================================================
    // FULL LOGOUT
    //
    // Existing global AppFeatures cache is preserved.
    // ============================================================

    private void clearDataAndLogout() {

        navigationStarted = true;

        cancelAllPendingCallbacks();


        /*
         * Save global AppFeatures BEFORE clearing preferences.
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


        SharedPreferences.Editor editor =
                prefs.edit();

        /*
         * Old session + old account data remove.
         */
        editor.clear();


        /*
         * Restore global AppFeatures cache.
         */
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


        editor.commit();


        /*
         * Complete Activity stack clear.
         * Login will start as fresh task.
         */
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


    // ============================================================
    // CUSTOM TOAST
    // ============================================================

    private void showCustomToast(
            String message,
            boolean isError
    ) {

        try {

            LinearLayout layout =
                    new LinearLayout(this);

            layout.setOrientation(
                    LinearLayout.VERTICAL
            );

            layout.setGravity(
                    Gravity.CENTER
            );

            layout.setPadding(
                    28,
                    18,
                    28,
                    18
            );


            GradientDrawable background =
                    new GradientDrawable();

            background.setCornerRadius(
                    45f
            );


            if (isError) {

                background.setColor(
                        Color.rgb(
                                185,
                                45,
                                45
                        )
                );

            } else {

                background.setColor(
                        Color.rgb(
                                35,
                                35,
                                35
                        )
                );
            }


            layout.setBackground(
                    background
            );


            TextView textView =
                    new TextView(this);

            textView.setText(
                    message
            );

            textView.setTextColor(
                    Color.WHITE
            );

            textView.setTextSize(
                    14
            );

            textView.setGravity(
                    Gravity.CENTER
            );

            textView.setMaxLines(
                    3
            );


            layout.addView(
                    textView,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );


            Toast toast =
                    new Toast(
                            getApplicationContext()
                    );

            toast.setDuration(
                    isError
                            ? Toast.LENGTH_LONG
                            : Toast.LENGTH_SHORT
            );

            toast.setGravity(
                    Gravity.BOTTOM
                            | Gravity.CENTER_HORIZONTAL,
                    0,
                    140
            );

            toast.setView(
                    layout
            );

            toast.show();

        } catch (Exception e) {

            /*
             * Fallback to normal Toast so that a UI
             * exception never crashes startup.
             */
            Toast.makeText(
                    this,
                    message,
                    isError
                            ? Toast.LENGTH_LONG
                            : Toast.LENGTH_SHORT
            ).show();
        }
    }


    // ============================================================
    // CALLBACK CLEANUP
    // ============================================================

    private void cancelNavigationCallback() {

        if (mainHandler != null
                && navigationRunnable != null) {

            mainHandler.removeCallbacks(
                    navigationRunnable
            );

            navigationRunnable = null;
        }
    }


    private void cancelStatusTimeout() {

        if (mainHandler != null
                && statusTimeoutRunnable != null) {

            mainHandler.removeCallbacks(
                    statusTimeoutRunnable
            );

            statusTimeoutRunnable = null;
        }
    }


    private void cancelFirebaseListener() {

        if (statusRef != null
                && statusListener != null) {

            try {

                statusRef.removeEventListener(
                        statusListener
                );

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

        if (isFinishing()) {
            return true;
        }

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