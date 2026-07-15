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
import androidx.cardview.widget.CardView;

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
    private CardView btnPrevMonth, btnNextMonth;
    private String savedUsername;
    
    // Naya global calendar jo month track karega
    private Calendar displayCalendar;

    private static final int STATUS_PRESENT = 1;
    private static final int STATUS_ABSENT = 2;
    private static final int STATUS_UPCOMING = 3;
    private static final int STATUS_EMPTY = 0;

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
        
        btnPrevMonth = findViewById(R.id.btnPrevMonth);
        btnNextMonth = findViewById(R.id.btnNextMonth);

        SharedPreferences prefs = getSharedPreferences("LibraryApp", Context.MODE_PRIVATE);
        savedUsername = prefs.getString("username", "");
        
        displayCalendar = Calendar.getInstance(); // Aaj ki date set karega default

        btnBack.setOnClickListener(v -> finish());
        
        // Month Navigation Listeners
        btnPrevMonth.setOnClickListener(v -> {
            displayCalendar.add(Calendar.MONTH, -1); // Ek mahina pichhe
            fetchMonthData();
        });

        btnNextMonth.setOnClickListener(v -> {
            displayCalendar.add(Calendar.MONTH, 1); // Ek mahina aage
            fetchMonthData();
        });

        // Pehli baar app khulte hi fetch
        fetchMonthData();
    }

    private void fetchMonthData() {
        // "July 2026" wala format
        String monthYearStr = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(displayCalendar.getTime());
        tvMonthYearTitle.setText(monthYearStr); 
        
        calendarGrid.removeAllViews(); // Puraana data clean karo load hone se pehle

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Attendance").child(savedUsername);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Set<Integer> presentDates = new HashSet<>();

                for (DataSnapshot snap : snapshot.getChildren()) {
                    String dateKey = snap.getKey();
                    if (dateKey != null && dateKey.endsWith(monthYearStr)) {
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
        
        // Logic check karne ke liye original 'Today' ka reference chahiye (Taaki upcoming sahi dikhe)
        Calendar realToday = Calendar.getInstance();

        int daysInMonth = displayCalendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        
        // Month starting day check karo
        Calendar tempCal = (Calendar) displayCalendar.clone();
        tempCal.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK); 
        int emptyCellsBeforeStart = firstDayOfWeek - 1; 

        int presentCount = 0;
        int absentCount = 0;
        int upcomingCount = 0;

        // 1. Khali Dabbe (Empty Start Padding)
        for (int i = 0; i < emptyCellsBeforeStart; i++) {
            calendarGrid.addView(createCalendarCell("", STATUS_EMPTY));
        }

        // 2. Asli Dabbe
        for (int day = 1; day <= daysInMonth; day++) {
            boolean isPresent = presentDates.contains(day);
            
            // Check agar is mahine ka din, asli aaj ke din se chhota/barabar hai (ya purane mahine ka hai)
            boolean isPastOrToday;
            if (displayCalendar.get(Calendar.YEAR) < realToday.get(Calendar.YEAR)) {
                isPastOrToday = true;
            } else if (displayCalendar.get(Calendar.YEAR) > realToday.get(Calendar.YEAR)) {
                isPastOrToday = false;
            } else {
                if (displayCalendar.get(Calendar.MONTH) < realToday.get(Calendar.MONTH)) {
                    isPastOrToday = true;
                } else if (displayCalendar.get(Calendar.MONTH) > realToday.get(Calendar.MONTH)) {
                    isPastOrToday = false;
                } else {
                    isPastOrToday = day <= realToday.get(Calendar.DAY_OF_MONTH);
                }
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
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0; 
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        // 🔥 WIDER CARDS: Margins kam ki hain taaki cell chauda ho
        params.setMargins(4, 8, 4, 8); 

        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setLayoutParams(params);
        // 🔥 WIDER & TALLER FEEL: Padding ko theek se balance kiya hai
        cell.setPadding(0, 16, 0, 16); 
        cell.setGravity(Gravity.CENTER);

        TextView tvDate = new TextView(this);
        tvDate.setText(dayText);
        tvDate.setTextSize(13f);
        tvDate.setTextColor(Color.parseColor("#0F172A"));
        tvDate.setGravity(Gravity.CENTER);

        View dot = new View(this);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(16, 16);
        dotParams.topMargin = 12;
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
