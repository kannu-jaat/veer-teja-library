package com.google.android.youtube.pro;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;

public class ForgotPasswordActivity extends Activity {

    private EditText etForgotUsername;
    private Button btnResetRequest;
    private TextView tvBackToLoginForgot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.forgot_password);

        etForgotUsername = findViewById(R.id.etForgotUsername);
        btnResetRequest = findViewById(R.id.btnResetRequest);
        tvBackToLoginForgot = findViewById(R.id.tvBackToLoginForgot);

        // Wapas Login Screen Par Jaane Ke Liye
        tvBackToLoginForgot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); 
            }
        });

        // Submit Button Click Listener
        btnResetRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = etForgotUsername.getText().toString().trim();
                if (username.isEmpty()) {
                    Toast.makeText(ForgotPasswordActivity.this, "Please enter your username", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Smart Verification Method
                checkUserAndSendRequest(username);
            }
        });
    }

    private void checkUserAndSendRequest(final String username) {
        btnResetRequest.setText("Verifying...");
        btnResetRequest.setEnabled(false); // Baar baar click hone se bachane ke liye

        // Pehle "Students" node me bache ka username check karenge
        DatabaseReference studentRef = FirebaseDatabase.getInstance().getReference("Students").child(username);

        studentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // ✅ CASE 1: User mil gaya! Ab uski profile details nikalenge
                    String fullName = snapshot.child("fullName").getValue(String.class);
                    String mobile = snapshot.child("mobile").getValue(String.class);
                    String address = snapshot.child("address").getValue(String.class);

                    // Ab Admin ke dekhne ke liye "PasswordRequests" folder me profile details bhejenge
                    DatabaseReference requestRef = FirebaseDatabase.getInstance().getReference("PasswordRequests").child(username);

                    HashMap<String, Object> requestData = new HashMap<>();
                    requestData.put("fullName", fullName != null ? fullName : "N/A");
                    requestData.put("mobile", mobile != null ? mobile : "N/A");
                    requestData.put("address", address != null ? address : "N/A");
                    requestData.put("status", "Pending"); // Admin manual reset karega

                    requestRef.setValue(requestData).addOnCompleteListener(task -> {
                        btnResetRequest.setText("Send Request   →");
                        btnResetRequest.setEnabled(true);
                        
                        if (task.isSuccessful()) {
                            // Bachhe ko confirmation message dikhao
                            Toast.makeText(ForgotPasswordActivity.this, "Request sent! Admin will reset your password.", Toast.LENGTH_LONG).show();
                            finish(); // Kaam khatam, wapas login screen par bhej do
                        } else {
                            Toast.makeText(ForgotPasswordActivity.this, "Failed to send request. Check internet.", Toast.LENGTH_SHORT).show();
                        }
                    });

                } else {
                    // ❌ CASE 2: Username galat hai ya database me nahi hai
                    btnResetRequest.setText("Send Request   →");
                    btnResetRequest.setEnabled(true);
                    
                    // Saaf saaf mana kar do!
                    Toast.makeText(ForgotPasswordActivity.this, "User not found! Please check your username.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                btnResetRequest.setText("Send Request   →");
                btnResetRequest.setEnabled(true);
                Toast.makeText(ForgotPasswordActivity.this, "Database Error. Try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}