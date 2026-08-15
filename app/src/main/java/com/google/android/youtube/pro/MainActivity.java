package com.google.android.youtube.pro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends Activity {

    // ---------------------------------------------------------
    // Constants
    // ---------------------------------------------------------
    private static final String PREF_NAME = "LibraryApp";

    // Splash duration
    private static final long SPLASH_DURATION = 2800L;

    // ---------------------------------------------------------
    // Variables
    // ---------------------------------------------------------
    private SharedPreferences prefs;
    private Handler splashHandler;
    private boolean isFinishingSplash = false;

    // ---------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Premium fullscreen splash appearance
        setupSystemUi();

        setContentView(R.layout.main);

        // Initialize
        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        splashHandler = new Handler(Looper.getMainLooper());

        // Premium UI
        setupPremiumSplash();

        // Start notification service safely
        startNotificationService();

        // Continue to login/dashboard after splash
        splashHandler.postDelayed(() -> {
            if (!isFinishingSplash && !isFinishing()) {
                checkLoginStatus();
            }
        }, SPLASH_DURATION);
    }

    // ---------------------------------------------------------
    // SYSTEM UI
    // ---------------------------------------------------------
    private void setupSystemUi() {

        Window window = getWindow();

        // Edge-to-edge / immersive style
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            // Dark background → light system icons
            window.getDecorView().setSystemUiVisibility(0);
        }

        // Prevent screen from dimming during splash
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
    }

    // ---------------------------------------------------------
    // PREMIUM SPLASH UI
    // ---------------------------------------------------------
    private void setupPremiumSplash() {

        TextView splashTitle = findViewById(R.id.splashTitle);

        // Optional views.
        // XML me ye IDs available hain to animation automatically chalegi.
        TextView splashSubtitle = findViewById(R.id.splashSubtitle);
        TextView splashStatus = findViewById(R.id.splashStatus);

        // -----------------------------------------------------
        // LIBRARY NAME
        // -----------------------------------------------------
        if (splashTitle != null) {

            splashTitle.setText(AppConfig.LIBRARY_NAME);

            // Initial state
            splashTitle.setAlpha(0f);
            splashTitle.setTranslationY(55f);
            splashTitle.setScaleX(0.94f);
            splashTitle.setScaleY(0.94f);

            // Premium entrance animation
            splashTitle.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(1200L)
                    .setStartDelay(180L)
                    .withLayer()
                    .start();
        }

        // -----------------------------------------------------
        // SUBTITLE
        // -----------------------------------------------------
        if (splashSubtitle != null) {

            splashSubtitle.setAlpha(0f);
            splashSubtitle.setTranslationY(25f);

            splashSubtitle.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(900L)
                    .setStartDelay(650L)
                    .start();
        }

        // -----------------------------------------------------
        // STATUS / LOADING TEXT
        // -----------------------------------------------------
        if (splashStatus != null) {

            splashStatus.setAlpha(0f);

            splashStatus.animate()
                    .alpha(1f)
                    .setDuration(700L)
                    .setStartDelay(1100L)
                    .start();

            animateStatusText(splashStatus);
        }
    }

    // ---------------------------------------------------------
    // ANIMATED STATUS TEXT
    // ---------------------------------------------------------
    private void animateStatusText(TextView statusView) {

        final String[] messages = {
                "Initializing library...",
                "Loading account...",
                "Checking access...",
                "Preparing dashboard..."
        };

        Handler handler = new Handler(Looper.getMainLooper());

        for (int i = 0; i < messages.length; i++) {

            final int index = i;

            handler.postDelayed(() -> {

                if (!isFinishingSplash && !isFinishing()) {
                    statusView.animate()
                            .alpha(0f)
                            .setDuration(180L)
                            .withEndAction(() -> {

                                statusView.setText(messages[index]);

                                statusView.animate()
                                        .alpha(1f)
                                        .setDuration(220L)
                                        .start();
                            })
                            .start();
                }

            }, 850L + (i * 500L));
        }
    }

    // ---------------------------------------------------------
    // NOTIFICATION SERVICE
    // ---------------------------------------------------------
    private void startNotificationService() {

        try {

            Intent serviceIntent =
                    new Intent(MainActivity.this, ForegroundService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                startForegroundService(serviceIntent);

            } else {

                startService(serviceIntent);
            }

        } catch (Exception e) {

            // Service failure should NOT stop splash/login flow.
            e.printStackTrace();
        }
    }

    // ---------------------------------------------------------
    // LOGIN STATUS CHECK
    // ---------------------------------------------------------
    private void checkLoginStatus() {

        if (isFinishingSplash) {
            return;
        }

        boolean isLoggedIn =
                prefs.getBoolean("isLoggedIn", false);

        String savedUsername =
                prefs.getString("username", "");

        // -----------------------------------------------------
        // USER NOT LOGGED IN
        // -----------------------------------------------------
        if (!isLoggedIn || savedUsername == null || savedUsername.trim().isEmpty()) {

            goToLogin();
            return;
        }

        // -----------------------------------------------------
        // LIVE FIREBASE APPROVAL CHECK
        // -----------------------------------------------------
        DatabaseReference statusRef =
                FirebaseDatabase
                        .getInstance()
                        .getReference("Students")
                        .child(savedUsername)
                        .child("status");

        statusRef.addListenerForSingleValueEvent(
                new ValueEventListener() {

                    @Override
                    public void onDataChange(
                            @NonNull DataSnapshot snapshot) {

                        if (isFinishingSplash) {
                            return;
                        }

                        if (!snapshot.exists()) {

                            Toast.makeText(
                                    MainActivity.this,
                                    "Account not found. Please contact Admin.",
                                    Toast.LENGTH_LONG
                            ).show();

                            clearDataAndLogout();

                            return;
                        }

                        String currentStatus =
                                snapshot.getValue(String.class);

                        if (currentStatus != null
                                && currentStatus.equalsIgnoreCase("Approved")) {

                            goToDashboard();

                        } else {

                            String statusText =
                                    (currentStatus == null
                                            || currentStatus.trim().isEmpty())
                                            ? "Unavailable"
                                            : currentStatus;

                            Toast.makeText(
                                    MainActivity.this,
                                    "Your account is " + statusText
                                            + ". Please contact Admin.",
                                    Toast.LENGTH_LONG
                            ).show();

                            clearDataAndLogout();
                        }
                    }

                    @Override
                    public void onCancelled(
                            @NonNull DatabaseError error) {

                        if (isFinishingSplash) {
                            return;
                        }

                        /*
                         * Internet/Firebase failure:
                         * Existing behavior retained:
                         * allow previously logged-in user to continue.
                         */
                        goToDashboard();
                    }
                }
        );
    }

    // ---------------------------------------------------------
    // DASHBOARD
    // ---------------------------------------------------------
    private void goToDashboard() {

        if (isFinishingSplash || isFinishing()) {
            return;
        }

        isFinishingSplash = true;

        Intent intent =
                new Intent(
                        MainActivity.this,
                        DashboardActivity.class
                );

        // Prevent splash from remaining in back stack
        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        startActivity(intent);

        // Smooth transition
        overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
        );

        finish();
    }

    // ---------------------------------------------------------
    // LOGIN
    // ---------------------------------------------------------
    private void goToLogin() {

        if (isFinishingSplash || isFinishing()) {
            return;
        }

        isFinishingSplash = true;

        Intent intent =
                new Intent(
                        MainActivity.this,
                        LoginActivity.class
                );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        startActivity(intent);

        overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
        );

        finish();
    }

    // ---------------------------------------------------------
    // LOGOUT / CLEAR DATA
    // ---------------------------------------------------------
    private void clearDataAndLogout() {

        if (prefs != null) {

            prefs.edit()
                    .clear()
                    .apply();
        }

        goToLogin();
    }

    // -------------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------------
    @Override
    protected void onDestroy() {

        if (splashHandler != null) {
            splashHandler.removeCallbacksAndMessages(null);
        }

        super.onDestroy();
    }
}