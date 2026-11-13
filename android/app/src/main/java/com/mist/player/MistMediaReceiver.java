package com.mist.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MistMediaReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction(); // com.mist.player.PLAY / PAUSE / NEXT / PREV
        String shortAction = action.replace("com.mist.player.", "").toLowerCase();
        // Forward to MainActivity's receiver, which forwards to JS
        Intent fwd = new Intent("com.mist.player.MEDIA_ACTION");
        fwd.setPackage(ctx.getPackageName());
        fwd.putExtra("action", action);
        try { ctx.sendBroadcast(fwd); } catch (Exception ignored) {}
        // Also wake the service so notification stays alive
        try {
            Intent svc = new Intent(ctx, MistMediaService.class);
            svc.setAction(action);
            if (android.os.Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc);
            else ctx.startService(svc);
        } catch (Exception ignored) {}
    }
}
