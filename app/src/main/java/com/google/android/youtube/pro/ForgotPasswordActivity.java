package com.google.android.youtube.pro;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

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

        // Firebase me Request Bhejne Ke Liye
        btnResetRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = etForgotUsername.getText().toString().trim();
                if (username.isEmpty()) {
                    Toast.makeText(ForgotPasswordActivity.this, "Please enter your username", Toast.LENGTH_SHORT).show();
                    return;
                }

                sendResetRequest(username);
            }
        });
    }

    private void sendResetRequest(String username) {
        btnResetRequest.setText("Sending...");
        
        // Firebase me "PasswordRequests" naam ka naya folder banega
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("PasswordRequests").child(username);
        
        ref.child("status").setValue("Pending").addOnCompleteListener(task -> {
            btnResetRequest.setText("Send Request   →");
            if (task.isSuccessful()) {
                Toast.makeText(ForgotPasswordActivity.this, "Request sent! Admin will reset your password.", Toast.LENGTH_LONG).show();
                finish(); // Wapas login par bhej do
            } else {
                Toast.makeText(ForgotPasswordActivity.this, "Failed to send request. Check internet.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}