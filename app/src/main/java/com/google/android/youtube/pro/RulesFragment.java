package com.google.android.youtube.pro;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class RulesFragment extends Fragment {

    private ImageView btnBackRules;
    private LinearLayout rulesContainer;

    // Rules Text Array
    private final String[] rules = {
            "Maintain Silence",
            "Mobile on Silent Mode",
            "Do Not Disturb Others",
            "Respect Library Property",
            "QR Attendance Mandatory",
            "Sit on Your Allotted Seat",
            "Keep the Library Clean",
            "Pay Fees Before Due Date",
            "Do Not Share Your Login Credentials"
    };

    // Rules Emoji Array based on context
    private final String[] emojis = {"🤫", "📴", "✋", "📚", "📱", "🪑", "🧹", "💳", "🔒"};

    // Colors according to the meaning of the rule
    private final String[] colors = {
            "#0EA5E9", // Sky Blue - Silence
            "#8B5CF6", // Purple - Silent Mode
            "#F43F5E", // Rose Red - Do not disturb
            "#10B981", // Emerald Green - Property
            "#F59E0B", // Amber - Mandatory Attendance
            "#06B6D4", // Cyan - Seat
            "#14B8A6", // Teal - Cleanliness
            "#EF4444", // Red - Pay Fees
            "#F97316"  // Orange - Credentials
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rules, container, false);

        btnBackRules = view.findViewById(R.id.btnBackRules);
        rulesContainer = view.findViewById(R.id.rulesContainer);

        // Close logic
        btnBackRules.setOnClickListener(v -> {
            if (getActivity() instanceof DashboardActivity) {
                ((DashboardActivity) getActivity()).closeFragmentWithAnimation();
            }
        });

        // Trigger the cascading animation
        animateRulesLoading();

        return view;
    }

    private void animateRulesLoading() {
        rulesContainer.removeAllViews();
        Handler handler = new Handler(Looper.getMainLooper());

        for (int i = 0; i < rules.length; i++) {
            final int index = i;
            
            // Create Rule View
            View ruleView = createRuleCard(rules[index], emojis[index], colors[index]);
            
            // Initial Invisible state for animation
            ruleView.setAlpha(0f);
            ruleView.setTranslationY(80f); // Niche se upar aayega
            
            rulesContainer.addView(ruleView);

            // Staggered Delay (Every rule appears after a 200ms delay from the previous one)
            handler.postDelayed(() -> {
                ruleView.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(400) // Animation speed
                        .start();
            }, index * 200L); // 200ms * 9 rules = ~1.8 Seconds total time
        }
    }

    private View createRuleCard(String ruleText, String emoji, String colorHex) {
        // Container for each rule
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 24); // Gap between rules
        card.setLayoutParams(params);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.setPadding(30, 30, 30, 30);

        // Premium Soft Background (10% opacity of the main color)
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(24f);
        bg.setColor(Color.parseColor(colorHex.replace("#", "#1A"))); // Adding 1A makes it 10% transparent
        bg.setStroke(2, Color.parseColor(colorHex.replace("#", "#4D"))); // 30% transparent border
        card.setBackground(bg);

        // Emoji View
        TextView tvEmoji = new TextView(getContext());
        tvEmoji.setText(emoji);
        tvEmoji.setTextSize(22f);
        tvEmoji.setPadding(0, 0, 24, 0);

        // Text View
        TextView tvText = new TextView(getContext());
        tvText.setText(ruleText);
        tvText.setTextColor(Color.parseColor(colorHex)); // Real color for text
        tvText.setTextSize(15f);
        tvText.setTypeface(null, android.graphics.Typeface.BOLD);

        card.addView(tvEmoji);
        card.addView(tvText);

        return card;
    }
}
