package com.google.android.youtube.pro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DashboardActivity extends Activity {

    private TextView tvDashName, tvDashLibName, tvStatusBadge, tvSeatBadge;
    private ImageView ivDashAvatar;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dashboard);

        // 1. UI Elements ko XML se connect karna
        tvDashName = findViewById(R.id.tvDashName);
        tvDashLibName = findViewById(R.id.tvDashLibName);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvSeatBadge = findViewById(R.id.tvSeatBadge);
        ivDashAvatar = findViewById(R.id.ivDashAvatar);
        
        // 2. AppConfig se Master Library Name set karna
        if (tvDashLibName != null) {
            tvDashLibName.setText("Welcome to " + AppConfig.LIBRARY_NAME);
        }

        // 3. Login kiye hue bache ka Username nikalna
        prefs = getSharedPreferences("LibraryApp", Context.MODE_PRIVATE);
        String savedUsername = prefs.getString("username", "");

        if (savedUsername.isEmpty()) {
            // Agar galti se bina login yahan aa gaya, toh wapas bhej do
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // 4. Firebase se asli data mangwana
        loadStudentData(savedUsername);
    }

    private void loadStudentData(String username) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Students").child(username);
        
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String fullName = snapshot.child("fullName").getValue(String.class);
                    String status = snapshot.child("status").getValue(String.class);
                    String photoUrl = snapshot.child("photoUrl").getValue(String.class);
                    String seat = snapshot.child("seatNumber").getValue(String.class);

                    // Naam set karna
                    if (fullName != null) tvDashName.setText(fullName);
                    
                    // Status Badge Logic
                    if ("Approved".equals(status)) {
                        tvStatusBadge.setText("✓ Approved • Active");
                        tvStatusBadge.setTextColor(android.graphics.Color.parseColor("#10B981"));
                    } else {
                        tvStatusBadge.setText("⌛ Pending Approval");
                        tvStatusBadge.setTextColor(android.graphics.Color.parseColor("#F59E0B"));
                    }

                    // Seat Logic
                    if (seat != null && !seat.isEmpty()) {
                        tvSeatBadge.setText("Seat " + seat);
                    } else {
                        tvSeatBadge.setText("Seat Pending");
                    }

                    // 🔥 Cloudinary se Photo load karna (Background Thread me)
                    if (photoUrl != null && !photoUrl.isEmpty()) {
                        loadImageFromCloud(photoUrl);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(DashboardActivity.this, "Failed to load Dashboard data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Direct URL se image nikal kar ImageView me daalne ka Master Logic
    private void loadImageFromCloud(String urlString) {
        new Thread(() -> {
            try {
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                Bitmap myBitmap = BitmapFactory.decodeStream(input);
                
                // Photo load hone ke baad wapas Main UI me aana
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (myBitmap != null && ivDashAvatar != null) {
                        ivDashAvatar.setImageBitmap(myBitmap);
                        ivDashAvatar.setPadding(0,0,0,0); // Padding hatao taaki photo gol (circle) me poori fit ho
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}