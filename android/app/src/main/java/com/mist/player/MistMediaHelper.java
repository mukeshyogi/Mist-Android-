package com.mist.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

public class MistMediaHelper {
    private static final String CHANNEL_ID = "mist_playback";
    private static final int NOTIF_ID = 1001;
    private final Context ctx;
    private final MistMediaPlugin plugin;
    private MediaSessionCompat session;
    private NotificationManager nm;

    public MistMediaHelper(Context ctx, MistMediaPlugin plugin) {
        this.ctx = ctx;
        this.plugin = plugin;
        this.nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Now playing", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Mist playback controls");
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        session = new MediaSessionCompat(ctx, "MistSession");
        session.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { emit("play"); }
            @Override public void onPause() { emit("pause"); }
            @Override public void onSkipToNext() { emit("next"); }
            @Override public void onSkipToPrevious() { emit("prev"); }
            @Override public void onSeekTo(long pos) {
                com.getcapacitor.JSObject d2 = new com.getcapacitor.JSObject();
                try { d2.put("action", "seek"); d2.put("position", pos); } catch (Exception ignored) {}
                if (plugin != null) plugin.emitAction(d2);
            }
            @Override public void onStop() { emit("pause"); }
        });
        session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setActive(true);
    }

    private void emit(String action) {
        com.getcapacitor.JSObject d = new com.getcapacitor.JSObject();
        try { d.put("action", action); } catch (Exception ignored) {}
        if (plugin != null) plugin.emitAction(d);
    }

    private PendingIntent actionIntent(String action) {
        Intent i = new Intent(ctx, MistMediaReceiver.class);
        i.setAction("com.mist.player." + action.toUpperCase());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(ctx, action.hashCode(), i, flags);
    }

    // Build notification - called by Service (foreground) and by Plugin (fallback)
    public Notification buildNotification(String title, String artist, boolean playing, int position, int duration) {
        session.setMetadata(new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            .build());
        int state = playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        session.setPlaybackState(new PlaybackStateCompat.Builder()
            .setState(state, position, 1.0f)
            .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_PLAY_PAUSE)
            .build());

        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent content = PendingIntent.getActivity(ctx, 0, open, flags);

        // Use app icon, not generic Android icon - shows Mist logo in status bar
        int icon = ctx.getResources().getIdentifier("ic_launcher", "mipmap", ctx.getPackageName());
        if (icon == 0) icon = android.R.drawable.ic_media_play;

        return new NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(artist)
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
            .setStyle(new MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2))
            .build();
    }

    public void notifyOnly(Notification n) {
        try { nm.notify(NOTIF_ID, n); } catch (SecurityException ignored) {}
    }

    public void updateNotification(String title, String artist, boolean playing, int position, int duration) {
        // Prefer Foreground Service - this is what keeps it on lockscreen + survives background
        if (MistMediaService.instance != null) {
            MistMediaService.instance.show(title, artist, playing, position, duration);
            return;
        }
        // No service yet - start it, it will show the notification
        try {
            Intent svc = new Intent(ctx, MistMediaService.class);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc);
            else ctx.startService(svc);
            // Fallback: show directly now, service will take over on next update
            notifyOnly(buildNotification(title, artist, playing, position, duration));
        } catch (Exception e) {
            notifyOnly(buildNotification(title, artist, playing, position, duration));
        }
    }

    public void stopNotification() {
        try { if (MistMediaService.instance != null) MistMediaService.instance.hide(); } catch (Exception ignored) {}
        try { nm.cancel(NOTIF_ID); } catch (Exception ignored) {}
        try { session.setPlaybackState(new PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_STOPPED, 0, 1.0f).build()); } catch (Exception ignored) {}
    }
}
