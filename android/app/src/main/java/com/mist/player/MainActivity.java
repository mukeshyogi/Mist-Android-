package com.mist.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private final BroadcastReceiver mediaReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            String a = intent.getStringExtra("action");
            if (a == null) return;
            String action = a.replace("com.mist.player.", "").toLowerCase();
            // JS expects: MM.addListener('action', e => e.action === 'play'/'pause'/'next'/'prev')
            // We dispatch via webview JS to keep it simple and reliable:
            String js = "(function(){try{window.dispatchEvent(new CustomEvent('mist-media-action',{detail:'" + action + "'}))}catch(e){}})()";
            // Also try to call the plugin's notifyListeners via bridge is not public, so use JS bridge:
            // index.html already listens to MM 'action' - we shim it below in JS by mapping this event.
            getBridge().getWebView().post(() -> getBridge().getWebView().evaluateJavascript(js, null));
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerPlugin(MistMediaPlugin.class);
        IntentFilter f = new IntentFilter("com.mist.player.MEDIA_ACTION");
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(mediaReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mediaReceiver, f);
        }
    }
    @Override protected void onDestroy() {
        try { unregisterReceiver(mediaReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
