package com.google.android.youtube.pro;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

public class RegisterActivity extends Activity {

    private EditText etUsername, etFullName, etMobile, etAddress, etPassword, etRegConfirmPassword;
    private TextView tvUsernameStatus, tvPhotoStatus, tvIDStatus, tvBackToLogin, tvTogglePassword, tvInternetWarning;
    private ImageView ivRegAvatar;
    private LinearLayout btnSelectPhoto, btnSelectID;
    private Button btnSubmitReg;
    private ProgressBar pbSubmit;
    private CheckBox cbTerms;

    private byte[] profileImageBytes = null;
    private byte[] idImageBytes = null;
    
    private boolean isUsernameValid = false;
    private boolean isPasswordVisible = false;
    
    private static final int PICK_PROFILE_REQ = 1;
    private static final int PICK_ID_REQ = 2;
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable workRunnable;

    interface UploadCallback {
        void onSuccess(String url);
        void onFailed(String error);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register);

        tvInternetWarning = findViewById(R.id.tvInternetWarning);
        ivRegAvatar = findViewById(R.id.ivRegAvatar);
        pbSubmit = findViewById(R.id.pbSubmit);
        
        etUsername = findViewById(R.id.etRegUsername);
        etFullName = findViewById(R.id.etRegFullName);
        etMobile = findViewById(R.id.etRegMobile);
        etAddress = findViewById(R.id.etRegAddress);
        etPassword = findViewById(R.id.etRegPassword);
        etRegConfirmPassword = findViewById(R.id.etRegConfirmPassword);
        
        tvUsernameStatus = findViewById(R.id.tvUsernameStatus);
        tvPhotoStatus = findViewById(R.id.tvPhotoStatus);
        tvIDStatus = findViewById(R.id.tvIDStatus);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);
        tvTogglePassword = findViewById(R.id.tvTogglePassword);
        
        btnSelectPhoto = findViewById(R.id.btnSelectPhoto);
        btnSelectID = findViewById(R.id.btnSelectID);
        btnSubmitReg = findViewById(R.id.btnSubmitReg);
        cbTerms = findViewById(R.id.cbTerms);

        tvBackToLogin.setOnClickListener(v -> finish());

        etPassword.setFilters(new InputFilter[]{new InputFilter.LengthFilter(15)});
        etRegConfirmPassword.setFilters(new InputFilter[]{new InputFilter.LengthFilter(15)});
        etMobile.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});

        tvTogglePassword.setOnClickListener(v -> {
            if (isPasswordVisible) {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                tvTogglePassword.setAlpha(0.5f);
            } else {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                tvTogglePassword.setAlpha(1.0f);
            }
            etPassword.setSelection(etPassword.length());
            isPasswordVisible = !isPasswordVisible;
        });

        etUsername.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String username = s.toString().trim().toLowerCase();
                if (!s.toString().equals(username)) {
                    etUsername.setText(username);
                    etUsername.setSelection(username.length());
                    return;
                }
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
                workRunnable = () -> checkUsernameInFirebase(username);
                handler.postDelayed(workRunnable, 800);
            }
        });

        btnSelectPhoto.setOnClickListener(v -> openGallery(PICK_PROFILE_REQ));
        btnSelectID.setOnClickListener(v -> openGallery(PICK_ID_REQ));

        btnSubmitReg.setOnClickListener(v -> {
            // 🔥 CHECK INTERNET FIRST
            if (!isNetworkAvailable()) {
                showInternetWarning();
                return;
            }

            String pass = etPassword.getText().toString();
            String confirmPass = etRegConfirmPassword.getText().toString();
            String mobile = etMobile.getText().toString().trim();

            if (!isUsernameValid) { showCustomAlert("Please choose a valid & available username.", true); return; }
            if (!mobile.matches("^[6-9]\\d{9}$")) { showCustomAlert("Please enter a valid 10-digit mobile number.", true); return; }
            if (pass.length() < 5) { showCustomAlert("Password must be between 5 to 15 characters.", true); return; }
            if (!pass.equals(confirmPass)) { showCustomAlert("Passwords do not match!", true); return; }
            if (!cbTerms.isChecked()) { showCustomAlert("Please accept Terms & Privacy Policy.", true); return; }
            if (profileImageBytes == null) { showCustomAlert("Please select a profile photo.", true); return; }
            if (etFullName.getText().toString().isEmpty() || etAddress.getText().toString().isEmpty()) {
                showCustomAlert("Please fill all details.", true); return;
            }

            setLoadingState(true, "Uploading Photo...");
            startUploadProcess();
        });
    }

    // 🔥 SMART DOWN-SCALER FOR 108MP PHOTOS (Avoids OOM Crash)
    private Bitmap getResizedBitmapSafe(Uri imageUri, int maxSize) {
        try {
            InputStream input = getContentResolver().openInputStream(imageUri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, options);
            input.close();

            int outWidth = options.outWidth;
            int outHeight = options.outHeight;
            int inSampleSize = 1;

            if (outHeight > maxSize || outWidth > maxSize) {
                final int halfHeight = outHeight / 2;
                final int halfWidth = outWidth / 2;
                while ((halfHeight / inSampleSize) >= maxSize && (halfWidth / inSampleSize) >= maxSize) {
                    inSampleSize *= 2;
                }
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;
            input = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            input.close();
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            
            // Resize image safely to max 800px
            Bitmap safeBitmap = getResizedBitmapSafe(imageUri, 800);
            
            if (safeBitmap == null) {
                showCustomAlert("Failed to process image.", true);
                return;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            safeBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos); 
            byte[] finalBytes = baos.toByteArray();
            
            if (requestCode == PICK_PROFILE_REQ) {
                profileImageBytes = finalBytes;
                ivRegAvatar.setImageBitmap(safeBitmap); // LIVE PREVIEW SET!
                ivRegAvatar.setPadding(0, 0, 0, 0); // Remove padding so image fills circle
                tvPhotoStatus.setText("Profile Selected ✅");
                tvPhotoStatus.setTextColor(Color.parseColor("#4CAF50"));
            } else if (requestCode == PICK_ID_REQ) {
                idImageBytes = finalBytes;
                tvIDStatus.setText("ID Selected ✅");
                tvIDStatus.setTextColor(Color.parseColor("#4CAF50"));
            }
        }
    }

    // CHECK INTERNET STATUS
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    // SHOW TOP WARNING BAR
    private void showInternetWarning() {
        tvInternetWarning.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> tvInternetWarning.setVisibility(View.GONE), 3000); // 3 sec baad chup jayega
    }

    // LOADING INDICATOR LOGIC
    private void setLoadingState(boolean isLoading, String text) {
        if (isLoading) {
            btnSubmitReg.setText(text);
            btnSubmitReg.setEnabled(false);
            pbSubmit.setVisibility(View.VISIBLE);
        } else {
            btnSubmitReg.setText(text);
            btnSubmitReg.setEnabled(true);
            pbSubmit.setVisibility(View.GONE);
        }
    }

    private void openGallery(int requestCode) {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Image"), requestCode);
    }

    private void setUsernameStatus(String msg, String color, boolean isValid) {
        tvUsernameStatus.setText(msg);
        tvUsernameStatus.setTextColor(Color.parseColor(color));
        isUsernameValid = isValid;
    }

    private void checkUsernameInFirebase(String username) {
        if (!isNetworkAvailable()) return;
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Students").child(username);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) setUsernameStatus("Username already taken ❌", "#E94560", false);
                else setUsernameStatus("Username available ✅", "#4CAF50", true);
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void startUploadProcess() {
        String username = etUsername.getText().toString().trim().toLowerCase();
        String safeFolder = AppConfig.LIBRARY_NAME.replace(" ", "_");

        uploadSingleImage(profileImageBytes, safeFolder + "_Profiles", username, new UploadCallback() {
            @Override
            public void onSuccess(String profileUrl) {
                if (idImageBytes != null) {
                    runOnUiThread(() -> setLoadingState(true, "Uploading ID..."));
                    uploadSingleImage(idImageBytes, safeFolder + "_ID_Proofs", username + "_ID", new UploadCallback() {
                        @Override
                        public void onSuccess(String idUrl) {
                            runOnUiThread(() -> saveStudentToFirebase(profileUrl, idUrl));
                        }
                        @Override
                        public void onFailed(String error) {
                            runOnUiThread(() -> {
                                setLoadingState(false, "Register");
                                showCustomAlert("ID Upload Failed: " + error, true);
                            });
                        }
                    });
                } else {
                    runOnUiThread(() -> saveStudentToFirebase(profileUrl, ""));
                }
            }
            @Override
            public void onFailed(String error) {
                runOnUiThread(() -> {
                    setLoadingState(false, "Register");
                    showCustomAlert("Profile Upload Failed: " + error, true);
                });
            }
        });
    }

    private void uploadSingleImage(byte[] imageBytes, String folderName, String publicId, UploadCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String boundary = "Boundary-" + System.currentTimeMillis();
                URL url = new URL("https://api.cloudinary.com/v1_1/" + AppConfig.CLOUDINARY_CLOUD_NAME + "/image/upload");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                writeMultipartParam(os, boundary, "upload_preset", AppConfig.CLOUDINARY_UPLOAD_PRESET);
                writeMultipartParam(os, boundary, "folder", folderName);
                writeMultipartParam(os, boundary, "public_id", publicId);
                
                os.write(("--" + boundary + "\r\n").getBytes());
                os.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + publicId + ".jpg\"\r\n").getBytes());
                os.write(("Content-Type: image/jpeg\r\n\r\n").getBytes());
                os.write(imageBytes);
                os.write(("\r\n").getBytes());
                os.write(("--" + boundary + "--\r\n").getBytes());
                os.flush(); os.close();

                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String inputLine; StringBuilder resp = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) resp.append(inputLine);
                    in.close();
                    String secureUrl = new JSONObject(resp.toString()).getString("secure_url");
                    callback.onSuccess(secureUrl);
                } else {
                    callback.onFailed("Status " + code);
                }
            } catch (Exception e) {
                callback.onFailed("Network/Server Error");
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void writeMultipartParam(OutputStream os, String boundary, String name, String value) throws Exception {
        os.write(("--" + boundary + "\r\n").getBytes());
        os.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes());
        os.write((value + "\r\n").getBytes());
    }

    private void saveStudentToFirebase(String profileUrl, String idUrl) {
        runOnUiThread(() -> setLoadingState(true, "Saving Data..."));
        String username = etUsername.getText().toString().trim().toLowerCase();
        
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Students").child(username);
        
        HashMap<String, Object> data = new HashMap<>();
        data.put("fullName", etFullName.getText().toString().trim());
        data.put("mobile", etMobile.getText().toString().trim());
        data.put("address", etAddress.getText().toString().trim());
        data.put("password", etPassword.getText().toString().trim());
        data.put("photoUrl", profileUrl);
        data.put("idProofUrl", idUrl);
        data.put("status", "Pending");

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MM-yyyy__HH-mm", java.util.Locale.getDefault());
        data.put("registrationTime", sdf.format(new java.util.Date()));

        ref.setValue(data).addOnCompleteListener(task -> {
            setLoadingState(false, "Register");
            if (task.isSuccessful()) {
                showCustomAlert("Registered Successfully! Pending Admin Approval.", false);
                handler.postDelayed(this::finish, 2000); 
            } else {
                showCustomAlert("Firebase Error: Failed to save data.", true);
            }
        });
    }

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