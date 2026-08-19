package com.google.android.youtube.pro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ProfileActivity extends AppCompatActivity {

    private ImageView btnBack, ivProfileImage, btnUpdatePhoto;
    private TextView tvFullNameHeader, tvUsernameHeader, tvStatusBadge, tvMemberSince;
    private Button btnLogout, btnEditProfile;
    
    private SharedPreferences prefs;
    private String savedUsername;

    // Image Picker Result Launcher
    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    try {
                        Uri imageUri = result.getData().getData();
                        InputStream imageStream = getContentResolver().openInputStream(imageUri);
                        Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
                        ivProfileImage.setImageBitmap(selectedImage); // Instant local update
                        uploadImageToCloudinary(selectedImage); // Background upload
                    } catch (Exception e) {
                        showCustomToast("Failed to load image", false);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        prefs = getSharedPreferences("LibraryApp", Context.MODE_PRIVATE);
        savedUsername = prefs.getString("username", "");

        initViews();
        loadCachedData();
        fetchLatestDataFromFirebase();
        setupClickListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        ivProfileImage = findViewById(R.id.ivProfileImage);
        btnUpdatePhoto = findViewById(R.id.btnUpdatePhoto);
        tvFullNameHeader = findViewById(R.id.tvFullNameHeader);
        tvUsernameHeader = findViewById(R.id.tvUsernameHeader);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvMemberSince = findViewById(R.id.tvMemberSince);
        btnLogout = findViewById(R.id.btnLogout);
        btnEditProfile = findViewById(R.id.btnEditProfile);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> onBackPressed());

        btnUpdatePhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(intent);
        });

        btnEditProfile.setOnClickListener(v -> showCustomToast("Edit Information Feature Coming Soon!", true));

        btnLogout.setOnClickListener(v -> logoutUser());
    }

    private void loadCachedData() {
        tvUsernameHeader.setText(savedUsername);
        tvFullNameHeader.setText(prefs.getString("cachedName", "Student"));
        
        File imgFile = new File(getFilesDir(), "profile_avatar.jpg");
        if (imgFile.exists()) {
            ivProfileImage.setImageBitmap(BitmapFactory.decodeFile(imgFile.getAbsolutePath()));
        }
    }

    private void fetchLatestDataFromFirebase() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Students").child(savedUsername);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String fullName = snapshot.child("fullName").getValue(String.class);
                    String status = snapshot.child("status").getValue(String.class);
                    String memberSince = snapshot.child("memberSince").getValue(String.class);

                    if (fullName != null) tvFullNameHeader.setText(fullName);
                    if (status != null) {
                        tvStatusBadge.setText(status);
                        if(status.equalsIgnoreCase("Approved")) tvStatusBadge.setTextColor(Color.parseColor("#10B981"));
                        else tvStatusBadge.setTextColor(Color.parseColor("#EF4444"));
                    }
                    if (memberSince != null) tvMemberSince.setText("Member Since: " + memberSince);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // 🔥 CLOUDINARY UPLOAD ENGINE (Using AppConfig dynamically)
    private void uploadImageToCloudinary(Bitmap bitmap) {
        showCustomToast("Uploading new photo...", true);

        new Thread(() -> {
            try {
                // 1. Convert Bitmap to Base64 String
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream);
                byte[] byteArray = byteArrayOutputStream.toByteArray();
                String base64Image = "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.DEFAULT);

                // 🔥 Use dynamic values from AppConfig
                String cloudinaryUrl = "https://api.cloudinary.com/v1_1/" + AppConfig.CLOUDINARY_CLOUD_NAME + "/image/upload";
                String postData = "upload_preset=" + AppConfig.CLOUDINARY_UPLOAD_PRESET + "&file=" + java.net.URLEncoder.encode(base64Image, "UTF-8");

                // 2. Prepare HTTP POST request
                URL url = new URL(cloudinaryUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                // 3. Send Data
                OutputStream os = conn.getOutputStream();
                os.write(postData.getBytes());
                os.flush();
                os.close();

                // 4. Read Response
                java.util.Scanner scanner = new java.util.Scanner(conn.getInputStream());
                String response = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                scanner.close();

                // 5. Parse JSON to get Secure URL
                JSONObject jsonObject = new JSONObject(response);
                String secureUrl = jsonObject.getString("secure_url");

                // 6. Update Firebase & Cache on Main Thread
                new Handler(Looper.getMainLooper()).post(() -> updateFirebasePhotoUrl(secureUrl));

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> showCustomToast("Upload Failed! Try again.", false));
            }
        }).start();
    }

    private void updateFirebasePhotoUrl(String newUrl) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Students").child(savedUsername);
        ref.child("photoUrl").setValue(newUrl).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                showCustomToast("Profile Photo Updated!", true);
                prefs.edit().putString("cachedImageUrl", newUrl).apply(); // Update cache pointer
            }
        });
    }

    // 🔥 LOGOUT ENGINE
    private void logoutUser() {
        // Clear all Cache
        prefs.edit().clear().apply();

        // Redirect to Login & Destroy all previous screens
        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity();
    }

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

    // Custom exit animation on back button
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, R.anim.slide_out_top_left); 
    }
}
