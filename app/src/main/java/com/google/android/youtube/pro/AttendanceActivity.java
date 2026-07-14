package com.google.android.youtube.pro;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.CalendarView;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AttendanceActivity extends Activity {

    private CalendarView calendarView;
    private TextView tvSelectedDate, tvDayStatus, tvDayTime;
    private ImageView btnBack;
    private String savedUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);

        calendarView = findViewById(R.id.calendarView);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        tvDayStatus = findViewById(R.id.tvDayStatus);
        tvDayTime = findViewById(R.id.tvDayTime);
        btnBack = findViewById(R.id.btnBack);

        SharedPreferences prefs = getSharedPreferences("LibraryApp", Context.MODE_PRIVATE);
        savedUsername = prefs.getString("username", "");

        btnBack.setOnClickListener(v -> finish());

        // Jab user calendar par koi date click karega
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            Calendar c = Calendar.getInstance();
            c.set(year, month, dayOfMonth);
            
            // Generate exact string used in our Firebase structure ("12 July 2026")
            String formattedDate = new SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH).format(c.getTime());
            tvSelectedDate.setText(formattedDate);
            
            checkStatusForDate(formattedDate);
        });
    }

    private void checkStatusForDate(String dateStr) {
        tvDayStatus.setText("Checking...");
        tvDayStatus.setTextColor(Color.parseColor("#F59E0B")); // Orange
        tvDayTime.setText("");

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Attendance").child(savedUsername).child(dateStr);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String time = snapshot.child("checkIn").getValue(String.class);
                    tvDayStatus.setText("Present ✓");
                    tvDayStatus.setTextColor(Color.parseColor("#10B981")); // Green
                    tvDayTime.setText("Check-in: " + time);
                } else {
                    tvDayStatus.setText("Not Marked");
                    tvDayStatus.setTextColor(Color.parseColor("#EF4444")); // Red
                    tvDayTime.setText("");
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                tvDayStatus.setText("Error loading data");
            }
        });
    }
}
