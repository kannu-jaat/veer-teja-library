package com.google.android.youtube.pro;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MySeatFragment extends Fragment {

    private ImageView btnBackSeat;
    private View btnHomeSeat;
    private TextView tvSeatStatusBadge, tvHeroSeatNo, tvSeatStudentName;
    private TextView tvDetailPlan, tvDetailValidTill, tvDetailSeatNo, tvDetailDaysPresent;

    private SharedPreferences prefs;
    private String savedUsername;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_seat, container, false);

        if (getContext() != null) {
            prefs = getContext().getSharedPreferences("LibraryApp", Context.MODE_PRIVATE);
            savedUsername = prefs.getString("username", "");
        }

        initViews(view);
        setupClickListeners();
        loadFromCache();
        fetchLiveSeatAndAttendance();

        return view;
    }

    private void initViews(View view) {
        btnBackSeat = view.findViewById(R.id.btnBackSeat);
        btnHomeSeat = view.findViewById(R.id.btnHomeSeat);
        tvSeatStatusBadge = view.findViewById(R.id.tvSeatStatusBadge);
        tvHeroSeatNo = view.findViewById(R.id.tvHeroSeatNo);
        tvSeatStudentName = view.findViewById(R.id.tvSeatStudentName);
        tvDetailPlan = view.findViewById(R.id.tvDetailPlan);
        tvDetailValidTill = view.findViewById(R.id.tvDetailValidTill);
        tvDetailSeatNo = view.findViewById(R.id.tvDetailSeatNo);
        tvDetailDaysPresent = view.findViewById(R.id.tvDetailDaysPresent);
    }

    private void setupClickListeners() {
        View.OnClickListener closeAction = v -> {
            if (getActivity() instanceof DashboardActivity) {
                ((DashboardActivity) getActivity()).closeFragmentWithAnimation();
            }
        };
        if (btnBackSeat != null) btnBackSeat.setOnClickListener(closeAction);
        if (btnHomeSeat != null) btnHomeSeat.setOnClickListener(closeAction);
    }

    private void loadFromCache() {
        if (prefs == null) return;

        String name = prefs.getString("cachedName", "Student");
        String seat = prefs.getString("cachedSeatNumber", "--");
        String plan = prefs.getString("cachedMembership", "Pending");
        String validTill = prefs.getString("cachedValidTill", "--");
        String status = prefs.getString("cachedStatus", "Active");
        int days = prefs.getInt("cachedDaysPresent", 0);

        bindSeatData(name, seat, plan, validTill, status, days);
    }

    private void fetchLiveSeatAndAttendance() {
        if (savedUsername == null || savedUsername.isEmpty() || prefs == null) return;

        DatabaseReference studentRef = FirebaseDatabase.getInstance().getReference("Students").child(savedUsername);
        studentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || !snapshot.exists()) return;

                SharedPreferences.Editor editor = prefs.edit();

                String fullName = snapshot.child("fullName").getValue(String.class);
                String seat = snapshot.child("seatNumber").getValue(String.class);
                String plan = snapshot.child("membership").getValue(String.class);
                String validTill = snapshot.child("validTill").getValue(String.class);
                String status = snapshot.child("status").getValue(String.class);

                if (fullName != null && !fullName.isEmpty()) editor.putString("cachedName", fullName);
                if (seat != null && !seat.isEmpty()) editor.putString("cachedSeatNumber", seat);
                if (plan != null && !plan.isEmpty()) editor.putString("cachedMembership", plan);
                if (validTill != null && !validTill.isEmpty()) editor.putString("cachedValidTill", validTill);
                if (status != null && !status.isEmpty()) editor.putString("cachedStatus", status);
                editor.apply();

                loadFromCache();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        String currentMonthYear = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(new Date());
        DatabaseReference attRef = FirebaseDatabase.getInstance().getReference("Attendance").child(savedUsername);
        attRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || prefs == null) return;

                int presentDays = 0;
                for (DataSnapshot daySnap : snapshot.getChildren()) {
                    String dateKey = daySnap.getKey();
                    if (dateKey != null && dateKey.endsWith(currentMonthYear)) {
                        presentDays++;
                    }
                }
                prefs.edit().putInt("cachedDaysPresent", presentDays).apply();
                tvDetailDaysPresent.setText(presentDays + (presentDays == 1 ? " Day" : " Days"));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void bindSeatData(String name, String seat, String plan, String validTill, String status, int days) {
        String cleanSeat = (seat != null && !seat.trim().isEmpty()) ? seat.trim() : "--";
        String cleanPlan = (plan != null && !plan.trim().isEmpty()) ? plan.trim() : "Pending";
        String cleanValid = (validTill != null && !validTill.trim().isEmpty()) ? validTill.trim() : "--";
        String cleanName = (name != null && !name.trim().isEmpty()) ? name.trim() : "Student";

        tvHeroSeatNo.setText(cleanSeat);
        tvDetailSeatNo.setText(cleanSeat);
        tvDetailPlan.setText(cleanPlan);
        tvDetailValidTill.setText(cleanValid);
        tvSeatStudentName.setText(cleanName);
        tvDetailDaysPresent.setText(days + (days == 1 ? " Day" : " Days"));

        boolean isInactive = status != null && (status.equalsIgnoreCase("Expired") || status.equalsIgnoreCase("Inactive") || status.equalsIgnoreCase("Pending"));
        if (isInactive) {
            tvSeatStatusBadge.setText(status);
            tvSeatStatusBadge.setBackgroundColor(Color.parseColor("#DC2626"));
        } else {
            tvSeatStatusBadge.setText("Active ✓");
            tvSeatStatusBadge.setBackgroundColor(Color.parseColor("#16A34A"));
        }
    }
}
