package com.example.audiobookmark;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.util.Log;

import java.util.List;

public class AudioBroadcastReceiver extends BroadcastReceiver {
    static String TAG = "AudioBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, intent.getAction());
        /*Bundle bundle = intent.getExtras();

        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            Log.d(TAG, String.format("%s %s (%s)", key, value.toString(), value.getClass().getName()));
        }*/

        MediaSessionManager mm = (MediaSessionManager) context.getSystemService(
                Context.MEDIA_SESSION_SERVICE);
        List<MediaController> controllers = mm.getActiveSessions(
                new ComponentName(context, NotificationListenerExampleService.class));
        for (MediaController controller : controllers) {
            Log.d(TAG, controller.getPlaybackState().toString());
        }
    }
}
