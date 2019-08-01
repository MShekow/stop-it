package com.example.audiobookmark.receiver.impl;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.util.Log;

import com.example.audiobookmark.NotificationListenerExampleService;
import com.example.audiobookmark.receiver.IntentProcessor;

import java.util.List;

public class DoggcatcherIntentProcessor extends IntentProcessor {
    public static final String DOGGCATCHER_PACKAGE = "com.snoggdoggler.android.applications.doggcatcher.v1_0";

    private static final String TAG = "DoggcatcherProcessor";
    private boolean isPaused;
    private int currentPlaybackPosition;

    public DoggcatcherIntentProcessor(Intent intent, Context context) {
        super(intent, context);
    }

    @Override
    protected boolean shouldDiscard() {
        boolean discard = super.shouldDiscard();
        if (discard) {
            return true;
        }
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
            return true;
        }

        // By sleeping a bit, we avoid problems with the playing state being wrong. Especially when
        // using the headset, the state would sometimes still be the old one (i.e. would state that
        // media is playing, even though it just paused, and vice versa)
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {}

        PlaybackState pbState = doggCatcherMediaController.getPlaybackState();
        if (pbState == null) {
            Log.d(TAG, "No PlaybackState");
            return true;
        }

        int currentPlaybackState = pbState.getState();
        this.isPaused = currentPlaybackState == PlaybackState.STATE_PAUSED;
        Log.d(TAG, "PlaybackState=" + currentPlaybackState);

        this.currentPlaybackPosition = (int) (pbState.getPosition() / 1000);
        return currentPlaybackState != PlaybackState.STATE_PAUSED
                && currentPlaybackState != PlaybackState.STATE_PLAYING;

    }

    @Override
    protected boolean isPaused() {
        return this.isPaused;
    }

    @Override
    protected int playbackPositionSeconds() {
        return this.currentPlaybackPosition;
    }

    @Override
    protected String getPackage() {
        return DOGGCATCHER_PACKAGE;
    }
}
