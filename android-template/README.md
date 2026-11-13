After `npx cap add android`, copy:
- android-plugin-src/com/mist/player/*.java -> android/app/src/main/java/com/mist/player/
- android-template/app/src/main/AndroidManifest.xml -> android/app/src/main/AndroidManifest.xml (merge permissions)
- android-template/app/build.gradle -> merge dependencies (media lib)

Then: npx cap sync android && cd android && ./gradlew assembleDebug
