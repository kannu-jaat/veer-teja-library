package com.google.android.youtube.pro;

import android.app.Activity;
import android.os.Bundle;

public class LoginActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Yeh login.xml screen ko dikhayega
        setContentView(R.layout.login);
    }
}

