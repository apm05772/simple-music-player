# Simple Music Player

A small **offline Android MP3 player** written in Java. It imports audio through Android's system document picker into app-specific storage and provides a song list, playback controls, a seek bar and a media notification.

## Features

- Import one or multiple MP3 files using the system document picker.
- Maintain a simple local song library in the app's own Music folder.
- Play, pause, resume, skip, seek and remove local songs.
- Show playback status and media control actions in a notification.
- Preserve imported music when restarting the app (until the app data is cleared or the app is uninstalled).

## Project layout

| Path | Purpose |
| --- | --- |
| `app/src/main/java/com/example/simplemusicplayer/MainActivity.java` | UI, document import and service binding |
| `app/src/main/java/com/example/simplemusicplayer/MusicService.java` | `MediaPlayer` and foreground media notification |
| `app/src/main/java/com/example/simplemusicplayer/Song.java` | Local song model |
| `app/src/main/java/com/example/simplemusicplayer/SongAdapter.java` | RecyclerView song list |
| `app/src/main/res/` | Layouts and visual resources |
| `app/src/test/` | Starter unit-test project |

## Build and run

Prerequisites: Android Studio, Android SDK 34, **JDK 17 or 21**, and an Android device/emulator running Android 6.0+ (API 23). Build tooling: Android Gradle Plugin 8.3.2 + Gradle 8.5.

1. Open the root project directory in Android Studio.
2. Sync Gradle (download the SDK and dependencies when prompted).
3. Build and run the `app` target.
4. Tap **+**, choose one or more MP3 documents and let them import. Imported tracks appear in the library; tap a track to play it.

Command-line checks with the Android SDK installed:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

## Implementation notes

- The document picker gives temporary access to **user-selected** audio files. No broad storage permission is requested.
- Imports run off the main UI thread, use temporary files and assign unique filenames instead of overwriting existing tracks.
- Playback starts the media service **on demand**. Android 14+ foreground service declarations use `mediaPlayback`.
- The app requests the notification permission in its manifest for Android 13+, but the user may need to grant notification access in Android settings to see media controls in the notification shade.
- The content picker is currently configured for MP3 (`audio/mpeg`); streaming services and unsupported audio formats are not implemented.

## Limitations

This is a learning/prototype project, not a feature-complete media library. There are no playlists, album-art metadata, automatic sorting or audio-focus/call handling yet. Test background media behavior on actual devices before a production release.
