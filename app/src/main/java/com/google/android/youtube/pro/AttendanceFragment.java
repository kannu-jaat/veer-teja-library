package com.google.android.youtube.pro;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

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

public class AttendanceFragment extends Fragment {

    private TextView tvMonthYearTitle, tvPresentCount, tvAbsentCount, tvUpcomingCount;
    private GridLayout calendarGrid;
    private CardView btnPrevMonth, btnNextMonth;
    private String savedUsername;
    private Calendar displayCalendar;

    private static final int STATUS_PRESENT = 1;
    private static final int STATUS_ABSENT = 2;
    private static final int STATUS_UPCOMING = 3;
    private static final int STATUS_EMPTY = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_attendance, container, false);

        tvMonthYearTitle = view.findViewById(R.id.tvMonthYearTitle);
        tvPresentCount = view.findViewById(R.id.tvPresentCount);
        tvAbsentCount = view.findViewById(R.id.tvAbsentCount);
        tvUpcomingCount = view.findViewById(R.id.tvUpcomingCount);
        calendarGrid = view.findViewById(R.id.calendarGrid);
        btnPrevMonth = view.findViewById(R.id.btnPrevMonth);
        btnNextMonth = view.findViewById(R.id.btnNextMonth);

        SharedPreferences prefs = requireActivity().getSharedPreferences("LibraryApp", Context.MODE_PRIVATE);
        savedUsername = prefs.getString("username", "");
        
        displayCalendar = Calendar.getInstance(); 
        
        btnPrevMonth.setOnClickListener(v -> {
            displayCalendar.add(Calendar.MONTH, -1);
            fetchMonthData();
        });

        btnNextMonth.setOnClickListener(v -> {
            displayCalendar.add(Calendar.MONTH, 1);
            fetchMonthData();
        });

        fetchMonthData();
        return view;
    }

    private void fetchMonthData() {
        String monthYearStr = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(displayCalendar.getTime());
        tvMonthYearTitle.setText(monthYearStr); 
        calendarGrid.removeAllViews(); 

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
                        } catch (Exception e) {}
                    }
                }
                buildPremiumCalendar(presentDates);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                if(getContext() != null) Toast.makeText(getContext(), "Failed to load data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void buildPremiumCalendar(Set<Integer> presentDates) {
        calendarGrid.removeAllViews(); 
        Calendar realToday = Calendar.getInstance();
        int daysInMonth = displayCalendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        
        Calendar tempCal = (Calendar) displayCalendar.clone();
        tempCal.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK); 
        int emptyCellsBeforeStart = firstDayOfWeek - 1; 

        int presentCount = 0;
        int absentCount = 0;
        int upcomingCount = 0;

        for (int i = 0; i < emptyCellsBeforeStart; i++) {
            calendarGrid.addView(createCalendarCell("", STATUS_EMPTY));
        }

        for (int day = 1; day <= daysInMonth; day++) {
            boolean isPresent = presentDates.contains(day);
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
        // 🔥 Margins ko vertical me kam kiya hai (Top aur Bottom sirf 4dp hain)
        params.setMargins(2, 4, 2, 4); 

        LinearLayout cell = new LinearLayout(requireContext());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setLayoutParams(params);
        // 🔥 Vertical Padding ko squeeze kiya hai (Top/Bottom 10dp ki jagah 8dp kar diya)
        cell.setPadding(0, 8, 0, 8); 
        cell.setGravity(Gravity.CENTER);

        TextView tvDate = new TextView(requireContext());
        tvDate.setText(dayText);
        tvDate.setTextSize(13f);
        tvDate.setTextColor(Color.parseColor("#0F172A"));
        tvDate.setGravity(Gravity.CENTER);

        View dot = new View(requireContext());
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(16, 16);
        // Dot ko thoda upar khiskaya hai
        dotParams.topMargin = 8;
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
