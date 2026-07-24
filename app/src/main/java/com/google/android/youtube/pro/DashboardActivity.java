package com.google.android.youtube.pro;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity; 

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

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
    private View fragmentContainer; // Generic container for all fragments
    
    private SharedPreferences prefs;
    private String savedUsername;
    private ConnectivityManager.NetworkCallback networkCallback;
    private ConnectivityManager cm;

    // 🔥 SMART NAVIGATION STATES
    private long lastClickTime = 0;
    private static final int STATE_DASHBOARD = 0;
    private static final int STATE_ATTENDANCE = 1;
    private static final int STATE_PAYMENTS = 2;
    private static final int STATE_MORE = 3;
    private int currentState = STATE_DASHBOARD;

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
        // Top Header
        tvInternetWarning = findViewById(R.id.tvInternetWarning);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvDashName = findViewById(R.id.tvDashName);
        ivHeaderAvatar = findViewById(R.id.ivHeaderAvatar);
        btnNotifications = findViewById(R.id.btnNotifications);
        
        // Status Cards
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
        
        // Grid Buttons
        btnMarkAttendGrid = findViewById(R.id.btnMarkAttendGrid);
        btnMyAttendanceGrid = findViewById(R.id.btnMyAttendanceGrid);
        btnMySeatGrid = findViewById(R.id.btnMySeatGrid);
        btnFeesGrid = findViewById(R.id.btnFeesGrid);
        btnNoticesGrid = findViewById(R.id.btnNoticesGrid);
        btnRulesGrid = findViewById(R.id.btnRulesGrid);
        btnProfileGrid = findViewById(R.id.btnProfileGrid);
        btnSupportGrid = findViewById(R.id.btnSupportGrid);
        
        // Bottom Nav Buttons
        btnDashboardNav = findViewById(R.id.btnDashboardNav);
        btnMyAttendanceNav = findViewById(R.id.btnMyAttendanceNav);
        btnPaymentsNav = findViewById(R.id.btnPaymentsNav);
        btnMoreNav = findViewById(R.id.btnMoreNav);
        btnCenterCameraFab = findViewById(R.id.btnCenterCameraFab);
        
        // Bottom Nav Icons & Texts
        ivDashboardIcon = findViewById(R.id.ivDashboardIcon);
        tvDashboardText = findViewById(R.id.tvDashboardText);
        ivAttendIcon = findViewById(R.id.ivAttendIcon);
        tvAttendText = findViewById(R.id.tvAttendText);
        ivPaymentsIcon = findViewById(R.id.ivPaymentsIcon);
        tvPaymentsText = findViewById(R.id.tvPaymentsText);
        ivMoreIcon = findViewById(R.id.ivMoreIcon);
        tvMoreText = findViewById(R.id.tvMoreText);
        
        // Containers
        dashboardBottomContent = findViewById(R.id.dashboard_bottom_content);
        fragmentContainer = findViewById(R.id.fragment_container); // Updated ID
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

    // 🔥 CENTRAL CLICK ROUTER (FUTURE-PROOF)
    private void setupClickListeners() {
        // ACTIVE FEATURES (Will load fragments/actions)
        btnDashboardNav.setOnClickListener(v -> handleNavigation(STATE_DASHBOARD));
        
        btnMyAttendanceGrid.setOnClickListener(v -> handleNavigation(STATE_ATTENDANCE));
        btnMyAttendanceNav.setOnClickListener(v -> handleNavigation(STATE_ATTENDANCE));
        
        btnFeesGrid.setOnClickListener(v -> handleNavigation(STATE_PAYMENTS));
        btnPaymentsNav.setOnClickListener(v -> handleNavigation(STATE_PAYMENTS));

        // MORE NAV
        btnMoreNav.setOnClickListener(v -> handleNavigation(STATE_MORE));

        // SUPPORT EXTERNAL INTENT
        btnSupportGrid.setOnClickListener(v -> {
            if (isSpamClick()) return;
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + AppConfig.CONTACT_NUMBER));
            startActivity(intent);
        });

        // INACTIVE FEATURES (Show Coming Soon)
        View.OnClickListener comingSoonListener = v -> {
            if (!isSpamClick()) Toast.makeText(this, "Feature coming soon!", Toast.LENGTH_SHORT).show();
        };
        
        btnMarkAttendGrid.setOnClickListener(comingSoonListener);
        btnMySeatGrid.setOnClickListener(comingSoonListener);
        btnNoticesGrid.setOnClickListener(comingSoonListener);
        btnRulesGrid.setOnClickListener(comingSoonListener);
        btnProfileGrid.setOnClickListener(comingSoonListener);
        btnNotifications.setOnClickListener(comingSoonListener);
        cvLatestNotice.setOnClickListener(comingSoonListener);
        btnCenterCameraFab.setOnClickListener(comingSoonListener);
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
        } else if (targetState == STATE_MORE) {
            // TODO: Open MoreFragment when created in future
            Toast.makeText(this, "More Settings Menu Coming Soon", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateBottomNavColors(int activeState) {
        // Reset ALL to Gray/Disabled
        int grayColor = Color.parseColor("#94A3B8");
        ivDashboardIcon.setColorFilter(grayColor); tvDashboardText.setTextColor(grayColor);
        ivAttendIcon.setColorFilter(grayColor);    tvAttendText.setTextColor(grayColor);
        ivPaymentsIcon.setColorFilter(grayColor);  tvPaymentsText.setTextColor(grayColor);
        ivMoreIcon.setColorFilter(grayColor);      tvMoreText.setTextColor(grayColor);

        // Set Active to Yellow
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

    // --- Firebase & Utility Methods Below ---
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
                    else tvSeatNumber.setText("--");

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
        String todayDateString = new SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH).format(new Date());
        if (tvAttDate != null) tvAttDate.setText(todayDateString);

        DatabaseReference attRef = FirebaseDatabase.getInstance().getReference("Attendance").child(savedUsername).child(todayDateString);
        attRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String checkInTime = snapshot.child("checkIn").getValue(String.class);
                    tvTodayStatus.setText("Marked ✓");
                    tvTodayStatus.setTextColor(Color.parseColor("#10B981"));
                    if (tvAttTime != null) tvAttTime.setText(checkInTime);
                } else {
                    tvTodayStatus.setText("Not Marked");
                    tvTodayStatus.setTextColor(Color.parseColor("#EF4444"));
                    if (tvAttTime != null) tvAttTime.setText("--:--");
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
