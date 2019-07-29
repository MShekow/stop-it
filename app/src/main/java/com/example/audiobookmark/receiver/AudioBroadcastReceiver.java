package com.example.audiobookmark.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.example.audiobookmark.receiver.impl.DoggcatcherIntentProcessor;
import com.example.audiobookmark.receiver.impl.SpotifyIntentProcessor;

public class AudioBroadcastReceiver extends BroadcastReceiver {
    static final String GENERAL_ACTION = "com.android.music.playstatechanged";
    static final String TAG = "AudioBroadcastReceiver";

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

        IntentProcessor processor = this.getIntentProcessor(context, intent);
        if (processor != null) {
            processor.process();
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
