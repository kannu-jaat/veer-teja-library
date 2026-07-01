package com.google.android.youtube.pro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.Calendar;

public class DashboardActivity extends Activity {

    private TextView tvGreeting, tvDashName, tvSeatNumber, tvMembershipType;
    private ImageView ivHeaderAvatar, ivStatusAvatar;
    private LinearLayout btnSupport;
    private SharedPreferences prefs;
    private String savedUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dashboard);

        // 1. Map UI Elements
        tvGreeting = findViewById(R.id.tvGreeting);
        tvDashName = findViewById(R.id.tvDashName);
        tvSeatNumber = findViewById(R.id.tvSeatNumber);
        tvMembershipType = findViewById(R.id.tvMembershipType);
        ivHeaderAvatar = findViewById(R.id.ivHeaderAvatar);
        ivStatusAvatar = findViewById(R.id.ivStatusAvatar);
        btnSupport = findViewById(R.id.btnSupport);

        prefs = getSharedPreferences("LibraryApp", Context.MODE_PRIVATE);
        savedUsername = prefs.getString("username", "");

        if (savedUsername.isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // 2. Set Dynamic Greeting (Morning/Afternoon/Evening)
        setDynamicGreeting();

        // 3. FAST PRELOAD: Load Cached Name & Photo Instantly (Zero Delay)
        String cachedName = prefs.getString("cachedName", "Student");
        tvDashName.setText(cachedName);
        loadCachedProfileImage();

        // 4. FIREBASE CHECK: Background me nayi details check karo
        fetchDataFromFirebase();

        // 5. SUPPORT BUTTON LOGIC (Dialer open karega)
        btnSupport.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + AppConfig.CONTACT_NUMBER));
            startActivity(intent);
        });
    }

    private void setDynamicGreeting() {
        Calendar c = Calendar.getInstance();
        int timeOfDay = c.get(Calendar.HOUR_OF_DAY);

        if (timeOfDay >= 0 && timeOfDay < 12) {
            tvGreeting.setText("Good Morning,");
        } else if (timeOfDay >= 12 && timeOfDay < 16) {
            tvGreeting.setText("Good Afternoon,");
        } else {
            tvGreeting.setText("Good Evening,");
        }
    }

    private void loadCachedProfileImage() {
        File imgFile = new File(getFilesDir(), "profile_avatar.jpg");
        if (imgFile.exists()) {
            Bitmap myBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
            if (ivHeaderAvatar != null) ivHeaderAvatar.setImageBitmap(myBitmap);
            if (ivStatusAvatar != null) ivStatusAvatar.setImageBitmap(myBitmap);
        }
    }

    private void fetchDataFromFirebase() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Students").child(savedUsername);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String fullName = snapshot.child("fullName").getValue(String.class);
                    String seat = snapshot.child("seatNumber").getValue(String.class);
                    String photoUrl = snapshot.child("photoUrl").getValue(String.class);
                    String status = snapshot.child("status").getValue(String.class);

                    // Update Name if changed
                    if (fullName != null) {
                        tvDashName.setText(fullName);
                        prefs.edit().putString("cachedName", fullName).apply();
                    }

                    // Admin assigned Seat Number
                    if (seat != null && !seat.isEmpty()) {
                        tvSeatNumber.setText(seat);
                    } else {
                        tvSeatNumber.setText("N/A");
                    }

                    // Admin assigned Membership (Simple Logic)
                    if ("Approved".equals(status)) {
                        tvMembershipType.setText("Premium");
                        tvMembershipType.setTextColor(android.graphics.Color.parseColor("#FBBF24")); // Yellow
                    } else {
                        tvMembershipType.setText("Pending");
                        tvMembershipType.setTextColor(android.graphics.Color.parseColor("#EF4444")); // Red
                    }

                    // 🔥 SMART IMAGE DOWNLOAD: Sirf tabhi download hogi jab URL naya ho!
                    String lastSavedUrl = prefs.getString("cachedImageUrl", "");
                    if (photoUrl != null && !photoUrl.isEmpty() && !photoUrl.equals(lastSavedUrl)) {
                        downloadAndCacheImage(photoUrl);
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {}
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
                    // Save to local storage for future fast loading
                    File file = new File(getFilesDir(), "profile_avatar.jpg");
                    FileOutputStream fos = new FileOutputStream(file);
                    myBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    fos.flush();
                    fos.close();

                    // Update SharedPreferences with new URL
                    prefs.edit().putString("cachedImageUrl", urlString).apply();

                    // Update UI live
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (ivHeaderAvatar != null) ivHeaderAvatar.setImageBitmap(myBitmap);
                        if (ivStatusAvatar != null) ivStatusAvatar.setImageBitmap(myBitmap);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}