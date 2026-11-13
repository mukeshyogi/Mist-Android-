MIST - FULL ANDROID BUILD (from your real files)

WHAT'S INSIDE:
- Your original main.js / preload.js / index.html (desktop still works: npm start)
- capacitor.config.ts (com.mist.player / Mist / www)
- package.json with Android deps
- android-plugin-src/ = MistMedia native plugin (Java)
  * scan() -> MediaStore query, returns {songs:[{id,title,artist,duration,path,added,folder}]}
  * update() -> notification + MediaSession (play/pause/next/prev)
  * stop() -> cancel notification
  * Handles READ_MEDIA_AUDIO (Android 13+) + READ_EXTERNAL_STORAGE (older)
- android-template/ = AndroidManifest + build.gradle to merge after `npx cap add android`
- .github/workflows/build-apk.yml -> auto APK on push
- www-shim.js -> fallback for notification buttons

HOW TO BUILD (first time, on your PC):
1. Unzip this over a fresh folder, copy icon.png + fonts/ from your original project into it
2. npm install
3. npm run build:web
4. npx cap add android
5. Copy plugin: cp -r android-plugin-src/* android/app/src/main/java/
   Copy manifest permissions from android-template/app/src/main/AndroidManifest.xml
   Add androidx.media:media:1.7.0 to android/app/build.gradle dependencies
6. npx cap sync android
7. npx cap open android -> Build > Build APK(s) > Build APK(s)
   OR: cd android && ./gradlew assembleDebug
   APK at: android/app/build/outputs/apk/debug/app-debug.apk

GITHUB (no Android Studio needed):
1. git init, git add ., git commit -m "Mist Android", git push to GitHub main
2. GitHub > Actions > Build Mist Android APK > Artifacts > Mist-debug-apk

IMPORTANT NOTES (from your code):
- Playback uses CAP.convertFileSrc('file://'+path) - works because plugin returns real file path from MediaStore.DATA
- On Android 14+, DATA column may be null for some files - those songs will be skipped. If you see missing songs, tell me and I'll switch to content:// + FileProvider copy.
- Desktop linked-folders (mistDesk) are ignored on Android - nativeScan() is used instead, exactly as your index.html already does.
- Notification buttons: MediaSession callback + MistMediaReceiver -> MainActivity -> window event -> www-shim.js
- Permissions: app will ask for Music/Audio on first scan. Allow it.

TEST CHECKLIST:
- Install APK, tap Add / Scan -> allow Music permission -> songs appear
- Play a song -> notification shows with controls
- Press Next in notification -> next song plays
- Press Back -> goes back in app, not exit (already in your index.html)
