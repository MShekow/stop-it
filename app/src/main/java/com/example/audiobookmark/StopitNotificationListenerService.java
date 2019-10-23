package com.example.audiobookmark;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

import java.util.HashMap;
import java.util.Map;

public class StopitNotificationListenerService extends NotificationListenerService {
    /**
     * This class is necessary to be able to call MediaSessionManager.getActiveSessions(...) and
     * to intercept notifications, because most audio players will pause and then resume their
     * playback when a notification happens, which is a similar signal to the user pressing pause
     * and play on purpose.
     */

    public static Map<String, Long> lastNotificationTimestampsMs = new HashMap<>();

    static final String TAG = "StopitNotifListenerServ";

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    public static boolean isEnabled(Context context) {
        return NotificationManagerCompat
                .getEnabledListenerPackages(context)
                .contains(context.getPackageName());
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn){
        // TODO refactor to keep the timestamp simply by checking that the package name is DIFFERENT to our own one!
        lastNotificationTimestampsMs.put(sbn.getPackageName(), SystemClock.elapsedRealtime());
        Log.d(TAG, "onNotificationPosted(): updated timestamp! " + sbn.getPackageName());
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        // This method is called when the user enables the Notification access in the settings!
        // But it is also called in other occasions, and also if it was already enabled
        // Thus we have to consider it with care!
        Log.d(TAG, "onListenerConnected()");
        Intent intent = new Intent(this, MediaCallbackService.class)
                .setAction(MediaCallbackService.START_ACTION);
        startService(intent);
    }
}
