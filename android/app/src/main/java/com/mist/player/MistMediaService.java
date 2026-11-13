package com.mist.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

public class MistMediaService extends Service {
    public static MistMediaService instance;
    private static final String CHANNEL_ID = "mist_playback";
    private static final int NOTIF_ID = 1001;
    private String lastTitle = "", lastArtist = "";
    private boolean lastPlaying = false;
    private int lastPos = 0, lastDur = 0;

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        createChannel();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Now playing", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("Mist playback controls");
                ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(ch);
            }
        }
    }

    private PendingIntent actionIntent(String action) {
        Intent i = new Intent(this, MistMediaReceiver.class);
        i.setAction("com.mist.player." + action.toUpperCase());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(this, action.hashCode(), i, flags);
    }

    // Builds notification HERE - does NOT call helper.buildNotification(), so it compiles with ANY Helper version
    private Notification buildNotification(String title, String artist, boolean playing, int pos, int dur) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent content = PendingIntent.getActivity(this, 0, open, flags);
        int icon = getResources().getIdentifier("ic_launcher", "mipmap", getPackageName());
        if (icon == 0) icon = android.R.drawable.ic_media_play;
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title == null || title.isEmpty() ? "Mist" : title)
            .setContentText(artist == null ? "" : artist)
            .setContentIntent(content)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(android.R.drawable.ic_media_previous, "Prev", actionIntent("prev"))
            .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play, playing ? "Pause" : "Play", actionIntent(playing ? "pause" : "play"))
            .addAction(android.R.drawable.ic_media_next, "Next", actionIntent("next"))
            .build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!lastTitle.isEmpty()) {
            show(lastTitle, lastArtist, lastPlaying, lastPos, lastDur);
        }
        return START_STICKY;
    }

    public void show(String title, String artist, boolean playing, int pos, int dur) {
        lastTitle = title; lastArtist = artist; lastPlaying = playing; lastPos = pos; lastDur = dur;
        Notification n = buildNotification(title, artist, playing, pos, dur);
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NOTIF_ID, n);
        } catch (Exception e) {
            try { startForeground(NOTIF_ID, n); } catch (Exception ignored) {}
        }
    }

    public void hide() {
        try { stopForeground(true); } catch (Exception ignored) {}
        try { stopSelf(); } catch (Exception ignored) {}
    }

    @Override public void onDestroy() { instance = null; super.onDestroy(); }
    @Override public IBinder onBind(Intent i) { return null; }
}
