package com.example.audiobookmark.receiver;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.SystemClock;
import android.util.Log;

import com.example.audiobookmark.AudioMetadata;
import com.example.audiobookmark.Bookmark;
import com.example.audiobookmark.BookmarkRepository;
import com.example.audiobookmark.R;

public abstract class IntentProcessor {
    private static long lastPauseTimestampMs = 0;
    private static AudioMetadata lastMetaData = null;
    private static final String TAG = "AudioBookmarkProcessor";
    private static final int THRESHOLD_MS = 2000;

    protected Intent intent;
    protected Context context;

    private String artist;
    private String album;
    private String track;

    public IntentProcessor(Intent intent, Context context) {
        this.intent = intent;
        this.context = context;
    }

    public void process() {
        artist = intent.getStringExtra("artist");
        album = intent.getStringExtra("album");
        track = intent.getStringExtra("track");

        if (this.shouldDiscard()) {
            return;
        }

        AudioMetadata metaData = new AudioMetadata(artist, album, track);

        if (this.isPaused()) {
            IntentProcessor.lastPauseTimestampMs = SystemClock.elapsedRealtime();
            IntentProcessor.lastMetaData = metaData;
        } else {
            if (!metaData.equals(IntentProcessor.lastMetaData)) {
                Log.d(TAG, "Current: " + metaData.toString());
                if (IntentProcessor.lastMetaData == null) {
                    Log.d(TAG, "Old: null");
                } else {
                    Log.d(TAG, "Old: " + IntentProcessor.lastMetaData.toString());
                }

                return;
            }
            Log.d(TAG, "Play = True. Last TS: " + IntentProcessor.lastPauseTimestampMs);
            long currentTimeMs = SystemClock.elapsedRealtime();
            if ((currentTimeMs - IntentProcessor.lastPauseTimestampMs) < IntentProcessor.THRESHOLD_MS) {
                Log.d(TAG, "Triggered event: " + track);
                Bookmark bookmark = new Bookmark();
                bookmark.createdAt = System.currentTimeMillis();
                bookmark.artist = artist;
                bookmark.album = album;
                bookmark.track = track;
                bookmark.timestampSeconds = this.playbackPositionSeconds();
                bookmark.playerPackage = this.getPackage();
                bookmark.metadata = this.getCustomMetadata();
                BookmarkRepository repository = new BookmarkRepository(context);
                repository.insert(bookmark);
                MediaPlayer mediaPlayer = MediaPlayer.create(context, R.raw.beep);
                mediaPlayer.start(); // no need to call prepare(); create() does that for you

            } else {
                long diff = currentTimeMs - IntentProcessor.lastPauseTimestampMs;
                Log.d(TAG, "Difference too large: " + diff);
            }
        }
    }

    protected boolean shouldDiscard() {
        return artist == null || artist.isEmpty() || album == null || album.isEmpty()
                || track == null || track.isEmpty();
    }

    protected abstract boolean isPaused();

    protected abstract int playbackPositionSeconds();

    protected abstract String getPackage();

    protected String getCustomMetadata() {
        return "";
    }
}
