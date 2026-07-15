package com.google.android.youtube.pro;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
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

    private ImageView btnBack, btnPrevMonth, btnNextMonth;
    private TextView tvMonthYearTitle, tvPresentCount, tvAbsentCount, tvUpcomingCount;
    private GridLayout calendarGrid;
    private String savedUsername;
    private Calendar currentCal; // Mahina track karne ke liye

    // Status Constants
    private static final int STATUS_PRESENT = 1;
    private static final int STATUS_ABSENT = 2;
    private static final int STATUS_UPCOMING = 3;
    private static final int STATUS_EMPTY = 0; 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);

        btnBack = findViewById(R.id.btnBack);
        btnPrevMonth = findViewById(R.id.btnPrevMonth);
        btnNextMonth = findViewById(R.id.btnNextMonth);
        
        tvMonthYearTitle = findViewById(R.id.tvMonthYearTitle);
        tvPresentCount = findViewById(R.id.tvPresentCount);
        tvAbsentCount = findViewById(R.id.tvAbsentCount);
        tvUpcomingCount = findViewById(R.id.tvUpcomingCount);
        calendarGrid = findViewById(R.id.calendarGrid);

        SharedPreferences prefs = getSharedPreferences("LibraryApp", Context.MODE_PRIVATE);
        savedUsername = prefs.getString("username", "");

        btnBack.setOnClickListener(v -> finish());

        // Calendar initialize karna (1st day of current month)
        currentCal = Calendar.getInstance();
        currentCal.set(Calendar.DAY_OF_MONTH, 1);

        // Previous/Next Buttons Logic
        btnPrevMonth.setOnClickListener(v -> {
            currentCal.add(Calendar.MONTH, -1);
            fetchCurrentMonthData();
        });

        btnNextMonth.setOnClickListener(v -> {
            currentCal.add(Calendar.MONTH, 1);
            fetchCurrentMonthData();
        });

        fetchCurrentMonthData();
    }

    private void fetchCurrentMonthData() {
        String currentMonthYear = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(currentCal.getTime());
        tvMonthYearTitle.setText(currentMonthYear); 
        
        // Data aane tak dabbe saaf kar do
        calendarGrid.removeAllViews();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Attendance").child(savedUsername);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Set<Integer> presentDates = new HashSet<>();

                for (DataSnapshot snap : snapshot.getChildren()) {
                    String dateKey = snap.getKey(); 
                    if (dateKey != null && dateKey.endsWith(currentMonthYear)) {
                        try {
                            int day = Integer.parseInt(dateKey.split(" ")[0]); 
                            presentDates.add(day);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                buildPremiumCalendar(presentDates);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(AttendanceActivity.this, "Failed to load data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void buildPremiumCalendar(Set<Integer> presentDates) {
        calendarGrid.removeAllViews(); 

        Calendar realToday = Calendar.getInstance();
        int daysInMonth = currentCal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int firstDayOfWeek = currentCal.get(Calendar.DAY_OF_WEEK); 
        int emptyCellsBeforeStart = firstDayOfWeek - 1; 

        // Check karo ki bacha past ke mahine me hai ya future me
        boolean isCurrentMonth = (currentCal.get(Calendar.YEAR) == realToday.get(Calendar.YEAR)) &&
                                 (currentCal.get(Calendar.MONTH) == realToday.get(Calendar.MONTH));
        boolean isPastMonth = (currentCal.get(Calendar.YEAR) < realToday.get(Calendar.YEAR)) || 
                              (currentCal.get(Calendar.YEAR) == realToday.get(Calendar.YEAR) && currentCal.get(Calendar.MONTH) < realToday.get(Calendar.MONTH));

        int presentCount = 0;
        int absentCount = 0;
        int upcomingCount = 0;

        for (int i = 0; i < emptyCellsBeforeStart; i++) {
            calendarGrid.addView(createCalendarCell("", STATUS_EMPTY));
        }

        for (int day = 1; day <= daysInMonth; day++) {
            boolean isPresent = presentDates.contains(day);
            
            // Logic for Absent vs Upcoming
            boolean isPastOrToday = false;
            if (isPastMonth) {
                isPastOrToday = true;
            } else if (isCurrentMonth) {
                isPastOrToday = day <= realToday.get(Calendar.DAY_OF_MONTH);
            }

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

        tvPresentCount.setText(String.valueOf(presentCount));
        tvAbsentCount.setText(String.valueOf(absentCount));
        tvUpcomingCount.setText(String.valueOf(upcomingCount));
    }

    private View createCalendarCell(String dayText, int status) {
        // UI MAGIC: Har dabbe ki fix height set karna taaki wo bada lage
        int cellHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 60, getResources().getDisplayMetrics());
        
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0; 
        params.height = cellHeightPx; // Ab dabba lamba aur premium lagega
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        // Margin kam kiya taaki width zyada mil sake
        params.setMargins(4, 6, 4, 6); 

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setLayoutParams(params);
        cell.setPadding(8, 12, 8, 8); // Padding update ki hai

        TextView tvDate = new TextView(this);
        tvDate.setText(dayText);
        tvDate.setTextSize(13f);
        tvDate.setTextColor(Color.parseColor("#0F172A"));

        View dot = new View(this);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(16, 16); // Dot thodi choti ki premium look ke liye
        dotParams.topMargin = 12;
        dotParams.gravity = Gravity.CENTER_HORIZONTAL;
        dot.setLayoutParams(dotParams);

        GradientDrawable cellBg = new GradientDrawable();
        cellBg.setShape(GradientDrawable.RECTANGLE);
        cellBg.setCornerRadius(14f); 
        
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);

        if (status == STATUS_PRESENT) {
            cellBg.setColor(Color.parseColor("#F0FDF4"));      
            cellBg.setStroke(2, Color.parseColor("#A7F3D0"));  
            dotBg.setColor(Color.parseColor("#10B981"));       
        } 
        else if (status == STATUS_ABSENT) {
            cellBg.setColor(Color.parseColor("#FEF2F2"));      
            cellBg.setStroke(2, Color.parseColor("#FECACA"));  
            dotBg.setColor(Color.parseColor("#EF4444"));       
        } 
        else if (status == STATUS_UPCOMING) {
            cellBg.setColor(Color.parseColor("#FFFFFF"));      
            cellBg.setStroke(2, Color.parseColor("#E2E8F0"));  
            dotBg.setColor(Color.parseColor("#94A3B8"));       
        }
        else if (status == STATUS_EMPTY) {
            cellBg.setColor(Color.TRANSPARENT);                
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
