package com.google.android.youtube.pro;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class AttendanceActivity extends Activity {

    private ImageView btnBack;
    private TextView tvMonthYearTitle, tvPresentCount, tvAbsentCount, tvUpcomingCount;
    private GridLayout calendarGrid;
    private String savedUsername;

    // Status Constants
    private static final int STATUS_PRESENT = 1;
    private static final int STATUS_ABSENT = 2;
    private static final int STATUS_UPCOMING = 3;
    private static final int STATUS_EMPTY = 0; // For blank padding cells

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);

        btnBack = findViewById(R.id.btnBack);
        tvMonthYearTitle = findViewById(R.id.tvMonthYearTitle);
        tvPresentCount = findViewById(R.id.tvPresentCount);
        tvAbsentCount = findViewById(R.id.tvAbsentCount);
        tvUpcomingCount = findViewById(R.id.tvUpcomingCount);
        calendarGrid = findViewById(R.id.calendarGrid);

        SharedPreferences prefs = getSharedPreferences("LibraryApp", Context.MODE_PRIVATE);
        savedUsername = prefs.getString("username", "");

        btnBack.setOnClickListener(v -> finish());

        // Start Fetching Data
        fetchCurrentMonthData();
    }

    private void fetchCurrentMonthData() {
        Calendar cal = Calendar.getInstance();
        String currentMonthYear = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(cal.getTime());
        tvMonthYearTitle.setText(currentMonthYear); // E.g., "July 2026"

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Attendance").child(savedUsername);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Set<Integer> presentDates = new HashSet<>();

                // Firebase se is mahine ki saari dates filter karo
                for (DataSnapshot snap : snapshot.getChildren()) {
                    String dateKey = snap.getKey(); // E.g., "12 July 2026"
                    if (dateKey != null && dateKey.endsWith(currentMonthYear)) {
                        try {
                            int day = Integer.parseInt(dateKey.split(" ")[0]); // "12" nikalo
                            presentDates.add(day);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                
                // Data aane ke baad Calendar Generate karo
                buildPremiumCalendar(cal, presentDates);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(AttendanceActivity.this, "Failed to load data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void buildPremiumCalendar(Calendar currentCal, Set<Integer> presentDates) {
        calendarGrid.removeAllViews(); // Purana grid saaf karo

        int todayDay = currentCal.get(Calendar.DAY_OF_MONTH); // Aaj ki taarikh
        int daysInMonth = currentCal.getActualMaximum(Calendar.DAY_OF_MONTH);
        
        // Mahine ka pehla din pata karo (1st taarikh ko Sunday tha ya Monday?)
        currentCal.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = currentCal.get(Calendar.DAY_OF_WEEK); // Sunday=1, Monday=2...
        int emptyCellsBeforeStart = firstDayOfWeek - 1; 

        int presentCount = 0;
        int absentCount = 0;
        int upcomingCount = 0;

        // 1. Khali Dabbe (Empty Cells for padding)
        for (int i = 0; i < emptyCellsBeforeStart; i++) {
            calendarGrid.addView(createCalendarCell("", STATUS_EMPTY));
        }

        // 2. Asli Taarikh Wale Dabbe (Dates)
        for (int day = 1; day <= daysInMonth; day++) {
            boolean isPresent = presentDates.contains(day);
            boolean isPastOrToday = day <= todayDay;

            int status;
            if (isPresent) {
                status = STATUS_PRESENT;
                presentCount++;
            } else if (isPastOrToday) {
                status = STATUS_ABSENT;
                absentCount++;
            } else {
                status = STATUS_UPCOMING;
                upcomingCount++;
            }

            calendarGrid.addView(createCalendarCell(String.valueOf(day), status));
        }

        // 3. Update Summary Cards
        tvPresentCount.setText(String.valueOf(presentCount));
        tvAbsentCount.setText(String.valueOf(absentCount));
        tvUpcomingCount.setText(String.valueOf(upcomingCount));
    }

    // 🔥 THE MAGIC CODE: Creating Premium UI Cells directly from Java
    private View createCalendarCell(String dayText, int status) {
        // Layout Params (Weight = 1, equally distributed)
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0; 
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(6, 6, 6, 6);

        // Main Box (Cell)
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setLayoutParams(params);
        cell.setPadding(12, 12, 12, 16);

        // Date Number Text
        TextView tvDate = new TextView(this);
        tvDate.setText(dayText);
        tvDate.setTextSize(12f);
        tvDate.setTextColor(Color.parseColor("#0F172A"));

        // Status Dot
        View dot = new View(this);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(20, 20);
        dotParams.topMargin = 16;
        dotParams.gravity = Gravity.CENTER_HORIZONTAL;
        dot.setLayoutParams(dotParams);

        // Setup Colors based on Screenshot Exact Matching
        GradientDrawable cellBg = new GradientDrawable();
        cellBg.setShape(GradientDrawable.RECTANGLE);
        cellBg.setCornerRadius(16f); // Gol corners
        
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);

        if (status == STATUS_PRESENT) {
            cellBg.setColor(Color.parseColor("#F0FDF4"));       // Light Green Background
            cellBg.setStroke(2, Color.parseColor("#A7F3D0"));   // Green Border
            dotBg.setColor(Color.parseColor("#10B981"));        // Solid Green Dot
        } 
        else if (status == STATUS_ABSENT) {
            cellBg.setColor(Color.parseColor("#FEF2F2"));       // Light Red Background
            cellBg.setStroke(2, Color.parseColor("#FECACA"));   // Red Border
            dotBg.setColor(Color.parseColor("#EF4444"));        // Solid Red Dot
        } 
        else if (status == STATUS_UPCOMING) {
            cellBg.setColor(Color.parseColor("#FFFFFF"));       // White Background
            cellBg.setStroke(2, Color.parseColor("#E2E8F0"));   // Gray Border
            dotBg.setColor(Color.parseColor("#94A3B8"));        // Gray Dot
        }
        else if (status == STATUS_EMPTY) {
            cellBg.setColor(Color.TRANSPARENT);                 // Invisible for empty blocks
            dotBg.setColor(Color.TRANSPARENT);
            tvDate.setText("");
        }

        cell.setBackground(cellBg);
        dot.setBackground(dotBg);

        cell.addView(tvDate);
        if (status != STATUS_EMPTY) {
            cell.addView(dot);
        }

        return cell;
    }
}
