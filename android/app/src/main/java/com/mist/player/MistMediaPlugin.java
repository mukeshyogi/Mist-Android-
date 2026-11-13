package com.mist.player;

import android.Manifest;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONException;

@CapacitorPlugin(
  name = "MistMedia",
  permissions = {
    @Permission(strings = { Manifest.permission.READ_MEDIA_AUDIO }, alias = "audio13"),
    @Permission(strings = { Manifest.permission.READ_EXTERNAL_STORAGE }, alias = "audioLegacy")
  }
)
public class MistMediaPlugin extends Plugin {

    private MistMediaHelper helper;
    private PluginCall pendingScan;

    @Override
    public void load() {
        helper = new MistMediaHelper(getContext(), this);
    }

    // FIX: Helper calls this public method instead of protected notifyListeners
    public void emitAction(JSObject data) {
        notifyListeners("action", data, true);
    }

    @PluginMethod
    public void scan(PluginCall call) {
        if (!hasAudioPermission()) {
            pendingScan = call;
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissionForAlias("audio13", call, "onAudioPermission");
            } else {
                requestPermissionForAlias("audioLegacy", call, "onAudioPermission");
            }
            return;
        }
        call.resolve(scanSongs());
    }

    @PermissionCallback
    private void onAudioPermission(PluginCall call) {
        if (hasAudioPermission()) {
            if (pendingScan != null) pendingScan.resolve(scanSongs());
            else call.resolve(scanSongs());
        } else {
            if (pendingScan != null) pendingScan.reject("Allow music access to find your songs");
            else call.reject("Allow music access to find your songs");
        }
        pendingScan = null;
    }

    // FIX: getPermissionState returns PermissionState enum in Capacitor 6, not int
    private boolean hasAudioPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return getPermissionState("audio13") == PermissionState.GRANTED;
        }
        return getPermissionState("audioLegacy") == PermissionState.GRANTED;
    }

    private JSObject scanSongs() {
        JSObject out = new JSObject();
        JSArray arr = new JSArray();
        ContentResolver cr = getContext().getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Audio.Media.IS_MUSIC
        };
        String sel = MediaStore.Audio.Media.IS_MUSIC + "!=0 AND " + MediaStore.Audio.Media.DURATION + ">10000";
        try (Cursor c = cr.query(uri, proj, sel, null, MediaStore.Audio.Media.TITLE + " ASC")) {
            if (c != null) {
                int iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                int iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);
                int iBucket = c.getColumnIndexOrThrow(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME);
                while (c.moveToNext()) {
                    try {
                        JSObject s = new JSObject();
                        s.put("id", String.valueOf(c.getLong(iId)));
                        s.put("title", c.getString(iTitle));
                        String artist = c.getString(iArtist);
                        s.put("artist", (artist == null || artist.equals("<unknown>")) ? "Unknown artist" : artist);
                        s.put("duration", c.getLong(iDur));
                        s.put("path", c.getString(iData));
                        s.put("added", c.getLong(iAdded));
                        String folder = c.getString(iBucket);
                        s.put("folder", folder != null ? folder : "Music");
                        arr.put(s);
                    } catch (JSONException ignored) {}
                }
            }
        } catch (Exception e) {}
        try { out.put("songs", arr); } catch (JSONException ignored) {}
        return out;
    }

    @PluginMethod
    public void update(PluginCall call) {
        String title = call.getString("title", "");
        String artist = call.getString("artist", "");
        boolean playing = call.getBoolean("playing", false);
        int position = call.getInt("position", 0);
        int duration = call.getInt("duration", 0);
        helper.updateNotification(title, artist, playing, position, duration);
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        helper.stopNotification();
        call.resolve();
    }
}
