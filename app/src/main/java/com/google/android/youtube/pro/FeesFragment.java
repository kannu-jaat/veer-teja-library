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

public class FeesFragment extends Fragment {

    private ImageView btnBackFees;
    private TextView tvFeeStatusText, tvDueAmount, tvValidTill, tvLastPaidMonth, tvNoHistory;
    private LinearLayout paymentHistoryContainer;
    private String savedUsername;

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

        SharedPreferences prefs = requireActivity().getSharedPreferences("LibraryApp", Context.MODE_PRIVATE);
        savedUsername = prefs.getString("username", "");

        // Close logic
        btnBackFees.setOnClickListener(v -> {
            if (getActivity() instanceof DashboardActivity) {
                ((DashboardActivity) getActivity()).closeFragmentWithAnimation();
            }
        });

        fetchCurrentStatus();
        fetchPaymentHistory();

        return view;
    }

    private void fetchCurrentStatus() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Students").child(savedUsername);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String status = snapshot.child("feeStatus").getValue(String.class);
                    Long dueAmt = snapshot.child("dueAmount").getValue(Long.class);
                    String valid = snapshot.child("validTill").getValue(String.class);
                    String lastMonth = snapshot.child("lastPaidMonth").getValue(String.class);

                    if (status != null) {
                        tvFeeStatusText.setText(status);
                        if (status.equalsIgnoreCase("Paid")) {
                            tvFeeStatusText.setTextColor(Color.parseColor("#10B981")); // Green
                        } else if (status.equalsIgnoreCase("Pending") || status.equalsIgnoreCase("Overdue")) {
                            tvFeeStatusText.setTextColor(Color.parseColor("#EF4444")); // Red
                        }
                    }
                    
                    if (dueAmt != null && dueAmt > 0) {
                        tvDueAmount.setText("₹" + dueAmt);
                        tvDueAmount.setVisibility(View.VISIBLE);
                    } else {
                        tvDueAmount.setVisibility(View.GONE);
                    }

                    if (valid != null) tvValidTill.setText(valid);
                    if (lastMonth != null) tvLastPaidMonth.setText(lastMonth);
                }
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void fetchPaymentHistory() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Payments").child(savedUsername);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                    tvNoHistory.setVisibility(View.GONE);
                    paymentHistoryContainer.removeAllViews();

                    for (DataSnapshot monthSnap : snapshot.getChildren()) {
                        String monthName = monthSnap.getKey(); // e.g., "June 2026"
                        Long amount = monthSnap.child("amount").getValue(Long.class);
                        String payDate = monthSnap.child("payDate").getValue(String.class);

                        addHistoryRow(monthName, payDate, amount);
                    }
                } else {
                    tvNoHistory.setText("No payment history found.");
                }
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void addHistoryRow(String monthName, String payDate, Long amount) {
        View rowView = LayoutInflater.from(getContext()).inflate(R.layout.item_payment_history, paymentHistoryContainer, false);
        
        // Dynamically creating a clean layout for the row to avoid needing another XML file
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(16, 24, 16, 24);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        LinearLayout leftCol = new LinearLayout(getContext());
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvMonth = new TextView(getContext());
        tvMonth.setText(monthName);
        tvMonth.setTextColor(Color.parseColor("#0F172A"));
        tvMonth.setTextSize(14f);
        tvMonth.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvDate = new TextView(getContext());
        tvDate.setText("Paid on: " + (payDate != null ? payDate : "--"));
        tvDate.setTextColor(Color.parseColor("#64748B"));
        tvDate.setTextSize(11f);
        tvDate.setPadding(0, 4, 0, 0);

        leftCol.addView(tvMonth);
        leftCol.addView(tvDate);

        TextView tvAmt = new TextView(getContext());
        tvAmt.setText("₹" + (amount != null ? amount : "0"));
        tvAmt.setTextColor(Color.parseColor("#10B981"));
        tvAmt.setTextSize(16f);
        tvAmt.setTypeface(null, android.graphics.Typeface.BOLD);

        row.addView(leftCol);
        row.addView(tvAmt);

        paymentHistoryContainer.addView(row);

        // Add divider
        View divider = new View(getContext());
        divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(Color.parseColor("#F1F5F9"));
        paymentHistoryContainer.addView(divider);
    }
}
