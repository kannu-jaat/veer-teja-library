package com.google.android.youtube.pro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

public class LoginActivity extends Activity {

    private EditText etUsername, etPassword;
    private Button btnLogin;
    private TextView tvRegister, tvForgotPassword;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);

        prefs = getSharedPreferences("LibraryApp", Context.MODE_PRIVATE);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);

        // 🚀 forword to registration screen 
        tvRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            }
        });

        // Forgot Password Logic
        tvForgotPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(LoginActivity.this, "Request sent to Admin for Password Reset.", Toast.LENGTH_LONG).show();
            }
        });

        // Login Check Logic
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = etUsername.getText().toString().trim();
                String password = etPassword.getText().toString().trim();

                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(LoginActivity.this, "Please enter all details", Toast.LENGTH_SHORT).show();
                    return;
                }

                loginUser(username, password);
            }
        });
    }

    private void loginUser(final String username, final String password) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Students").child(username);
        
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String dbPassword = snapshot.child("password").getValue(String.class);
                    String status = snapshot.child("status").getValue(String.class);

                    if (dbPassword != null && dbPassword.equals(password)) {
                        if ("Approved".equals(status)) {
                            // Login Success - Save to Device
                            prefs.edit().putBoolean("isLoggedIn", true)
                                 .putString("username", username)
                                 .apply();
                            
                            Toast.makeText(LoginActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                            
                            // 🚀 RASTA KHUL GAYA: Login Pass Hone Par Dashboard Par Bhejna
                            startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
                            finish(); // Login screen ko band kar do taaki back aane par dobara na dikhe
                        } else {
                            Toast.makeText(LoginActivity.this, "Account Pending Approval from Admin.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(LoginActivity.this, "Wrong Password!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(LoginActivity.this, "Student not found. Register first.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(LoginActivity.this, "Database Error", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
