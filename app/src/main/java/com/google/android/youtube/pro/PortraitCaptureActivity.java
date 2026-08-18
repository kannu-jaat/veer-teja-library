package com.google.android.youtube.pro;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.client.android.Intents;
import com.google.zxing.common.HybridBinarizer;
import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.io.InputStream;

public class PortraitCaptureActivity extends AppCompatActivity {

    private CaptureManager capture;
    private DecoratedBarcodeView barcodeScannerView;
    private LinearLayout btnGalleryContainer;

    // 🔥 Modern Gallery Intent Handler
    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    try {
                        Uri imageUri = result.getData().getData();
                        InputStream imageStream = getContentResolver().openInputStream(imageUri);
                        Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
                        scanQRFromBitmap(selectedImage); // Pass image to our decoder
                    } catch (Exception e) {
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_scanner);

        barcodeScannerView = findViewById(R.id.zxing_barcode_scanner);
        btnGalleryContainer = findViewById(R.id.btnGalleryContainer);

        // 🔥 FIREBASE CACHE CHECK
        SharedPreferences prefs = getSharedPreferences("LibraryApp", Context.MODE_PRIVATE);
        String canUpload = prefs.getString("feature_qr_upload", "no");

        if ("yes".equalsIgnoreCase(canUpload)) {
            btnGalleryContainer.setVisibility(View.VISIBLE); // Button Dikhana
            btnGalleryContainer.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                galleryLauncher.launch(intent);
            });
        } else {
            btnGalleryContainer.setVisibility(View.GONE); // Button Chhupana
        }

        // Initialize ZXing Camera Manager
        capture = new CaptureManager(this, barcodeScannerView);
        capture.initializeFromIntent(getIntent(), savedInstanceState);
        capture.decode();
    }

    // 🔥 DECODE QR FROM GALLERY IMAGE
    private void scanQRFromBitmap(Bitmap bMap) {
        try {
            int[] intArray = new int[bMap.getWidth() * bMap.getHeight()];
            bMap.getPixels(intArray, 0, bMap.getWidth(), 0, 0, bMap.getWidth(), bMap.getHeight());
            
            LuminanceSource source = new RGBLuminanceSource(bMap.getWidth(), bMap.getHeight(), intArray);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            
            // Core ZXing Decoder
            Result result = new MultiFormatReader().decode(bitmap);
            
            // Return result back to DashboardActivity (Jaise live camera return karta hai)
            Intent intent = new Intent();
            intent.putExtra(Intents.Scan.RESULT, result.getText());
            setResult(RESULT_OK, intent);
            finish(); // Scanner band karo aur wapas Dashboard pe jao
            
        } catch (Exception e) {
            Toast.makeText(this, "No valid QR Code found in this image!", Toast.LENGTH_LONG).show();
        }
    }

    // --- Mandatory ZXing Lifecycle Methods ---
    @Override
    protected void onResume() {
        super.onResume();
        capture.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        capture.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        capture.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        capture.onSaveInstanceState(outState);
    }
}
