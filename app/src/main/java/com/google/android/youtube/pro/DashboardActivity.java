package com.google.android.youtube.pro;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DashboardActivity extends FragmentActivity {

    private TextView tvGreeting, tvDashName, tvSeatNumber, tvMembershipType, tvValidity, tvInternetWarning;
    private TextView tvTodayStatus, tvAttDate, tvAttTime, tvDaysPresent;
    private ImageView ivHeaderAvatar, ivStatusAvatar, btnNotifications;

    private LinearLayout btnMarkAttendGrid, btnMyAttendanceGrid, btnMySeatGrid, btnFeesGrid;
    private LinearLayout btnNoticesGrid, btnRulesGrid, btnProfileGrid, btnSupportGrid;
    private CardView cvLatestNotice;

    private LinearLayout btnDashboardNav, btnMyAttendanceNav, btnPaymentsNav, btnMoreNav;
    private ImageView ivDashboardIcon, ivAttendIcon, ivPaymentsIcon, ivMoreIcon;
    private TextView tvDashboardText, tvAttendText, tvPaymentsText, tvMoreText;
    private CardView btnCenterCameraFab;

    private LinearLayout dashboardBottomContent;
    private View fragmentContainer; 

    private SharedPreferences prefs;
    private String savedUsername;
    private ConnectivityManager.NetworkCallback networkCallback;
    private ConnectivityManager cm;

    private long lastClickTime = 0;
    private static final int STATE_DASHBOARD = 0;
    private static final int STATE_ATTENDANCE = 1;
    private static final int STATE_PAYMENTS = 2;
    private static final int STATE_MORE = 3;
    private static final int STATE_RULES = 4;
    private int currentState = STATE_DASHBOARD;
    private boolean isAttendanceMarkedToday = false; 

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (result.getOrDefault(Manifest.permission.CAMERA, false)) {
                    launchQRScanner();
                } else {
                    showCustomToast("Camera permission required!", false);
                }
            });

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(), result -> {
        if(result.getContents() != null) {
            verifyQrAndMarkAttendance(result.getContents());
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dashboard);

        initializeViews();
        setupSharedPreferences();
        setupRealtimeInternetCheck();

        if (savedUsername.isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setupUIInfo();
        setupClickListeners();
    }

    private void initializeViews() {
        tvInternetWarning = findViewById(R.id.tvInternetWarning);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvDashName = findViewById(R.id.tvDashName);
        ivHeaderAvatar = findViewById(R.id.ivHeaderAvatar);
        btnNotifications = findViewById(R.id.btnNotifications);

        tvSeatNumber = findViewById(R.id.tvSeatNumber);
        tvMembershipType = findViewById(R.id.tvMembershipType);
        tvValidity = findViewById(R.id.tvValidity);
        tvDaysPresent = findViewById(R.id.tvDaysPresent);
        tvTodayStatus = findViewById(R.id.tvTodayStatus);
        tvAttDate = findViewById(R.id.tvAttDate);
        tvAttTime = findViewById(R.id.tvAttTime);
        ivStatusAvatar = findViewById(R.id.ivStatusAvatar);
        cvLatestNotice = findViewById(R.id.cvLatestNotice);

        btnMarkAttendGrid = findViewById(R.id.btnMarkAttendGrid);
        btnMyAttendanceGrid = findViewById(R.id.btnMyAttendanceGrid);
        btnMySeatGrid = findViewById(R.id.btnMySeatGrid);
        btnFeesGrid = findViewById(R.id.btnFeesGrid);
        btnNoticesGrid = findViewById(R.id.btnNoticesGrid);
        btnRulesGrid = findViewById(R.id.btnRulesGrid);
        btnProfileGrid = findViewById(R.id.btnProfileGrid);
        btnSupportGrid = findViewById(R.id.btnSupportGrid);

        btnDashboardNav = findViewById(R.id.btnDashboardNav);
        btnMyAttendanceNav = findViewById(R.id.btnMyAttendanceNav);
        btnPaymentsNav = findViewById(R.id.btnPaymentsNav);
        btnMoreNav = findViewById(R.id.btnMoreNav);
        btnCenterCameraFab = findViewById(R.id.btnCenterCameraFab);

        ivDashboardIcon = findViewById(R.id.ivDashboardIcon);
        tvDashboardText = findViewById(R.id.tvDashboardText);
        ivAttendIcon = findViewById(R.id.ivAttendIcon);
        tvAttendText = findViewById(R.id.tvAttendText);
        ivPaymentsIcon = findViewById(R.id.ivPaymentsIcon);
        tvPaymentsText = findViewById(R.id.tvPaymentsText);
        ivMoreIcon = findViewById(R.id.ivMoreIcon);
        tvMoreText = findViewById(R.id.tvMoreText);

        dashboardBottomContent = findViewById(R.id.dashboard_bottom_content);
        fragmentContainer = findViewById(R.id.fragment_container);
    }

    private void setupSharedPreferences() {
        prefs = getSharedPreferences("LibraryApp", Context.MODE_PRIVATE);
        savedUsername = prefs.getString("username", "");
    }

    private void setupUIInfo() {
        setDynamicGreeting();
        tvDashName.setText(prefs.getString("cachedName", "Student"));
        loadCachedProfileImage();
        fetchProfileDataFromFirebase();
        calculateMonthlyAttendance();
        checkTodayAttendance();
    }

    private void setupClickListeners() {
        btnDashboardNav.setOnClickListener(v -> handleNavigation(STATE_DASHBOARD));
        btnMyAttendanceGrid.setOnClickListener(v -> handleNavigation(STATE_ATTENDANCE));
        btnMyAttendanceNav.setOnClickListener(v -> handleNavigation(STATE_ATTENDANCE));
        btnFeesGrid.setOnClickListener(v -> handleNavigation(STATE_PAYMENTS));
        btnPaymentsNav.setOnClickListener(v -> handleNavigation(STATE_PAYMENTS));
        btnMoreNav.setOnClickListener(v -> handleNavigation(STATE_MORE));
        btnRulesGrid.setOnClickListener(v -> handleNavigation(STATE_RULES));

        // 🔥 Profile Clicks
        btnProfileGrid.setOnClickListener(v -> openProfileWithAnimation());
        if (ivHeaderAvatar != null) ivHeaderAvatar.setOnClickListener(v -> openProfileWithAnimation());

        btnSupportGrid.setOnClickListener(v -> {
            if (isSpamClick()) return;
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + AppConfig.CONTACT_NUMBER));
            startActivity(intent);
        });

        btnMarkAttendGrid.setOnClickListener(v -> checkPermissionsAndScan());
        btnCenterCameraFab.setOnClickListener(v -> checkPermissionsAndScan());

        View.OnClickListener comingSoonListener = v -> {
            if (!isSpamClick()) showCustomToast("Feature coming soon!", false);
        };

        btnMySeatGrid.setOnClickListener(comingSoonListener);
        btnNoticesGrid.setOnClickListener(comingSoonListener);
        btnNotifications.setOnClickListener(comingSoonListener);
        cvLatestNotice.setOnClickListener(comingSoonListener);
    }

    private void openProfileWithAnimation() {
        if (isSpamClick()) return;
        Intent intent = new Intent(DashboardActivity.this, ProfileActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_bottom_right, 0);
    }

    public void showCustomToast(String message, boolean isSuccess) {
        Toast toast = new Toast(this);
        toast.setDuration(Toast.LENGTH_LONG);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(40, 24, 40, 24);
        layout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(isSuccess ? Color.parseColor("#065F46") : Color.parseColor("#991B1B"));
        gd.setCornerRadius(50f);
        layout.setBackground(gd);
        TextView tv = new TextView(this);
        tv.setText((isSuccess ? "✅ " : "⚠️ ") + message);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(tv);
        toast.setView(layout);
        toast.show();
    }

    private void checkPermissionsAndScan() {
        if (isSpamClick()) return;
        if (isAttendanceMarkedToday) {
            showCustomToast("Attendance already marked!", true);
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchQRScanner();
        } else {
            requestPermissionLauncher.launch(new String[]{ Manifest.permission.CAMERA });
        }
    }

    private void launchQRScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan Library QR");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setCaptureActivity(PortraitCaptureActivity.class); 
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        barcodeLauncher.launch(options);
    }

    private void verifyQrAndMarkAttendance(String scannedData) {
        showCustomToast("Verifying securely...", true);
        boolean isIpAuthRequired = "yes".equalsIgnoreCase(prefs.getString("feature_ip", "no"));

        if (isIpAuthRequired) {
            new Thread(() -> {
                try {
                    URL url = new URL("https://api.ipify.org");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    java.util.Scanner scanner = new java.util.Scanner(connection.getInputStream());
                    String myPublicIP = scanner.useDelimiter("\\A").hasNext() ? scanner.next().trim() : "";
                    scanner.close();
                    new Handler(Looper.getMainLooper()).post(() -> verifyQrWithIP(scannedData, myPublicIP));
                } catch (Exception e) {
                    new Handler(Looper.getMainLooper()).post(() -> showCustomToast("Network Error!", false));
                }
            }).start();
        } else {
            verifyQrWithIP(scannedData, null); 
        }
    }

    private void verifyQrWithIP(String scannedData, String studentIP) {
        String extractedHash = "";
        try {
            org.json.JSONObject qrJson = new org.json.JSONObject(scannedData);
            extractedHash = qrJson.getString("hash");
        } catch (Exception e) {
            showCustomToast("Invalid QR Format!", false);
            return;
        }
        final String finalScannedHash = extractedHash;
        DatabaseReference studentRef = FirebaseDatabase.getInstance().getReference("Students").child(savedUsername);
        studentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot studentSnap) {
                boolean isAdmin = studentSnap.hasChild("Admin") && "true".equalsIgnoreCase(String.valueOf(studentSnap.child("Admin").getValue()));
                DatabaseReference qrRef = FirebaseDatabase.getInstance().getReference("QRConfig/current");
                qrRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot qrSnap) {
                        if(qrSnap.exists()) {
                            String dbHash = qrSnap.child("hash").getValue(String.class);
                            String dbAllowedIP = qrSnap.child("allowedIP").getValue(String.class);
                            if (dbHash != null && finalScannedHash.equals(dbHash)) {
                                if (isAdmin || studentIP == null || studentIP.equals((dbAllowedIP != null ? dbAllowedIP.trim() : ""))) {
                                    markAttendanceInDatabase();
                                } else {
                                    showCustomToast("Proxy Blocked!", false);
                                }
                            } else {
                                showCustomToast("Invalid/Old QR Code!", false);
                            }
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void markAttendanceInDatabase() {
        FirebaseDatabase.getInstance().getReference(".info/serverTimeOffset").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot offsetSnapshot) {
                long offset = offsetSnapshot.exists() ? offsetSnapshot.getValue(Long.class) : 0;
                String dateString = new SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(new Date(System.currentTimeMillis() + offset));
                String timeString = new SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(new Date(System.currentTimeMillis() + offset));

                FirebaseDatabase.getInstance().getReference("Attendance").child(savedUsername).child(dateString).child("checkIn").setValue(timeString).addOnCompleteListener(task -> {
                    if(task.isSuccessful()){
                        showCustomToast("Attendance Marked!", true);
                        checkTodayAttendance(); 
                        calculateMonthlyAttendance(); 
                    }
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private boolean isSpamClick() {
        if (System.currentTimeMillis() - lastClickTime < 600) return true;
        lastClickTime = System.currentTimeMillis();
        return false;
    }

    private void handleNavigation(int targetState) {
        if (isSpamClick() || currentState == targetState) return;
        currentState = targetState;
        updateBottomNavColors(targetState);
        if (targetState == STATE_DASHBOARD) closeFragmentWithAnimation();
        else if (targetState == STATE_ATTENDANCE) openFragmentWithAnimation(new AttendanceFragment());
        else if (targetState == STATE_PAYMENTS) openFragmentWithAnimation(new FeesFragment());
        else if (targetState == STATE_RULES) openFragmentWithAnimation(new RulesFragment());
    }

    private void updateBottomNavColors(int activeState) {
        int gray = Color.parseColor("#94A3B8");
        int gold = Color.parseColor("#FBBF24");
        ivDashboardIcon.setColorFilter(activeState == STATE_DASHBOARD ? gold : gray);
        ivAttendIcon.setColorFilter(activeState == STATE_ATTENDANCE ? gold : gray);
        ivPaymentsIcon.setColorFilter(activeState == STATE_PAYMENTS ? gold : gray);
        ivMoreIcon.setColorFilter(activeState == STATE_MORE ? gold : gray);
    }

    private void openFragmentWithAnimation(Fragment fragment) {
        dashboardBottomContent.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
    }

    public void closeFragmentWithAnimation() {
        currentState = STATE_DASHBOARD;
        updateBottomNavColors(STATE_DASHBOARD);
        fragmentContainer.setVisibility(View.GONE);
        dashboardBottomContent.setVisibility(View.VISIBLE);
    }

    private void setDynamicGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        tvGreeting.setText(h < 12 ? "Good Morning," : (h < 16 ? "Good Afternoon," : "Good Evening,"));
    }

    private void loadCachedProfileImage() {
        File f = new File(getFilesDir(), "profile_avatar.jpg");
        if (f.exists()) ivHeaderAvatar.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath()));
    }

    private void fetchProfileDataFromFirebase() {
        FirebaseDatabase.getInstance().getReference("Students").child(savedUsername).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot s) {
                if (s.exists()) {
                    tvDashName.setText(s.child("fullName").getValue(String.class));
                    tvSeatNumber.setText(s.child("seatNumber").getValue(String.class));
                    tvMembershipType.setText(s.child("membership").getValue(String.class));
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void calculateMonthlyAttendance() {
        String mY = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(new Date());
        FirebaseDatabase.getInstance().getReference("Attendance").child(savedUsername).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot s) {
                int p = 0;
                for (DataSnapshot d : s.getChildren()) if (d.getKey() != null && d.getKey().endsWith(mY)) p++;
                if (tvDaysPresent != null) tvDaysPresent.setText(String.valueOf(p));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void checkTodayAttendance() {
        String dS = new SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(new Date());
        if (tvAttDate != null) tvAttDate.setText(dS);
        FirebaseDatabase.getInstance().getReference("Attendance").child(savedUsername).child(dS).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot s) {
                if (s.exists()) {
                    isAttendanceMarkedToday = true;
                    tvTodayStatus.setText("Marked ✓");
                    tvTodayStatus.setTextColor(Color.parseColor("#10B981"));
                    if (tvAttTime != null) tvAttTime.setText(s.child("checkIn").getValue(String.class));
                } else {
                    isAttendanceMarkedToday = false;
                    tvTodayStatus.setText("Not Marked");
                    tvTodayStatus.setTextColor(Color.parseColor("#EF4444"));
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}
