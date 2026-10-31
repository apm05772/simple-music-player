package com.example.simplemusicplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/** Owns MediaPlayer and the notification shown during local audio playback. */
public class MusicService extends Service {

    private static final String TAG = "MusicService";
    private MediaPlayer mediaPlayer;
    private List<Song> songList;
    private int currentPosition = 0;
    private boolean isPlaying = false;
    private Song currentSong;
    private final IBinder binder = new MusicBinder();

    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setSongs(List<Song> songs) {
        this.songList = new ArrayList<>(songs);
        if (currentSong != null) {
            currentPosition = -1;
            for (int i = 0; i < songList.size(); i++) {
                if (songList.get(i).getPath().equals(currentSong.getPath())) {
                    currentPosition = i;
                    break;
                }
            }
        }
    }

    // Resolve the active track by path so list edits do not shift playback.
    public void playSongByPath(Song song) {
        if (song == null) return;
        // Find the track in the latest snapshot of the imported library.
        int newPosition = -1;
        if (songList != null) {
            for (int i = 0; i < songList.size(); i++) {
                if (songList.get(i).getPath().equals(song.getPath())) {
                    newPosition = i;
                    break;
                }
            }
        }
        if (newPosition != -1) currentPosition = newPosition;

        releasePlayer();
        currentSong = null;

        String path = song.getPath();
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepare();
            mediaPlayer.start();
            isPlaying = true;
            currentSong = song;

            mediaPlayer.setOnCompletionListener(mp -> playNext());

            showNotification(song.getTitle());
            Log.d(TAG, "Playing: " + song.getTitle());
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Error playing: " + path, e);
            releasePlayer();
            currentSong = null;
        }
    }

    public void playSong(int position) {
        if (songList != null && position >= 0 && position < songList.size()) {
            playSongByPath(songList.get(position));
        } else {
            Log.e(TAG, "Invalid position: " + position);
        }
    }

    public void pause() {
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            if (currentSong != null) showNotification(currentSong.getTitle());
        }
    }

    public void resume() {
        if (mediaPlayer != null && !isPlaying) {
            mediaPlayer.start();
            isPlaying = true;
            if (currentSong != null) showNotification(currentSong.getTitle());
        }
    }

    public void playNext() {
        if (songList == null || songList.isEmpty()) { stopPlayback(); return; }
        int nextPosition = currentPosition + 1;
        if (nextPosition >= songList.size()) {
            nextPosition = 0;
        }
        playSong(nextPosition);
    }

    public void playPrevious() {
        if (songList == null || songList.isEmpty()) { stopPlayback(); return; }
        int prevPosition = currentPosition - 1;
        if (prevPosition < 0) {
            prevPosition = 0;
        }
        playSong(prevPosition);
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public int getCurrentPosition() {
        return (mediaPlayer != null) ? mediaPlayer.getCurrentPosition() : 0;
    }

    public int getDuration() {
        return (mediaPlayer != null) ? mediaPlayer.getDuration() : 0;
    }

    public void seekTo(int position) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(position);
        }
    }

    public String getCurrentSongTitle() {
        return currentSong != null ? currentSong.getTitle() : "—";
    }

    public String getCurrentSongPath() {
        return currentSong == null ? "" : currentSong.getPath();
    }

    public void stopPlayback() {
        releasePlayer();
        currentSong = null;
        currentPosition = 0;
        stopForeground(true);
        stopSelf();
    }

    private void releasePlayer() {
        isPlaying = false;
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void showNotification(String songTitle) {
        String channelId = "music_player_channel";
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent prevIntent = new Intent(this, MusicService.class);
        prevIntent.setAction("PREV");
        PendingIntent prevPendingIntent = PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent playPauseIntent = new Intent(this, MusicService.class);
        playPauseIntent.setAction(isPlaying ? "PAUSE" : "PLAY");
        PendingIntent playPausePendingIntent = PendingIntent.getService(this, 2, playPauseIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent nextIntent = new Intent(this, MusicService.class);
        nextIntent.setAction("NEXT");
        PendingIntent nextPendingIntent = PendingIntent.getService(this, 3, nextIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("music")
                .setContentText(songTitle)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_media_previous, "prev", prevPendingIntent)
                .addAction(android.R.drawable.ic_media_pause, isPlaying ? "pause" : "play", playPausePendingIntent)
                .addAction(android.R.drawable.ic_media_next, "next", nextPendingIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(1))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notification);
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "music_player_channel",
                    "Music Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Currently playing song");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case "PREV":
                    playPrevious();
                    break;
                case "PLAY":
                    resume();
                    break;
                case "PAUSE":
                    pause();
                    break;
                case "NEXT":
                    playNext();
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}