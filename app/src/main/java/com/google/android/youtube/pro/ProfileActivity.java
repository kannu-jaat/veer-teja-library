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

    // 🔥 Naye Views Rows ke liye
    private View rowName, rowPhone, rowDob, rowAddress;

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
        loadCachedData(); // 🔥 Pehle Cache Load Hoga
        fetchLatestDataFromFirebase(); // 🔥 Fir Background me Firebase se Update Hoga
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

        rowName = findViewById(R.id.rowName);
        rowPhone = findViewById(R.id.rowPhone);
        rowDob = findViewById(R.id.rowDob);
        rowAddress = findViewById(R.id.rowAddress);

        // 🔥 SLIDE-UP & FADE-IN ANIMATION 🔥
        View bottomInfoSection = findViewById(R.id.bottomInfoSection);
        LinearLayout headerTextData = findViewById(R.id.profileHeader).findViewById(R.id.tvFullNameHeader).getParent(); // Header ke text ka container

        // 1. Initial State (Chhupa do aur thoda neeche kar do)
        bottomInfoSection.setAlpha(0f);
        bottomInfoSection.setTranslationY(150f); 
        
        headerTextData.setAlpha(0f);
        headerTextData.setTranslationX(-50f); // Text halka sa left se aayega

        // 2. Animate State (Fast slide up aur fade in)
        bottomInfoSection.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400) // 0.4 seconds (Fast & Smooth)
                .setStartDelay(200) // Photo udne ke beech me start hoga
                .start();

        headerTextData.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(400)
                .setStartDelay(150)
                .start();
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

    // 🔥 HELPER METHOD: Info Rows me data aur icon set karne ke liye
    private void setRowData(View row, String label, String value, int iconRes) {
        if (row != null) {
            ImageView ivIcon = row.findViewById(R.id.rowIcon);
            TextView tvLabel = row.findViewById(R.id.rowLabel);
            TextView tvValue = row.findViewById(R.id.rowValue);

            if (ivIcon != null) ivIcon.setImageResource(iconRes);
            if (tvLabel != null) tvLabel.setText(label);
            if (tvValue != null) tvValue.setText((value != null && !value.isEmpty()) ? value : "Not provided");
        }
    }

    // 🔥 OFFLINE CACHE LOAD ENGINE
    private void loadCachedData() {
        tvUsernameHeader.setText(savedUsername);

        String cachedName = prefs.getString("cachedName", "Student");
        tvFullNameHeader.setText(cachedName);

        String cachedStatus = prefs.getString("cachedStatus", "Loading...");
        tvStatusBadge.setText(cachedStatus);
        if(cachedStatus.equalsIgnoreCase("Approved")) tvStatusBadge.setTextColor(Color.parseColor("#10B981"));
        else tvStatusBadge.setTextColor(Color.parseColor("#EF4444"));

        tvMemberSince.setText("Member Since: " + prefs.getString("cachedMemberSince", "--"));

        // Set Bottom Rows from Cache
        setRowData(rowName, "Full Name", cachedName, android.R.drawable.ic_menu_myplaces);
        setRowData(rowPhone, "Phone Number", prefs.getString("cachedPhone", "--"), android.R.drawable.ic_menu_call);
        setRowData(rowDob, "Date of Birth", prefs.getString("cachedDob", "--"), android.R.drawable.ic_menu_today);
        setRowData(rowAddress, "Address", prefs.getString("cachedAddress", "--"), android.R.drawable.ic_menu_mapmode);

        File imgFile = new File(getFilesDir(), "profile_avatar.jpg");
        if (imgFile.exists()) {
            ivProfileImage.setImageBitmap(BitmapFactory.decodeFile(imgFile.getAbsolutePath()));
        }
    }

    // 🔥 FIREBASE FETCH & CACHE UPDATE ENGINE
    private void fetchLatestDataFromFirebase() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Students").child(savedUsername);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    SharedPreferences.Editor editor = prefs.edit();

                    String fullName = snapshot.child("fullName").getValue(String.class);
                    String status = snapshot.child("status").getValue(String.class);
                    String memberSince = snapshot.child("registrationTime").getValue(String.class);
                    String phone = snapshot.child("mobile").getValue(String.class); // Firebase key: phone
                    String dob = snapshot.child("dob").getValue(String.class);     // Firebase key: dob
                    String address = snapshot.child("address").getValue(String.class); // Firebase key: address

                    if (fullName != null) {
                        tvFullNameHeader.setText(fullName);
                        editor.putString("cachedName", fullName);
                        setRowData(rowName, "Full Name", fullName, android.R.drawable.ic_menu_myplaces);
                    }

                    if (status != null) {
                        tvStatusBadge.setText(status);
                        if(status.equalsIgnoreCase("Approved")) tvStatusBadge.setTextColor(Color.parseColor("#10B981"));
                        else tvStatusBadge.setTextColor(Color.parseColor("#EF4444"));
                        editor.putString("cachedStatus", status);
                    }

                    if (memberSince != null) {
                        tvMemberSince.setText("Member Since: " + memberSince);
                        editor.putString("cachedMemberSince", memberSince);
                    }

                    if (phone != null) {
                        editor.putString("cachedPhone", phone);
                        setRowData(rowPhone, "Phone Number", phone, android.R.drawable.ic_menu_call);
                    }

                    if (dob != null) {
                        editor.putString("cachedDob", dob);
                        setRowData(rowDob, "Date of Birth", dob, android.R.drawable.ic_menu_today);
                    }

                    if (address != null) {
                        editor.putString("cachedAddress", address);
                        setRowData(rowAddress, "Address", address, android.R.drawable.ic_menu_mapmode);
                    }

                    // Save all fresh data to Cache permanently
                    editor.apply(); 
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // 🔥 CLOUDINARY UPLOAD ENGINE 
    private void uploadImageToCloudinary(Bitmap bitmap) {
        showCustomToast("Uploading new photo...", true);

        new Thread(() -> {
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream);
                byte[] byteArray = byteArrayOutputStream.toByteArray();
                String base64Image = "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.DEFAULT);

                String cloudinaryUrl = "https://api.cloudinary.com/v1_1/" + AppConfig.CLOUDINARY_CLOUD_NAME + "/image/upload";
                String postData = "upload_preset=" + AppConfig.CLOUDINARY_UPLOAD_PRESET + "&file=" + java.net.URLEncoder.encode(base64Image, "UTF-8");

                URL url = new URL(cloudinaryUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                OutputStream os = conn.getOutputStream();
                os.write(postData.getBytes());
                os.flush();
                os.close();

                java.util.Scanner scanner = new java.util.Scanner(conn.getInputStream());
                String response = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                scanner.close();

                JSONObject jsonObject = new JSONObject(response);
                String secureUrl = jsonObject.getString("secure_url");

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
                prefs.edit().putString("cachedImageUrl", newUrl).apply(); 
            }
        });
    }

    // 🔥 LOGOUT ENGINE
    private void logoutUser() {
        prefs.edit().clear().apply();
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

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, R.anim.slide_out_top_left); 
    }
}