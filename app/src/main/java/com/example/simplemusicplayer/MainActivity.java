package com.example.simplemusicplayer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Local MP3 library, document-picker imports and playback controls. */
public class MainActivity extends AppCompatActivity {

    private static final int ADD_SONG_REQUEST = 200;

    private RecyclerView recyclerView;
    private SongAdapter songAdapter;
    private final List<Song> songList = new ArrayList<>();
    private final Set<String> songPathsSet = new HashSet<>();
    private MusicService musicService;
    private boolean isBound = false;
    private boolean bindingRequested = false;
    private ImageButton btnPlayPause;
    private Button btnNext, btnPrevious;
    private TextView currentSongText, tvStatus;
    private SeekBar seekBar;
    private ImageButton fabAdd;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateSeekBar;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isBound = true;
            musicService.setSongs(songList);
            setupSeekBarUpdater();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            musicService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        recyclerView = findViewById(R.id.recyclerView);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);
        btnPrevious = findViewById(R.id.btnPrevious);
        currentSongText = findViewById(R.id.currentSongText);
        seekBar = findViewById(R.id.seekBar);
        fabAdd = findViewById(R.id.fabAdd);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        songAdapter = new SongAdapter(songList);
        recyclerView.setAdapter(songAdapter);

        // Select a track; the service resolves it by path, not by a stale list index.
        songAdapter.setOnItemClickListener(position -> {
            if (isBound && musicService != null) {
                // Start only when the user actually selects a song, not when the library loads.
                startService(new Intent(this, MusicService.class));
                musicService.playSongByPath(songList.get(position));
                updatePlayPauseButton(musicService.isPlaying());
                currentSongText.setText(musicService.getCurrentSongTitle());
            }
        });

        // Confirm a local file removal before deleting it.
        songAdapter.setOnDeleteClickListener(position -> {
            new AlertDialog.Builder(this)
                    .setTitle("delete song")
                    .setMessage("delete " + songList.get(position).getTitle() + "?")
                    .setPositiveButton("yes", (dialog, which) -> deleteSong(position))
                    .setNegativeButton("no", null)
                    .show();
        });

        btnPlayPause.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                if (musicService.isPlaying()) {
                    musicService.pause();
                    updatePlayPauseButton(false);
                } else {
                    musicService.resume();
                    updatePlayPauseButton(musicService.isPlaying());
                }
                currentSongText.setText(musicService.getCurrentSongTitle());
            }
        });

        btnNext.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                musicService.playNext();
                updatePlayPauseButton(musicService.isPlaying());
                currentSongText.setText(musicService.getCurrentSongTitle());
            }
        });

        btnPrevious.setOnClickListener(v -> {
            if (isBound && musicService != null) {
                musicService.playPrevious();
                updatePlayPauseButton(musicService.isPlaying());
                currentSongText.setText(musicService.getCurrentSongTitle());
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && musicService != null) musicService.seekTo(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        fabAdd.setOnClickListener(v -> addMultipleSongs());

        loadSongsFromAppFolder();
    }

    private void setupSeekBarUpdater() {
        if (updateSeekBar != null) handler.removeCallbacks(updateSeekBar);
        updateSeekBar = new Runnable() {
            @Override
            public void run() {
                if (isBound && musicService != null) {
                    try {
                        int current = musicService.getCurrentPosition();
                        int duration = musicService.getDuration();
                        if (duration > 0) {
                            seekBar.setMax(duration);
                            seekBar.setProgress(current);
                        }
                        if (musicService.isPlaying()) {
                            currentSongText.setText(musicService.getCurrentSongTitle());
                        }
                    } catch (Exception e) { /* ignore */ }
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(updateSeekBar);
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        if (isPlaying) btnPlayPause.setImageResource(R.drawable.ic_pause);
        else btnPlayPause.setImageResource(R.drawable.ic_play);
    }

    private void loadSongsFromAppFolder() {
        new Thread(() -> {
            List<Song> tempList = new ArrayList<>();
            Set<String> tempSet = new HashSet<>();
            File baseDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            File appMusicDir = baseDir == null ? null : new File(baseDir, "musicapp");
            if (appMusicDir != null && appMusicDir.isDirectory()) {
                scanDirectory(appMusicDir, tempList, tempSet);
            }
            runOnUiThread(() -> {
                songList.clear();
                songPathsSet.clear();
                songList.addAll(tempList);
                songPathsSet.addAll(tempSet);
                songAdapter.updateList(songList);
                tvStatus.setText(songList.isEmpty()
                        ? "no songs. tap + to add mp3"
                        : songList.size() + " songs loaded");
                if (isBound && musicService != null) {
                    musicService.setSongs(songList);
                } else if (!songList.isEmpty()) {
                    bindMusicService();
                }
            });
        }).start();
    }

    private void scanDirectory(File directory, List<Song> list, Set<String> set) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    scanDirectory(file, list, set);
                } else if (file.getName().toLowerCase().endsWith(".mp3")) {
                    String path = file.getAbsolutePath();
                    if (!set.contains(path)) {
                        set.add(path);
                        String title = file.getName().substring(0, file.getName().length() - 4);
                        list.add(new Song(title, path));
                    }
                }
            }
        }
    }

    /** Import songs through Android's document picker; broad storage access is unnecessary. */
    private void addMultipleSongs() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/mpeg");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "select mp3 songs"), ADD_SONG_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != ADD_SONG_REQUEST || resultCode != RESULT_OK || data == null) {
            return;
        }
        List<Uri> imports = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                imports.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            imports.add(data.getData());
        }
        if (imports.isEmpty()) return;

        tvStatus.setText("importing " + imports.size() + " song(s)...");
        new Thread(() -> {
            int failures = 0;
            for (Uri uri : imports) {
                try {
                    copySongToAppFolder(uri);
                } catch (IOException | SecurityException e) {
                    failures++;
                    android.util.Log.e("SimpleMusicPlayer", "Unable to import audio", e);
                }
            }
            final int failedCount = failures;
            runOnUiThread(() -> {
                loadSongsFromAppFolder();
                if (failedCount > 0) {
                    android.widget.Toast.makeText(this, failedCount + " import(s) failed",
                            android.widget.Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    /** Copy into app-specific music storage without overwriting an existing file. */
    private void copySongToAppFolder(Uri uri) throws IOException {
        File root = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (root == null) throw new IOException("App music storage is unavailable");
        File directory = new File(root, "musicapp");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create music directory");
        }
        String providedName = getFileNameFromUri(uri);
        String safeName = providedName == null ? "song.mp3" :
                providedName.replace('\\', '/').replaceAll(".*/", "");
        safeName = safeName.replaceAll("[\\x00-\\x1F]", "_").trim();
        if (safeName.isEmpty() || safeName.equals(".") || safeName.equals("..")) safeName = "song.mp3";
        if (!safeName.toLowerCase(java.util.Locale.ROOT).endsWith(".mp3")) safeName += ".mp3";
        String baseName = safeName.substring(0, safeName.length() - 4);
        File destination = new File(directory, safeName);
        int suffix = 2;
        while (destination.exists()) {
            destination = new File(directory, baseName + " (" + suffix++ + ").mp3");
        }

        File temporary = File.createTempFile("import-", ".part", directory);
        try {
            try (InputStream inputStream = getContentResolver().openInputStream(uri);
                 FileOutputStream outputStream = new FileOutputStream(temporary)) {
                if (inputStream == null) throw new IOException("Cannot open selected file");
                byte[] buffer = new byte[8192];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, length);
                }
            }
            if (!temporary.renameTo(destination)) throw new IOException("Cannot finish import");
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) fileName = cursor.getString(nameIndex);
                }
            }
        }
        if (fileName == null && uri.getPath() != null) {
            fileName = new File(uri.getPath()).getName();
        }
        return fileName;
    }

    private void deleteSong(int position) {
        if (position < 0 || position >= songList.size()) return;
        Song song = songList.get(position);
        File file = new File(song.getPath());
        if (file.exists() && !file.delete()) {
            tvStatus.setText("could not delete " + song.getTitle());
            return;
        }
        boolean deletingCurrent = musicService != null &&
                musicService.getCurrentSongPath().equals(song.getPath());
        if (deletingCurrent) {
            musicService.stopPlayback();
            updatePlayPauseButton(false);
            currentSongText.setText("—");
        }

        songList.remove(position);
        songPathsSet.remove(song.getPath());
        songAdapter.updateList(songList);
        tvStatus.setText(songList.size() + " songs loaded");

        if (isBound && musicService != null) {
            musicService.setSongs(songList);
        }
    }

    private void bindMusicService() {
        if (!bindingRequested) {
            Intent serviceIntent = new Intent(this, MusicService.class);
            bindingRequested = bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bindingRequested) unbindService(serviceConnection);
        if (updateSeekBar != null) handler.removeCallbacks(updateSeekBar);
    }
}