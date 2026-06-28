package com.google.android.youtube.pro;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

public class RegisterActivity extends Activity {

    private EditText etUsername, etFullName, etMobile, etAddress, etPassword;
    private TextView tvUsernameStatus, tvPhotoStatus, tvBackToLogin;
    private LinearLayout btnSelectPhoto;
    private Button btnSubmitReg;
    private CheckBox cbTerms;

    private String base64ImageString = "";
    private boolean isUsernameValid = false;
    private static final int PICK_IMAGE_REQUEST = 1;
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable workRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register);

        etUsername = findViewById(R.id.etRegUsername);
        etFullName = findViewById(R.id.etRegFullName);
        etMobile = findViewById(R.id.etRegMobile);
        etAddress = findViewById(R.id.etRegAddress);
        etPassword = findViewById(R.id.etRegPassword);
        
        tvUsernameStatus = findViewById(R.id.tvUsernameStatus);
        tvPhotoStatus = findViewById(R.id.tvPhotoStatus);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);
        
        btnSelectPhoto = findViewById(R.id.btnSelectPhoto);
        btnSubmitReg = findViewById(R.id.btnSubmitReg);
        cbTerms = findViewById(R.id.cbTerms);

        tvBackToLogin.setOnClickListener(v -> finish());

        // 1. REALTIME USERNAME CHECK
        etUsername.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String username = s.toString().trim();
                tvUsernameStatus.setVisibility(View.VISIBLE);

                if (workRunnable != null) handler.removeCallbacks(workRunnable);

                if (username.length() < 4) {
                    setUsernameStatus("Too short (min 4 chars)", "#E94560", false);
                    return;
                }
                if (!username.matches("^[a-zA-Z0-9_]+$")) {
                    setUsernameStatus("Only letters, numbers & _ allowed", "#E94560", false);
                    return;
                }

                setUsernameStatus("Checking availability...", "#94A3B8", false);

                // Firebase call after 0.8s to prevent spamming
                workRunnable = () -> checkUsernameInFirebase(username);
                handler.postDelayed(workRunnable, 800);
            }
        });

        // 2. SELECT PHOTO
        btnSelectPhoto.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select Photo"), PICK_IMAGE_REQUEST);
        });

        // 3. SUBMIT REGISTRATION
        btnSubmitReg.setOnClickListener(v -> {
            if (!isUsernameValid) {
                showCustomAlert("Please choose a valid & available username.", true);
                return;
            }
            if (!cbTerms.isChecked()) {
                showCustomAlert("Please accept Terms & Privacy Policy.", true);
                return;
            }
            if (base64ImageString.isEmpty()) {
                showCustomAlert("Please select a profile photo.", true);
                return;
            }
            if (etFullName.getText().toString().isEmpty() || etMobile.getText().toString().isEmpty() || etPassword.getText().toString().isEmpty()) {
                showCustomAlert("Please fill all details.", true);
                return;
            }

            btnSubmitReg.setText("Uploading Photo...");
            btnSubmitReg.setEnabled(false);
            uploadImageToCloudinary();
        });
    }

    private void setUsernameStatus(String msg, String color, boolean isValid) {
        tvUsernameStatus.setText(msg);
        tvUsernameStatus.setTextColor(Color.parseColor(color));
        isUsernameValid = isValid;
    }

    private void checkUsernameInFirebase(String username) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Students").child(username);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    setUsernameStatus("Username already taken ❌", "#E94560", false);
                } else {
                    setUsernameStatus("Username available ✅", "#4CAF50", true);
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                Uri imageUri = data.getData();
                InputStream imageStream = getContentResolver().openInputStream(imageUri);
                Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
                
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                selectedImage.compress(Bitmap.CompressFormat.JPEG, 50, baos); // Reduced quality for fast upload
                byte[] imageBytes = baos.toByteArray();
                base64ImageString = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
                
                tvPhotoStatus.setText("Photo Selected ✅");
                tvPhotoStatus.setTextColor(Color.parseColor("#4CAF50"));
            } catch (Exception e) {
                showCustomAlert("Failed to process image.", true);
            }
        }
    }

    // 4. UPLOAD TO CLOUDINARY (Background Thread)
    private void uploadImageToCloudinary() {
        new Thread(() -> {
            try {
                URL url = new URL("https://api.cloudinary.com/v1_1/" + AppConfig.CLOUDINARY_CLOUD_NAME + "/image/upload");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                // Cloudinary Config
                JSONObject jsonParam = new JSONObject();
                jsonParam.put("upload_preset", AppConfig.CLOUDINARY_UPLOAD_PRESET);
                jsonParam.put("file", "data:image/jpeg;base64," + base64ImageString);
                jsonParam.put("folder", AppConfig.LIBRARY_NAME + "/Profiles");

                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                os.writeBytes(jsonParam.toString());
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String inputLine;
                    StringBuilder response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) response.append(inputLine);
                    in.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String secureUrl = jsonResponse.getString("secure_url");

                    // Cloudinary se URL mil gaya, ab Firebase bhejenge
                    runOnUiThread(() -> saveStudentToFirebase(secureUrl));
                } else {
                    runOnUiThread(() -> {
                        btnSubmitReg.setText("Register   →");
                        btnSubmitReg.setEnabled(true);
                        showCustomAlert("Cloudinary Upload Failed. Status: " + responseCode, true);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    btnSubmitReg.setText("Register   →");
                    btnSubmitReg.setEnabled(true);
                    showCustomAlert("Upload Error: Check Network.", true);
                });
            }
        }).start();
    }

    // 5. SAVE FINAL DATA TO FIREBASE
    private void saveStudentToFirebase(String photoUrl) {
        btnSubmitReg.setText("Saving Data...");
        String username = etUsername.getText().toString().trim();
        
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Students").child(username);
        
        HashMap<String, Object> data = new HashMap<>();
        data.put("fullName", etFullName.getText().toString().trim());
        data.put("mobile", etMobile.getText().toString().trim());
        data.put("address", etAddress.getText().toString().trim());
        data.put("password", etPassword.getText().toString().trim());
        data.put("photoUrl", photoUrl); // Base64 ki jagah ab sidha Fast URL!
        data.put("status", "Pending");
        data.put("timestamp", System.currentTimeMillis());

        ref.setValue(data).addOnCompleteListener(task -> {
            btnSubmitReg.setText("Register   →");
            btnSubmitReg.setEnabled(true);
            if (task.isSuccessful()) {
                showCustomAlert("Registered Successfully! Pending Admin Approval.", false);
                handler.postDelayed(this::finish, 2000); // 2 second baad screen band
            } else {
                showCustomAlert("Firebase Error: Failed to save data.", true);
            }
        });
    }

    // CUSTOM ALERT REUSED
    private void showCustomAlert(String message, boolean isError) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.custom_alert);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.TOP);
        }
        
        TextView tvMsg = dialog.findViewById(R.id.tvAlertMessage);
        LinearLayout bg = dialog.findViewById(R.id.alertBackground);
        tvMsg.setText(message);
        
        if (!isError) bg.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50")));
        dialog.findViewById(R.id.tvAlertClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}