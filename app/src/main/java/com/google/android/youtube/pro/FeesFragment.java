package com.google.android.youtube.pro;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FeesFragment extends Fragment {

    private ImageView btnBackFees;
    private TextView tvFeeStatusText, tvDueAmount, tvValidTill, tvLastPaidMonth, tvNoHistory;
    private LinearLayout paymentHistoryContainer;
    private String savedUsername;
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_fees, container, false);

        btnBackFees = view.findViewById(R.id.btnBackFees);
        tvFeeStatusText = view.findViewById(R.id.tvFeeStatusText);
        tvDueAmount = view.findViewById(R.id.tvDueAmount);
        tvValidTill = view.findViewById(R.id.tvValidTill);
        tvLastPaidMonth = view.findViewById(R.id.tvLastPaidMonth);
        paymentHistoryContainer = view.findViewById(R.id.paymentHistoryContainer);
        tvNoHistory = view.findViewById(R.id.tvNoHistory);

        prefs = requireActivity().getSharedPreferences("LibraryApp", Context.MODE_PRIVATE);
        savedUsername = prefs.getString("username", "");

        btnBackFees.setOnClickListener(v -> {
            if (getActivity() instanceof DashboardActivity) {
                ((DashboardActivity) getActivity()).closeFragmentWithAnimation();
            }
        });

        loadCachedData();

        fetchCurrentStatus();
        fetchPaymentHistory();

        return view;
    }

    private void loadCachedData() {
        String status = prefs.getString("cache_feeStatus", "Loading...");
        long dueAmt = prefs.getLong("cache_dueAmount", 0);
        String valid = prefs.getString("cache_validTill", "--");
        String lastMonth = prefs.getString("cache_lastPaidMonth", "--");

        updateCurrentStatusUI(status, dueAmt, valid);
        tvLastPaidMonth.setText(lastMonth); // Cache se Last Paid Month set karo

        String historyJson = prefs.getString("cache_paymentHistory", "");
        if (!historyJson.isEmpty()) {
            try {
                JSONArray array = new JSONArray(historyJson);
                if (array.length() > 0) {
                    tvNoHistory.setVisibility(View.GONE);
                    paymentHistoryContainer.removeAllViews();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.getJSONObject(i);
                        addHistoryRow(
                                obj.getString("monthName"),
                                obj.optString("payDate", "--"),
                                obj.getLong("amount"),
                                obj.optString("d2d", "")
                        );
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Isme se lastMonth ka parameter hata diya gaya hai
    private void updateCurrentStatusUI(String status, Long dueAmt, String valid) {
        if (status != null) {
            tvFeeStatusText.setText(status);
            if (status.equalsIgnoreCase("Paid")) {
                tvFeeStatusText.setTextColor(Color.parseColor("#10B981")); 
            } else if (status.equalsIgnoreCase("Pending") || status.equalsIgnoreCase("Overdue") || status.equalsIgnoreCase("Due")) {
                tvFeeStatusText.setTextColor(Color.parseColor("#EF4444")); 
            }
        }
        
        if (dueAmt != null && dueAmt > 0) {
            tvDueAmount.setText("₹" + dueAmt);
            tvDueAmount.setVisibility(View.VISIBLE);
        } else {
            tvDueAmount.setVisibility(View.GONE);
        }

        if (valid != null) tvValidTill.setText(valid);
    }

    private void fetchCurrentStatus() {
        DatabaseReference studentRef = FirebaseDatabase.getInstance().getReference("Students").child(savedUsername);
        DatabaseReference offsetRef = FirebaseDatabase.getInstance().getReference(".info/serverTimeOffset");

        offsetRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot offsetSnapshot) {
                long offset = 0;
                if (offsetSnapshot.exists()) {
                    offset = offsetSnapshot.getValue(Long.class);
                }

                long estimatedServerTimeMs = System.currentTimeMillis() + offset;
                Date onlineCurrentDate = new Date(estimatedServerTimeMs);

                studentRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Long dueAmt = snapshot.child("dueAmount").getValue(Long.class);
                            String validTill = snapshot.child("validTill").getValue(String.class);
                            
                            // Ab lastPaidMonth Firebase se read karne ki jarurat nahi hai

                            String dynamicStatus = "Pending"; 

                            if (validTill != null && !validTill.isEmpty()) {
                                try {
                                    SimpleDateFormat sdf = new SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH);
                                    Date validTillDate = sdf.parse(validTill);

                                    if (validTillDate != null) {
                                        if (validTillDate.before(onlineCurrentDate)) {
                                            dynamicStatus = "Due";
                                        } else {
                                            dynamicStatus = "Paid"; 
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    dynamicStatus = "Error parsing date";
                                }
                            }

                            updateCurrentStatusUI(dynamicStatus, dueAmt, validTill);

                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("cache_feeStatus", dynamicStatus);
                            if (dueAmt != null) editor.putLong("cache_dueAmount", dueAmt);
                            if (validTill != null) editor.putString("cache_validTill", validTill);
                            editor.apply();
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void fetchPaymentHistory() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Payments").child(savedUsername);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                    
                    List<DataSnapshot> monthList = new ArrayList<>();
                    for (DataSnapshot snap : snapshot.getChildren()) {
                        monthList.add(snap);
                    }

                    SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);
                    Collections.sort(monthList, new Comparator<DataSnapshot>() {
                        @Override
                        public int compare(DataSnapshot s1, DataSnapshot s2) {
                            try {
                                Date date1 = sdf.parse(s1.getKey());
                                Date date2 = sdf.parse(s2.getKey());
                                return date2.compareTo(date1); 
                            } catch (Exception e) {
                                return 0;
                            }
                        }
                    });

                    // 🔥 AUTO-FETCH LAST PAID MONTH SE LATEST ENTRY (Index 0)
                    if (!monthList.isEmpty()) {
                        String latestMonth = monthList.get(0).getKey(); // Sabse top wala month
                        tvLastPaidMonth.setText(latestMonth);
                        prefs.edit().putString("cache_lastPaidMonth", latestMonth).apply();
                    }

                    tvNoHistory.setVisibility(View.GONE);
                    paymentHistoryContainer.removeAllViews();
                    JSONArray cacheArray = new JSONArray();

                    for (DataSnapshot monthSnap : monthList) {
                        String monthName = monthSnap.getKey(); 
                        Long amount = monthSnap.child("amount").getValue(Long.class);
                        String payDate = monthSnap.child("payDate").getValue(String.class);
                        String d2d = monthSnap.child("d2d").getValue(String.class); 

                        if (amount == null) amount = 0L;
                        if (payDate == null) payDate = "--";
                        if (d2d == null) d2d = "";

                        addHistoryRow(monthName, payDate, amount, d2d);

                        try {
                            JSONObject obj = new JSONObject();
                            obj.put("monthName", monthName);
                            obj.put("amount", amount);
                            obj.put("payDate", payDate);
                            obj.put("d2d", d2d);
                            cacheArray.put(obj);
                        } catch (Exception e) { e.printStackTrace(); }
                    }

                    prefs.edit().putString("cache_paymentHistory", cacheArray.toString()).apply();

                } else {
                    tvNoHistory.setText("No payment history found.");
                    tvNoHistory.setVisibility(View.VISIBLE);
                    
                    // Agar koi history nahi hai toh dash dikhao
                    tvLastPaidMonth.setText("--");
                    prefs.edit().putString("cache_lastPaidMonth", "--").apply();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void addHistoryRow(String monthName, String payDate, Long amount, String d2d) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(16, 28, 16, 28);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout leftCol = new LinearLayout(getContext());
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvMonth = new TextView(getContext());
        tvMonth.setText(monthName);
        tvMonth.setTextColor(Color.parseColor("#0F172A"));
        tvMonth.setTextSize(15f);
        tvMonth.setTypeface(null, android.graphics.Typeface.BOLD);
        leftCol.addView(tvMonth);

        if (d2d != null && !d2d.trim().isEmpty()) {
            TextView tvD2D = new TextView(getContext());
            tvD2D.setText("🗓️ " + d2d);
            tvD2D.setTextColor(Color.parseColor("#475569"));
            tvD2D.setTextSize(12f);
            tvD2D.setPadding(0, 6, 0, 0);
            leftCol.addView(tvD2D);
        }

        TextView tvDate = new TextView(getContext());
        tvDate.setText("Paid on: " + payDate);
        tvDate.setTextColor(Color.parseColor("#94A3B8"));
        tvDate.setTextSize(11f);
        tvDate.setPadding(0, 4, 0, 0);
        leftCol.addView(tvDate);

        TextView tvAmt = new TextView(getContext());
        tvAmt.setText("₹" + amount);
        tvAmt.setTextColor(Color.parseColor("#10B981"));
        tvAmt.setTextSize(18f);
        tvAmt.setTypeface(null, android.graphics.Typeface.BOLD);

        row.addView(leftCol);
        row.addView(tvAmt);

        paymentHistoryContainer.addView(row);

        View divider = new View(getContext());
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
        divider.setBackgroundColor(Color.parseColor("#F1F5F9"));
        paymentHistoryContainer.addView(divider);
    }
}
