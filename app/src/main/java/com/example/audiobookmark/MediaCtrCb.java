package com.example.audiobookmark;

import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.util.Log;

public class MediaCtrCb extends MediaController.Callback {
    @Override
    public void onPlaybackStateChanged(PlaybackState state) {
        Log.d("MediaCtrCb", state.toString());
    }

    @Override
    public void onSessionDestroyed() {
        Log.d("MediaCtrCb", "Session destroyed");

    }
}
