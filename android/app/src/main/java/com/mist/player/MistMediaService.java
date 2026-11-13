package com.mist.player;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class MistMediaService extends Service {
    public static MistMediaService instance;
    private MistMediaHelper helper;
    private String lastTitle = "", lastArtist = "";
    private boolean lastPlaying = false;
    private int lastPos = 0, lastDur = 0;

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        // Helper needs a plugin ref - we pass null and use static emit via MainActivity
        // We create helper with a lightweight plugin stub via MistMediaPlugin.getInstance()
        MistMediaPlugin plugin = MistMediaPlugin.getInstance();
        helper = new MistMediaHelper(this, plugin);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // If intent carries an action (from Receiver), MainActivity will handle it via broadcast.
        // Here we just ensure we are in foreground with last known notification.
        if (helper != null && !lastTitle.isEmpty()) {
            Notification n = helper.buildNotification(lastTitle, lastArtist, lastPlaying, lastPos, lastDur);
            try { startForeground(1001, n); } catch (Exception e) { try { startForeground(1001, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK); } catch (Exception ignored) {} }
        }
        return START_STICKY;
    }
    public void show(String title, String artist, boolean playing, int pos, int dur) {
        lastTitle = title; lastArtist = artist; lastPlaying = playing; lastPos = pos; lastDur = dur;
        if (helper == null) return;
        Notification n = helper.buildNotification(title, artist, playing, pos, dur);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(1001, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(1001, n);
        } catch (Exception e) {
            // Fallback: plain notify (Android 13+ needs POST_NOTIFICATIONS)
            try { helper.notifyOnly(n); } catch (Exception ignored) {}
        }
    }
    public void hide() {
        try { stopForeground(true); } catch (Exception ignored) {}
        try { stopSelf(); } catch (Exception ignored) {}
    }
    @Override public void onDestroy() { instance = null; super.onDestroy(); }
    @Override public IBinder onBind(Intent i) { return null; }
}
