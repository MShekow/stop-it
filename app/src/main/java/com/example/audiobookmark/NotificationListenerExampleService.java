package com.example.audiobookmark;

import android.content.Intent;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;

public class NotificationListenerExampleService extends NotificationListenerService {
    /**
     * This class doesn't really do anything, we just need it to be allowed to call
     * MediaSessionManager.getActiveSessions(...)
     */
    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
}
