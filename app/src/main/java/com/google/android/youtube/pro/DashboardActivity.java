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
    private TextView tvTodayStatus, tvStatusTitle, tvAttDate, tvAttTime, tvDaysPresent;
    private ImageView ivHeaderAvatar, ivStatusAvatar, btnNotifications;

    // All Grid Buttons
    private LinearLayout btnMarkAttendGrid, btnMyAttendanceGrid, btnMySeatGrid, btnFeesGrid;
    private LinearLayout btnNoticesGrid, btnRulesGrid, btnProfileGrid, btnSupportGrid;
    private CardView cvLatestNotice;

    // Bottom Nav Elements
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

    // 🔥 SMART NAVIGATION & ATTENDANCE STATES
    private long lastClickTime = 0;
    private static final int STATE_DASHBOARD = 0;
    private static final int STATE_ATTENDANCE = 1;
    private static final int STATE_PAYMENTS = 2;
    private static final int STATE_MORE = 3;
    private static final int STATE_RULES = 4;
    private int currentState = STATE_DASHBOARD;
    
    // Scanner block flag
    private boolean isAttendanceMarkedToday = false; 

    // 🔥 Permission Launcher for Camera ONLY (No GPS)
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean cameraGranted = result.getOrDefault(Manifest.permission.CAMERA, false);
                if (cameraGranted) {
                    launchQRScanner();
                } else {
                    showCustomToast("Camera permission required for Attendance!", false);
                }
            });

    // 🔥 QR Scanner Result System
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
        tvStatusTitle = findViewById(R.id.tvStatusTitle);
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
        String cachedName = prefs.getString("cachedName", "Student");
        tvDashName.setText(cachedName);
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

        btnSupportGrid.setOnClickListener(v -> {
            if (isSpamClick()) return;
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + AppConfig.CONTACT_NUMBER));
            startActivity(intent);
        });

        // 🔥 Mark Attendance Clicks
        btnMarkAttendGrid.setOnClickListener(v -> checkPermissionsAndScan());
        btnCenterCameraFab.setOnClickListener(v -> checkPermissionsAndScan());

        // Inactive Features
        View.OnClickListener comingSoonListener = v -> {
            if (!isSpamClick()) showCustomToast("Feature coming soon!", false);
        };

        btnMySeatGrid.setOnClickListener(comingSoonListener);
        btnNoticesGrid.setOnClickListener(comingSoonListener);
        btnProfileGrid.setOnClickListener(comingSoonListener);
        btnNotifications.setOnClickListener(comingSoonListener);
        cvLatestNotice.setOnClickListener(comingSoonListener);
    }

    // 🔥 Premium Custom Toast Programmatically Built
    private void showCustomToast(String message, boolean isSuccess) {
        Toast toast = new Toast(this);
        toast.setDuration(Toast.LENGTH_LONG);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(40, 24, 40, 24);
        layout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(isSuccess ? Color.parseColor("#065F46") : Color.parseColor("#991B1B")); 
        gd.setCornerRadius(50f);
        gd.setStroke(2, isSuccess ? Color.parseColor("#10B981") : Color.parseColor("#EF4444")); 
        layout.setBackground(gd);

        TextView icon = new TextView(this);
        icon.setText(isSuccess ? "✅" : "⚠️");
        icon.setTextSize(16f);

        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14f);
        tv.setPadding(20, 0, 0, 0);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);

        layout.addView(icon);
        layout.addView(tv);

        toast.setView(layout);
        toast.show();
    }

    // 🔥 Check Flag before opening scanner (No GPS Needed)
    private void checkPermissionsAndScan() {
        if (isSpamClick()) return;

        if (isAttendanceMarkedToday) {
            showCustomToast("Attendance already marked for today!", true);
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
        options.setPrompt("Scan Library QR to Mark Attendance");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setCaptureActivity(PortraitCaptureActivity.class); 
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        barcodeLauncher.launch(options);
    }

    // 🔥 NEW: Background IP Fetching with Trim
    private void verifyQrAndMarkAttendance(String scannedData) {
        showCustomToast("Verifying securely...", true);
        
        new Thread(() -> {
            try {
                URL url = new URL("https://api.ipify.org");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                java.util.Scanner scanner = new java.util.Scanner(connection.getInputStream());
                
                // 🔥 .trim() added here to remove invisible spaces/newlines
                String myPublicIP = scanner.useDelimiter("\\A").hasNext() ? scanner.next().trim() : "";
                scanner.close();

                new Handler(Looper.getMainLooper()).post(() -> {
                    verifyQrWithIP(scannedData, myPublicIP);
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    showCustomToast("Network Error! Try again.", false);
                });
            }
        }).start();
    }


        // 🔥 NEW: IP Verification & Checks (With Debug Mode)
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
                boolean isAdmin = false;
                if(studentSnap.hasChild("Admin")) {
                    Object adminObj = studentSnap.child("Admin").getValue();
                    if(adminObj != null && adminObj.toString().equalsIgnoreCase("true")) {
                        isAdmin = true;
                    }
                }
                
                final boolean finalIsAdmin = isAdmin;

                DatabaseReference qrRef = FirebaseDatabase.getInstance().getReference("QRConfig/current");
                qrRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot qrSnap) {
                        if(qrSnap.exists()) {
                            String dbHash = qrSnap.child("hash").getValue(String.class);
                            String dbAllowedIP = qrSnap.child("allowedIP").getValue(String.class);

                            if (dbHash != null && finalScannedHash.equals(dbHash)) {
                                if (finalIsAdmin) {
                                    showCustomToast("Admin Bypass Active 🚀", true);
                                    markAttendanceInDatabase();
                                } else {
                                    // 🔥 CLEANING DB IP BEFORE MATCHING
                                    String cleanDbIP = (dbAllowedIP != null) ? dbAllowedIP.trim() : "";
                                    
                                    if (cleanDbIP.isEmpty() || studentIP.equals(cleanDbIP)) {
                                        markAttendanceInDatabase();
                                    } else {
                                        // 🔥 DEBUG TOAST: Yeh aapko batayega ki problem kahan hai
                                        showCustomToast("Proxy Blocked!\nMy IP: " + studentIP + "\nDB IP: " + cleanDbIP, false);
                                    }
                                }
                            } else {
                                showCustomToast("Invalid or Old QR Code!", false);
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
        DatabaseReference offsetRef = FirebaseDatabase.getInstance().getReference(".info/serverTimeOffset");
        offsetRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot offsetSnapshot) {
                long offset = offsetSnapshot.exists() ? offsetSnapshot.getValue(Long.class) : 0;
                long estimatedServerTimeMs = System.currentTimeMillis() + offset;
                Date onlineDate = new Date(estimatedServerTimeMs);

                SimpleDateFormat dateSdf = new SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH);
                SimpleDateFormat timeSdf = new SimpleDateFormat("hh:mm a", Locale.ENGLISH);
                String dateString = dateSdf.format(onlineDate);
                String timeString = timeSdf.format(onlineDate);

                DatabaseReference attRef = FirebaseDatabase.getInstance().getReference("Attendance").child(savedUsername).child(dateString);
                attRef.child("checkIn").setValue(timeString).addOnCompleteListener(task -> {
                    if(task.isSuccessful()){
                        showCustomToast("Attendance Marked Successfully!", true);
                        checkTodayAttendance(); 
                        calculateMonthlyAttendance(); 
                    } else {
                        showCustomToast("Failed to mark attendance.", false);
                    }
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private boolean isSpamClick() {
        if (System.currentTimeMillis() - lastClickTime < 600) {
            return true;
        }
        lastClickTime = System.currentTimeMillis();
        return false;
    }

    private void handleNavigation(int targetState) {
        if (isSpamClick() || currentState == targetState) return;

        currentState = targetState;
        updateBottomNavColors(targetState);

        if (targetState == STATE_DASHBOARD) {
            closeFragmentWithAnimation();
        } else if (targetState == STATE_ATTENDANCE) {
            openFragmentWithAnimation(new AttendanceFragment());
        } else if (targetState == STATE_PAYMENTS) {
            openFragmentWithAnimation(new FeesFragment());
        } else if (targetState == STATE_RULES) { 
            openFragmentWithAnimation(new RulesFragment());
        } else if (targetState == STATE_MORE) {
            showCustomToast("More Settings Menu Coming Soon", false);
        }
    }

    private void updateBottomNavColors(int activeState) {
        int grayColor = Color.parseColor("#94A3B8");
        ivDashboardIcon.setColorFilter(grayColor); tvDashboardText.setTextColor(grayColor);
        ivAttendIcon.setColorFilter(grayColor);    tvAttendText.setTextColor(grayColor);
        ivPaymentsIcon.setColorFilter(grayColor);  tvPaymentsText.setTextColor(grayColor);
        ivMoreIcon.setColorFilter(grayColor);      tvMoreText.setTextColor(grayColor);

        int activeColor = Color.parseColor("#FBBF24");
        switch (activeState) {
            case STATE_DASHBOARD:
                ivDashboardIcon.setColorFilter(activeColor); tvDashboardText.setTextColor(activeColor);
                break;
            case STATE_ATTENDANCE:
                ivAttendIcon.setColorFilter(activeColor); tvAttendText.setTextColor(activeColor);
                break;
            case STATE_PAYMENTS:
                ivPaymentsIcon.setColorFilter(activeColor); tvPaymentsText.setTextColor(activeColor);
                break;
            case STATE_MORE:
                ivMoreIcon.setColorFilter(activeColor); tvMoreText.setTextColor(activeColor);
                break;
        }
    }

    private void openFragmentWithAnimation(Fragment fragment) {
        dashboardBottomContent.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);

        fragmentContainer.setScaleX(0.5f);
        fragmentContainer.setScaleY(0.5f);
        fragmentContainer.setAlpha(0f);
        fragmentContainer.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(350) 
                .start();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    public void closeFragmentWithAnimation() {
        currentState = STATE_DASHBOARD;
        updateBottomNavColors(STATE_DASHBOARD);

        fragmentContainer.animate()
                .scaleX(0.5f).scaleY(0.5f).alpha(0f)
                .setDuration(300)
                .withEndAction(() -> {
                    fragmentContainer.setVisibility(View.GONE);
                    dashboardBottomContent.setVisibility(View.VISIBLE);
                    dashboardBottomContent.setAlpha(0f);
                    dashboardBottomContent.animate().alpha(1f).setDuration(200).start();
                }).start();
    }

    @Override
    public void onBackPressed() {
        if (fragmentContainer.getVisibility() == View.VISIBLE) {
            closeFragmentWithAnimation();
        } else {
            super.onBackPressed();
        }
    }

    private void setupRealtimeInternetCheck() {
        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null && cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected()) {
            tvInternetWarning.setVisibility(View.GONE);
        } else {
            tvInternetWarning.setVisibility(View.VISIBLE);
        }
        NetworkRequest networkRequest = new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) { runOnUiThread(() -> tvInternetWarning.setVisibility(View.GONE)); }
            @Override public void onLost(Network network) { runOnUiThread(() -> tvInternetWarning.setVisibility(View.VISIBLE)); }
        };
        if (cm != null) cm.registerNetworkCallback(networkRequest, networkCallback);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cm != null && networkCallback != null) cm.unregisterNetworkCallback(networkCallback);
    }

    private void setDynamicGreeting() {
        Calendar c = Calendar.getInstance();
        int timeOfDay = c.get(Calendar.HOUR_OF_DAY);
        if (timeOfDay >= 0 && timeOfDay < 12) tvGreeting.setText("Good Morning,");
        else if (timeOfDay >= 12 && timeOfDay < 16) tvGreeting.setText("Good Afternoon,");
        else tvGreeting.setText("Good Evening,");
    }

    private void loadCachedProfileImage() {
        File imgFile = new File(getFilesDir(), "profile_avatar.jpg");
        if (imgFile.exists()) {
            Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
            if (ivHeaderAvatar != null) ivHeaderAvatar.setImageBitmap(myBitmap);
            if (ivStatusAvatar != null) ivStatusAvatar.setImageBitmap(myBitmap);
        }
    }

    private void fetchProfileDataFromFirebase() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Students").child(savedUsername);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String fullName = snapshot.child("fullName").getValue(String.class);
                    String seat = snapshot.child("seatNumber").getValue(String.class);
                    String membership = snapshot.child("membership").getValue(String.class);
                    String validTill = snapshot.child("validTill").getValue(String.class);
                    String photoUrl = snapshot.child("photoUrl").getValue(String.class);

                    if (fullName != null) {
                        tvDashName.setText(fullName);
                        prefs.edit().putString("cachedName", fullName).apply();
                    }
                    if (seat != null && !seat.isEmpty()) tvSeatNumber.setText(seat);
                    else tvSeatNumber.setText(" ");

                    if (membership != null && !membership.isEmpty()) {
                        tvMembershipType.setText(membership);
                        tvMembershipType.setTextColor(Color.parseColor("#FBBF24"));
                    } else {
                        tvMembershipType.setText("Pending");
                    }

                    if (validTill != null && !validTill.isEmpty()) tvValidity.setText("Valid till " + validTill);
                    else tvValidity.setText("Valid till --");

                    String lastSavedUrl = prefs.getString("cachedImageUrl", "");
                    if (photoUrl != null && !photoUrl.isEmpty() && !photoUrl.equals(lastSavedUrl)) {
                        downloadAndCacheImage(photoUrl);
                    }
                }
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void calculateMonthlyAttendance() {
        String currentMonthYear = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(new Date());
        DatabaseReference attRef = FirebaseDatabase.getInstance().getReference("Attendance").child(savedUsername);
        attRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                int presentDays = 0;
                for (DataSnapshot daySnap : snapshot.getChildren()) {
                    String dateKey = daySnap.getKey(); 
                    if (dateKey != null && dateKey.endsWith(currentMonthYear)) presentDays++;
                }
                if (tvDaysPresent != null) tvDaysPresent.setText(String.valueOf(presentDays));
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void checkTodayAttendance() {
        String todayDateString = new SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(new Date());
        if (tvAttDate != null) tvAttDate.setText(todayDateString);

        DatabaseReference attRef = FirebaseDatabase.getInstance().getReference("Attendance").child(savedUsername).child(todayDateString);
        attRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    isAttendanceMarkedToday = true; // 🔥 Block Scanner Flag
                    String checkInTime = snapshot.child("checkIn").getValue(String.class);
                    tvTodayStatus.setText("Marked ✓");
                    tvTodayStatus.setTextColor(Color.parseColor("#10B981"));
                    if (tvAttTime != null) tvAttTime.setText(checkInTime);
                } else {
                    isAttendanceMarkedToday = false; // 🔥 Allow Scanner Flag
                    tvTodayStatus.setText("Not Marked");
                    tvTodayStatus.setTextColor(Color.parseColor("#EF4444"));
                    if (tvAttTime != null) tvAttTime.setText("__:__");
                }
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void downloadAndCacheImage(String urlString) {
        new Thread(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                Bitmap myBitmap = BitmapFactory.decodeStream(input);

                if (myBitmap != null) {
                    File file = new File(getFilesDir(), "profile_avatar.jpg");
                    FileOutputStream fos = new FileOutputStream(file);
                    myBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    fos.flush(); fos.close();

                    prefs.edit().putString("cachedImageUrl", urlString).apply();

                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (ivHeaderAvatar != null) ivHeaderAvatar.setImageBitmap(myBitmap);
                        if (ivStatusAvatar != null) ivStatusAvatar.setImageBitmap(myBitmap);
                    });
                }
            } catch (Exception e) {}
        }).start();
    }
}
