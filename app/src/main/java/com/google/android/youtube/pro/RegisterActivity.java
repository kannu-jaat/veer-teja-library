package com.google.android.youtube.pro;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;

public class RegisterActivity extends Activity {

    private EditText etFullName, etMobile, etAddress, etUsername, etPassword;
    private Button btnSelectPhoto, btnSubmitReg;
    private TextView tvBackToLogin;
    
    private String base64ImageString = ""; // Photo yahan save hogi
    private static final int PICK_IMAGE_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register);

        etFullName = findViewById(R.id.etRegFullName);
        etMobile = findViewById(R.id.etRegMobile);
        etAddress = findViewById(R.id.etRegAddress);
        etUsername = findViewById(R.id.etRegUsername);
        etPassword = findViewById(R.id.etRegPassword);
        btnSelectPhoto = findViewById(R.id.btnSelectPhoto);
        btnSubmitReg = findViewById(R.id.btnSubmitReg);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);

        // Photo Select Karne Ka Button
        btnSelectPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select Photo"), PICK_IMAGE_REQUEST);
            }
        });

        // Wapas Login Par Jaane Ka Button
        tvBackToLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Data Submit Karne Ka Button
        btnSubmitReg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerStudent();
            }
        });
    }

    // Photo Select Hone Ke Baad Base64 Me Convert Karna
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            try {
                InputStream imageStream = getContentResolver().openInputStream(imageUri);
                Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
                
                // Photo ka size chhota karna taaki Firebase jaldi save kare
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                selectedImage.compress(Bitmap.CompressFormat.JPEG, 40, baos);
                byte[] imageBytes = baos.toByteArray();
                
                base64ImageString = Base64.encodeToString(imageBytes, Base64.DEFAULT);
                btnSelectPhoto.setText("PHOTO SELECTED ✅");
                btnSelectPhoto.setBackgroundColor(0xFF4CAF50); // Green color
                
            } catch (Exception e) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Firebase Me Student Data Bhejna
    private void registerStudent() {
        String fullName = etFullName.getText().toString().trim();
        String mobile = etMobile.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (fullName.isEmpty() || mobile.isEmpty() || address.isEmpty() || username.isEmpty() || password.isEmpty() || base64ImageString.isEmpty()) {
            Toast.makeText(this, "Please fill all details and select a photo", Toast.LENGTH_LONG).show();
            return;
        }

        // Firebase Connection
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Students").child(username);

        HashMap<String, Object> studentData = new HashMap<>();
        studentData.put("fullName", fullName);
        studentData.put("mobile", mobile);
        studentData.put("address", address);
        studentData.put("password", password);
        studentData.put("photoBase64", base64ImageString);
        studentData.put("status", "Pending"); // Admin manually Approve karega

        ref.setValue(studentData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(RegisterActivity.this, "Registration Sent! Wait for Admin Approval.", Toast.LENGTH_LONG).show();
                finish(); // Wapas Login screen par bhej dega
            } else {
                Toast.makeText(RegisterActivity.this, "Failed to Register. Check Internet.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
