package com.example.audiobookmark;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;

public class AudioBroadcastReceiver extends BroadcastReceiver {
    static final String SPOTIFY_ACTION = "com.spotify.music.playbackstatechanged";
    static final String GENERAL_ACTION = "com.android.music.playstatechanged";
    static final String DOGGCATCHER_PACKAGE = "com.snoggdoggler.android.applications.doggcatcher.v1_0";
    static final String TAG = "AudioBroadcastReceiver";
    static final int THRESHOLD_MS = 2000;

    private static long lastPauseTimestampMs = 0;
    private static AudioMetadata lastMetaData = null;

    /*
     *
     */

    @Override
    public void onReceive(Context context, Intent intent) {
        if (false) {
            Log.d(TAG, intent.getAction());
            Bundle bundle = intent.getExtras();

            for (String key : bundle.keySet()) {
                Object value = bundle.get(key);
                String strVal = "UNKNOWN";
                String className = "UNKNOWN";
                try {
                    strVal = value.toString();
                    className = value.getClass().getName();
                } catch (Exception e) {}
                Log.d(TAG, String.format("%s %s (%s)", key, strVal, className));
            }
        }

        String action = intent.getAction();
        if (action == null) {
            return;
        }

        if (action.equals(AudioBroadcastReceiver.GENERAL_ACTION)) {
            String packageName = intent.getStringExtra("package");
            if (packageName == null
                    || !packageName.equals(AudioBroadcastReceiver.DOGGCATCHER_PACKAGE)) {
                return;
            }
            this.parseDoggcatcherIntent(context, intent);
        } else if (action.equals(AudioBroadcastReceiver.SPOTIFY_ACTION)) {
            this.parseSpotifyIntent(context, intent);
        }


        /*MediaSessionManager mm = (MediaSessionManager) context.getSystemService(
                Context.MEDIA_SESSION_SERVICE);
        List<MediaController> controllers = mm.getActiveSessions(
                new ComponentName(context, NotificationListenerExampleService.class));
        /*for (MediaController controller : controllers) {
            Log.d(TAG, controller.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE));
        }*/
    }

    private void parseDoggcatcherIntent(Context context, Intent intent) {
        Log.d(TAG, "parseDoggcatcherIntent() called");
        String artist = intent.getStringExtra("artist");
        String album = intent.getStringExtra("album");
        String track = intent.getStringExtra("track");
        // int positionMillis = intent.getIntExtra("position", -1);
        AudioMetadata metaData = new AudioMetadata(artist, album, track);

        // "playing" String extra exists, but it is buggy and always contains "true", so let's get
        // the value some other way
        MediaSessionManager mm = (MediaSessionManager) context.getSystemService(
                Context.MEDIA_SESSION_SERVICE);
        List<MediaController> controllers = mm.getActiveSessions(
                new ComponentName(context, NotificationListenerExampleService.class));
        MediaController doggCatcherMediaController = null;
        for (MediaController controller : controllers) {
            if (controller.getPackageName().contains("doggcatcher")) {
                doggCatcherMediaController = controller;
                break;
            }
        }
        if (doggCatcherMediaController == null) {
            Log.d(TAG, "Found no media controller");
            return;
        }

        PlaybackState pbState = doggCatcherMediaController.getPlaybackState();
        if (pbState == null) {
            Log.d(TAG, "No PlaybackState");
            return;
        }
        if (pbState.getState() == PlaybackState.STATE_PAUSED) {
            AudioBroadcastReceiver.lastPauseTimestampMs = SystemClock.elapsedRealtime();
            AudioBroadcastReceiver.lastMetaData = metaData;
        } else if (pbState.getState() == PlaybackState.STATE_PLAYING) {
            if (!metaData.equals(AudioBroadcastReceiver.lastMetaData)) {
                Log.d(TAG, "Current: " + metaData.toString());
                Log.d(TAG, "Old: " + AudioBroadcastReceiver.lastMetaData.toString());
                return;
            }
            Log.d(TAG, "Play = True. Last TS: " + AudioBroadcastReceiver.lastPauseTimestampMs);
            long currentTimeMs = SystemClock.elapsedRealtime();
            if ((currentTimeMs - AudioBroadcastReceiver.lastPauseTimestampMs) < AudioBroadcastReceiver.THRESHOLD_MS) {
                Log.d(TAG, "Triggered event: " + track);
            }else {
                long diff = currentTimeMs - AudioBroadcastReceiver.lastPauseTimestampMs;
                Log.d(TAG, "Difference too large: " + diff);
            }
        }
    }

    private void parseSpotifyIntent(Context context, Intent intent) {
        // playstate or playing both have the same value!
        boolean playState = intent.getBooleanExtra("playstate", false);
        Bundle bundle = intent.getExtras();
        boolean playStateForReal = bundle.getBoolean("playstate", false);
        Log.d(TAG, "Spotify playstate " + playState);
        Log.d(TAG, "Spotify playstate REAL " + playStateForReal);

        String artist = intent.getStringExtra("artist");
        String album = intent.getStringExtra("album");
        String track = intent.getStringExtra("track");
        AudioMetadata metaData = new AudioMetadata(artist, album, track);

        if (!playState) {
            AudioBroadcastReceiver.lastPauseTimestampMs = SystemClock.elapsedRealtime();
            AudioBroadcastReceiver.lastMetaData = metaData;
            Log.d(TAG, "Updated lastTS to " + AudioBroadcastReceiver.lastPauseTimestampMs);
        } else {
            if (!metaData.equals(AudioBroadcastReceiver.lastMetaData)) {
                Log.d(TAG, "Last metadata does not match");
                return;
            }
            Log.d(TAG, "Play = True. Last TS: " + AudioBroadcastReceiver.lastPauseTimestampMs);
            long currentTimeMs = SystemClock.elapsedRealtime();
            if ((currentTimeMs - AudioBroadcastReceiver.lastPauseTimestampMs) < AudioBroadcastReceiver.THRESHOLD_MS) {
                Log.d(TAG, "Triggered event: " + track);
            }
        }
        // int positionMillis = intent.getIntExtra("position", -1);
        // artist may coincide with album
    }
}
