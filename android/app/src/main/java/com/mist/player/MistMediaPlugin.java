package com.mist.player;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import androidx.documentfile.provider.DocumentFile;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.PluginCall;
import java.util.ArrayList;
import java.util.List;
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
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {}
        out.put("songs", arr);
        return out;
    }

    private static final int REQ_PICK_FOLDER = 9911;
    private PluginCall pendingPickFolder;

    // REAL folder picker: shows folders with "Use this folder" button (SAF)
    @PluginMethod
    public void pickFolder(PluginCall call) {
        pendingPickFolder = call;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(call, intent, REQ_PICK_FOLDER);
        } catch (Exception e) {
            pendingPickFolder = null;
            call.reject("Cannot open folder picker: " + e.getMessage());
        }
    }

    @Override
    protected void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
        super.handleOnActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_FOLDER) return;
        PluginCall call = pendingPickFolder;
        pendingPickFolder = null;
        if (call == null) return;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            call.reject("Folder not selected");
            return;
        }
        Uri treeUri = data.getData();
        try {
            getContext().getContentResolver().takePersistableUriPermission(treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        JSObject out = new JSObject();
        JSArray arr = new JSArray();
        try {
            DocumentFile root = DocumentFile.fromTreeUri(getContext(), treeUri);
            String folderName = (root != null && root.getName() != null) ? root.getName() : "Music";
            out.put("folder", folderName);
            out.put("treeUri", treeUri.toString());
            if (root != null) collectAudio(root, folderName, arr);
        } catch (Exception e) {
            call.reject("Cannot read folder: " + e.getMessage());
            return;
        }
        out.put("songs", arr);
        call.resolve(out);
    }

    private void collectAudio(DocumentFile dir, String folder, JSArray arr) {
        DocumentFile[] files = dir.listFiles();
        if (files == null) return;
        for (DocumentFile f : files) {
            try {
                if (f.isDirectory()) { collectAudio(f, folder, arr); continue; }
                if (!f.isFile()) continue;
                String name = f.getName();
                if (name == null) continue;
                String lower = name.toLowerCase();
                if (!(lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac")
                    || lower.endsWith(".flac") || lower.endsWith(".wav") || lower.endsWith(".ogg")
                    || lower.endsWith(".opus") || lower.endsWith(".oga") || lower.endsWith(".weba"))) continue;
                JSObject s = new JSObject();
                s.put("name", name);
                s.put("uri", f.getUri().toString());
                s.put("size", f.length());
                s.put("folder", folder);
                arr.put(s);
            } catch (Exception ignored) {}
        }
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
