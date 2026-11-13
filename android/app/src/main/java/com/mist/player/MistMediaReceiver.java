package com.mist.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MistMediaReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Forward to MainActivity's static helper - plugin will pick it up via session callback
        // We use MediaSession via a broadcast that MainActivity listens to is complex,
        // so instead we send an intent to MainActivity which forwards to the plugin via JS event.
        // Simplest: store action in shared prefs and let JS poll? No - use explicit broadcast to plugin.
        // This receiver is wired in AndroidManifest; MainActivity registers a receiver that re-emits.
        Intent i = new Intent("com.mist.player.MEDIA_ACTION");
        i.putExtra("action", intent.getAction());
        context.sendBroadcast(i);
    }
}
