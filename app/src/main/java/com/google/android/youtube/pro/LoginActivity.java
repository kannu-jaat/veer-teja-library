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
    private TextView tvRegister, tvForgotPassword, tvLoginLibName, tvLoginTagline;
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
        
        // AppConfig se text set karna
        tvLoginLibName = findViewById(R.id.tvLoginLibName);
        tvLoginTagline = findViewById(R.id.tvLoginTagline);
        
        if (tvLoginLibName != null) tvLoginLibName.setText(AppConfig.LIBRARY_NAME);
        if (tvLoginTagline != null) tvLoginTagline.setText(AppConfig.TAGLINE);

        tvRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            }
        });

       tvForgotPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Rasta Khul Gaya: Nayi Forgot Password Activity par bhejega
                startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class));
            }
        });

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = etUsername.getText().toString().trim();
                String password = etPassword.getText().toString().trim();

                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(LoginActivity.this, "Please enter all details", Toast.LENGTH_SHORT).show();
                    return;
                }

                btnLogin.setText("LOGGING IN..."); // Feedback on click
                loginUser(username, password);
            }
        });
    }

    private void loginUser(final String username, final String password) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Students").child(username);
        
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                btnLogin.setText("LOGIN"); // Reset text
                if (snapshot.exists()) {
                    String dbPassword = snapshot.child("password").getValue(String.class);
                    String status = snapshot.child("status").getValue(String.class);

                    if (dbPassword != null && dbPassword.equals(password)) {
                        if ("Approved".equals(status)) {
                            prefs.edit().putBoolean("isLoggedIn", true)
                                 .putString("username", username)
                                 .apply();
                            
                            startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
                            finish(); 
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
                btnLogin.setText("LOGIN");
                Toast.makeText(LoginActivity.this, "Database Error", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
