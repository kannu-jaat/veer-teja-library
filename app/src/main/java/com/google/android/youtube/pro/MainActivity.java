package com.google.android.youtube.pro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

    private static final String PREF_NAME = "LibraryApp";
    private static final long SPLASH_DURATION = 2800L;

    private SharedPreferences prefs;
    private Handler splashHandler;
    private boolean isFinishingSplash = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupSystemUi();

        setContentView(R.layout.main);

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        splashHandler = new Handler(Looper.getMainLooper());

        setupPremiumSplash();

        startNotificationService();

        splashHandler.postDelayed(() -> {
            if (!isFinishingSplash && !isFinishing()) {
                checkLoginStatus();
            }
        }, SPLASH_DURATION);
    }

    private void setupSystemUi() {

        Window window = getWindow();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Dark splash background ke liye light status/navigation icons
            window.getDecorView().setSystemUiVisibility(0);
        }

        window.addFlags(
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        );
    }

    private void setupPremiumSplash() {

        TextView splashTitle = findViewById(R.id.splashTitle);

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
    }

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
            e.printStackTrace();
        }
    }

    private void checkLoginStatus() {

        if (isFinishingSplash) {
            return;
        }

        boolean isLoggedIn =
                prefs.getBoolean("isLoggedIn", false);

        String savedUsername =
                prefs.getString("username", "");

        if (!isLoggedIn
                || savedUsername == null
                || savedUsername.trim().isEmpty()) {

            goToLogin();
            return;
        }

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
                                    "Your account is "
                                            + statusText
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

                        // Existing behavior retained:
                        // Firebase unavailable -> allow cached login
                        goToDashboard();
                    }
                }
        );
    }

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

    private void clearDataAndLogout() {

        if (prefs != null) {
            prefs.edit()
                    .clear()
                    .apply();
        }

        goToLogin();
    }

    @Override
    protected void onDestroy() {

        if (splashHandler != null) {
            splashHandler.removeCallbacksAndMessages(null);
        }

        super.onDestroy();
    }
}