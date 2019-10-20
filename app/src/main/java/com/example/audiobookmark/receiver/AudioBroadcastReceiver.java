package com.example.audiobookmark.receiver;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.audiobookmark.StopitNotificationListenerService;
import com.example.audiobookmark.receiver.impl.DoggcatcherIntentProcessor;
import com.example.audiobookmark.receiver.impl.SpotifyIntentProcessor;

import java.util.List;

public class AudioBroadcastReceiver extends BroadcastReceiver {
    static final String GENERAL_ACTION = "com.android.music.playstatechanged";
    static final String TAG = "AudioBroadcastReceiver";
    static boolean hasRegistered = false;

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

        final Context test = context;

        if (false && !AudioBroadcastReceiver.hasRegistered) {
            MediaSessionManager mm = (MediaSessionManager) context.getSystemService(
                    Context.MEDIA_SESSION_SERVICE);
            List<MediaController> controllers = mm.getActiveSessions(
                    new ComponentName(context, StopitNotificationListenerService.class));
            MediaController doggCatcherMediaController = null;
            for (MediaController controller : controllers) {
                if (controller.getPackageName().contains("spotify")) {
                    doggCatcherMediaController = controller;
                    break;
                }
            }

            if (doggCatcherMediaController != null) {
                Log.d(TAG, "Controller: " + doggCatcherMediaController.toString());
                doggCatcherMediaController.registerCallback(new MediaController.Callback() {
                    @Override
                    public void onSessionDestroyed() {
                        Log.d(TAG, "onSessionDestroyed");
                    }

                    @Override
                    public void onSessionEvent(@NonNull String event, @Nullable Bundle extras) {
                        Log.d(TAG, "onSessionEvent " + event);
                    }

                    @Override
                    public void onPlaybackStateChanged(@Nullable PlaybackState state) {
                        Log.d(TAG, "onPlaybackStateChanged " + state.getState());

                        MediaSessionManager mm = (MediaSessionManager) test.getSystemService(
                                Context.MEDIA_SESSION_SERVICE);
                        List<MediaController> controllers = mm.getActiveSessions(
                                new ComponentName(test, StopitNotificationListenerService.class));
                        MediaController doggCatcherMediaController = null;
                        for (MediaController controller : controllers) {
                            if (controller.getPackageName().contains("spotify")) {
                                doggCatcherMediaController = controller;
                                break;
                            }
                        }

                        if (doggCatcherMediaController != null) {
                            Log.d(TAG, "Controller: " + doggCatcherMediaController.toString());
                            doggCatcherMediaController.registerCallback(this);
                            Log.d(TAG, "RE-Registered CB");
                            AudioBroadcastReceiver.hasRegistered = true;
                        }
                    }

                    @Override
                    public void onMetadataChanged(@Nullable MediaMetadata metadata) {
                        Log.d(TAG, "onMetadataChanged");
                    }

                    @Override
                    public void onQueueChanged(@Nullable List<MediaSession.QueueItem> queue) {
                        Log.d(TAG, "onQueueChanged");
                    }

                    @Override
                    public void onQueueTitleChanged(@Nullable CharSequence title) {
                        Log.d(TAG, "onQueueTitleChanged");
                    }

                    @Override
                    public void onAudioInfoChanged(MediaController.PlaybackInfo info) {
                        Log.d(TAG, "onAudioInfoChanged");
                    }
                });
                Log.d(TAG, "Registered CB");
                AudioBroadcastReceiver.hasRegistered = true;
            }
        }

        IntentProcessor processor = this.getIntentProcessor(context, intent);
        if (processor != null) {
            //processor.process(); FIXME
        }
    }

    private IntentProcessor getIntentProcessor(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return null;
        }

        if (action.equals(AudioBroadcastReceiver.GENERAL_ACTION)) {
            String packageName = intent.getStringExtra("package");
            if (packageName != null && packageName.equals(DoggcatcherIntentProcessor.DOGGCATCHER_PACKAGE)) {
                return new DoggcatcherIntentProcessor(intent, context);
            }
        } else if (action.equals(SpotifyIntentProcessor.SPOTIFY_ACTION)) {
            return new SpotifyIntentProcessor(intent, context);
        }
        return null;
    }

}
