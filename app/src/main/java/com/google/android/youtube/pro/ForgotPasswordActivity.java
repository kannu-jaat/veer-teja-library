package com.google.android.youtube.pro;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

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

        tvBackToLoginForgot.setOnClickListener(v -> finish());

        btnResetRequest.setOnClickListener(v -> {
            String username = etForgotUsername.getText().toString().trim();
            if (username.isEmpty()) {
                showCustomAlert("Please enter your username", true);
                return;
            }
            checkAndSendRequest(username);
        });
    }

    private void checkAndSendRequest(final String username) {
        btnResetRequest.setText("Checking...");
        btnResetRequest.setEnabled(false);

        DatabaseReference requestsRef = FirebaseDatabase.getInstance().getReference("PasswordRequests").child(username);

        // 1. Pehle check karein ki request pehle se toh nahi aayi
        requestsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists() && "Pending".equals(snapshot.child("status").getValue(String.class))) {
                    // Agar pehle se Pending request hai
                    btnResetRequest.setText("Send Request   →");
                    btnResetRequest.setEnabled(true);
                    showCustomAlert("Request is under verification. Get it verified from Admin", true);
                } else {
                    // 2. Agar nahi hai, toh check karein ki User Database me hai ya nahi
                    DatabaseReference studentRef = FirebaseDatabase.getInstance().getReference("Students").child(username);
                    studentRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot studentSnap) {
                            if (studentSnap.exists()) {
                                // User mil gaya! Sirf username aur status submit karein
                                HashMap<String, Object> reqData = new HashMap<>();
                                reqData.put("status", "Pending");
                                reqData.put("timestamp", System.currentTimeMillis()); // Kab request ki gayi
                                
                                requestsRef.setValue(reqData).addOnCompleteListener(task -> {
                                    btnResetRequest.setText("Send Request   →");
                                    btnResetRequest.setEnabled(true);
                                    if (task.isSuccessful()) {
                                        showCustomAlert("Request sent successfully! Admin will update it.", false);
                                    } else {
                                        showCustomAlert("Failed to send request. Check internet.", true);
                                    }
                                });
                            } else {
                                // User hi nahi hai database me
                                btnResetRequest.setText("Send Request   →");
                                btnResetRequest.setEnabled(true);
                                showCustomAlert("User not found! Please check your username.", true);
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            resetButton();
                        }
                    });
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                resetButton();
            }
        });
    }

    private void resetButton() {
        btnResetRequest.setText("Send Request   →");
        btnResetRequest.setEnabled(true);
        showCustomAlert("Database Error. Try again.", true);
    }

    // Custom Toast with ❌ Button (Top Corner me chamkega)
    private void showCustomAlert(String message, boolean isError) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.custom_alert);
        
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.TOP); // Upar ki taraf dikhega
        }

        TextView tvMsg = dialog.findViewById(R.id.tvAlertMessage);
        TextView tvClose = dialog.findViewById(R.id.tvAlertClose);
        LinearLayout bg = dialog.findViewById(R.id.alertBackground);

        tvMsg.setText(message);

        // Agar Error nahi hai (Success hai), toh Green background kar do, warna Red hi rahega
        if (!isError) {
            bg.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50")));
        }

        // ❌ button par click karte hi popup band hoga
        tvClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}