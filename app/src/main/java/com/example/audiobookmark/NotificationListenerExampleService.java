package com.example.audiobookmark;

import android.content.Intent;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;

public class NotificationListenerExampleService extends NotificationListenerService {
    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
}
