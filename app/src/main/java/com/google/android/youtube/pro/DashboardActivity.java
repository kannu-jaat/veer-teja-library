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
    private ImageView ivHeaderAvatar, ivStatusAvatar;
    private LinearLayout btnSupport, btnMyAttendanceGrid;
    
    // Bottom Nav Variables
    private LinearLayout btnDashboardNav, btnMyAttendanceNav;
    private ImageView ivDashboardIcon, ivAttendIcon;
    private TextView tvDashboardText, tvAttendText;

    private LinearLayout dashboardBottomContent;
    private View attendanceContainer;
    
    private SharedPreferences prefs;
    private String savedUsername;
    private ConnectivityManager.NetworkCallback networkCallback;
    private ConnectivityManager cm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dashboard);

        tvInternetWarning = findViewById(R.id.tvInternetWarning);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvDashName = findViewById(R.id.tvDashName);
        tvSeatNumber = findViewById(R.id.tvSeatNumber);
        tvMembershipType = findViewById(R.id.tvMembershipType);
        tvValidity = findViewById(R.id.tvValidity);
        tvDaysPresent = findViewById(R.id.tvDaysPresent);
        tvTodayStatus = findViewById(R.id.tvTodayStatus);
        tvStatusTitle = findViewById(R.id.tvStatusTitle);
        tvAttDate = findViewById(R.id.tvAttDate);
        tvAttTime = findViewById(R.id.tvAttTime);
        ivHeaderAvatar = findViewById(R.id.ivHeaderAvatar);
        ivStatusAvatar = findViewById(R.id.ivStatusAvatar);
        
        btnSupport = findViewById(R.id.btnSupport);
        btnMyAttendanceGrid = findViewById(R.id.btnMyAttendanceGrid);
        
        // Nav IDs
        btnDashboardNav = findViewById(R.id.btnDashboardNav);
        btnMyAttendanceNav = findViewById(R.id.btnMyAttendanceNav);
        ivDashboardIcon = findViewById(R.id.ivDashboardIcon);
        tvDashboardText = findViewById(R.id.tvDashboardText);
        ivAttendIcon = findViewById(R.id.ivAttendIcon);
        tvAttendText = findViewById(R.id.tvAttendText);
        
        dashboardBottomContent = findViewById(R.id.dashboard_bottom_content);
        attendanceContainer = findViewById(R.id.attendance_container);

        prefs = getSharedPreferences("LibraryApp", Context.MODE_PRIVATE);
        savedUsername = prefs.getString("username", "");

        if (savedUsername.isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setDynamicGreeting();
        String cachedName = prefs.getString("cachedName", "Student");
        tvDashName.setText(cachedName);
        loadCachedProfileImage();

        setupRealtimeInternetCheck();
        fetchProfileDataFromFirebase();
        calculateMonthlyAttendance();
        checkTodayAttendance();

        btnSupport.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + AppConfig.CONTACT_NUMBER));
            startActivity(intent);
        });

        // ATTEND Clicks
        View.OnClickListener openAttendance = v -> openAttendanceWithAnimation();
        btnMyAttendanceGrid.setOnClickListener(openAttendance);
        btnMyAttendanceNav.setOnClickListener(openAttendance);

        // DASHBOARD Click
        btnDashboardNav.setOnClickListener(v -> {
            if (attendanceContainer.getVisibility() == View.VISIBLE) {
                closeAttendanceWithAnimation();
            }
        });
    }

    // COLOR UPDATE & SPAM CLICK PREVENTION LOGIC
    private void updateBottomNavState(boolean isDashboardOpen) {
        if (isDashboardOpen) {
            ivDashboardIcon.setColorFilter(Color.parseColor("#FBBF24"));
            tvDashboardText.setTextColor(Color.parseColor("#FBBF24"));
            btnDashboardNav.setClickable(false); // Dashboard disable

            ivAttendIcon.setColorFilter(Color.parseColor("#94A3B8"));
            tvAttendText.setTextColor(Color.parseColor("#94A3B8"));
            btnMyAttendanceNav.setClickable(true); // Attend enable
        } else {
            ivDashboardIcon.setColorFilter(Color.parseColor("#94A3B8"));
            tvDashboardText.setTextColor(Color.parseColor("#94A3B8"));
            btnDashboardNav.setClickable(true); // Dashboard enable

            ivAttendIcon.setColorFilter(Color.parseColor("#FBBF24"));
            tvAttendText.setTextColor(Color.parseColor("#FBBF24"));
            btnMyAttendanceNav.setClickable(false); // Attend disable
        }
    }

    private void openAttendanceWithAnimation() {
        updateBottomNavState(false); // Make Attend Yellow
        
        dashboardBottomContent.setVisibility(View.GONE);
        attendanceContainer.setVisibility(View.VISIBLE);

        attendanceContainer.setScaleX(0.5f);
        attendanceContainer.setScaleY(0.5f);
        attendanceContainer.setAlpha(0f);
        attendanceContainer.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(350) 
                .start();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.attendance_container, new AttendanceFragment())
                .commit();
    }

    public void closeAttendanceWithAnimation() {
        updateBottomNavState(true); // Make Dashboard Yellow
        
        attendanceContainer.animate()
                .scaleX(0.5f).scaleY(0.5f).alpha(0f)
                .setDuration(300)
                .withEndAction(() -> {
                    attendanceContainer.setVisibility(View.GONE);
                    dashboardBottomContent.setVisibility(View.VISIBLE);
                    dashboardBottomContent.setAlpha(0f);
                    dashboardBottomContent.animate().alpha(1f).setDuration(200).start();
                }).start();
    }

    @Override
    public void onBackPressed() {
        if (attendanceContainer.getVisibility() == View.VISIBLE) {
            closeAttendanceWithAnimation();
        } else {
            super.onBackPressed();
        }
    }

    // --- Backend Logic Same as Before ---
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
