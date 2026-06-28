package com.google.android.youtube.pro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class ForegroundService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        
        // Yeh ek basic system icon ke sath notification banayega
        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, "LibraryChannel")
                    .setContentTitle("Krishna Library")
                    .setContentText("Library App background me chal raha hai")
                    .setSmallIcon(android.R.drawable.ic_dialog_info) 
                    .build();
        } else {
            notification = new Notification.Builder(this)
                    .setContentTitle("Krishna Library")
                    .setContentText("Welcome Back 💕")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build();
        }
        
        // Service ko zinda rakhne ke liye Foreground me start karein
        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // START_STICKY ka matlab hai agar phone service ko kill kare, toh wo wapas auto-start ho jayegi
        return START_STICKY; 
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "LibraryChannel",
                    "Library Notifications",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
