package com.example.audiobookmark;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;

import androidx.core.app.NotificationManagerCompat;

public class NotificationListenerExampleService extends NotificationListenerService {
    /**
     * This class doesn't really do anything, we just need it to be allowed to call
     * MediaSessionManager.getActiveSessions(...)
     */
    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    // Helper method to check if our notification listener is enabled. In order to get active media
    // sessions, we need an enabled notification listener component.
    public static boolean isEnabled(Context context) {
        return NotificationManagerCompat
                .getEnabledListenerPackages(context)
                .contains(context.getPackageName());
    }
}
