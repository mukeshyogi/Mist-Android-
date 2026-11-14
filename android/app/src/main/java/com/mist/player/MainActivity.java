package com.mist.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    // DO NOT call registerPlugin() here - Capacitor 6 auto-discovers @CapacitorPlugin classes.
    // Calling registerPlugin() with the old API is why MM bridge was sometimes missing.
    private final BroadcastReceiver mediaReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            String a = intent.getStringExtra("action");
            if (a == null) return;
            String action = a.replace("com.mist.player.", "").toLowerCase();
            String js = "(function(){try{window.dispatchEvent(new CustomEvent('mist-media-action',{detail:'" + action + "'}))}catch(e){}})()";
            try { getBridge().getWebView().post(() -> getBridge().getWebView().evaluateJavascript(js, null)); } catch (Exception ignored) {}
        }
    };
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // No registerPlugin() - auto-discovery handles MistMediaPlugin
        IntentFilter f = new IntentFilter("com.mist.player.MEDIA_ACTION");
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(mediaReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mediaReceiver, f);
        }
    }
    @Override public void onDestroy() {
        try { unregisterReceiver(mediaReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
