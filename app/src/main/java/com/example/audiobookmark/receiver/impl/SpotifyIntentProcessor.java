package com.example.audiobookmark.receiver.impl;

import android.content.Context;
import android.content.Intent;

import com.example.audiobookmark.receiver.IntentProcessor;

public class SpotifyIntentProcessor extends IntentProcessor {
    public static final String SPOTIFY_ACTION = "com.spotify.music.playbackstatechanged";

    private static final String SPOTIFY_PACKAGE = "com.spotify.music";
    private int currentPlaybackPosition;

    public SpotifyIntentProcessor(Intent intent, Context context) {
        super(intent, context);
    }

    @Override
    protected boolean shouldDiscard() {
        boolean discard = super.shouldDiscard();
        if (discard) {
            return true;
        }

        currentPlaybackPosition = intent.getIntExtra("playbackPosition", -1);
        if (currentPlaybackPosition == -1) {
            return true;
        }
        currentPlaybackPosition = currentPlaybackPosition / 1000;
        return false;
    }

    @Override
    protected boolean isPaused() {
        return !intent.getBooleanExtra("playstate", false);
    }

    @Override
    protected int playbackPositionSeconds() {
        return currentPlaybackPosition;
    }

    @Override
    protected String getPackage() {
        return SPOTIFY_PACKAGE;
    }

    @Override
    protected String getCustomMetadata() {
        String metaData = intent.getStringExtra("id");
        if (metaData != null) {
            return metaData;
        }
        return super.getCustomMetadata();
    }
}
