package com.example.audiobookmark;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MediaCallbackService extends Service {
    static final String TAG = "MediaCallbackService";
    private static final int ONGOING_NOTIFICATION_ID = 21434;
    public static final String CHANNEL_ID = "MediaCallbackServiceChannel";
    private static final boolean REGISTER_ON_SESSION_CHANGE = true;
    private static final boolean REREGISTER_AT_END_OF_HANDLER = true;
    private static final boolean REREGISTER_ONLY_NEW_PLAYERS = false;

    private Map<String, MediaCB> callbacks = new HashMap<>();
    private static long lastCbMs = 0;
    private static boolean isJobScheduled = false;
    private Looper serviceLooper;
    private ServiceHandler serviceHandler;


    private class MediaCB extends MediaController.Callback {
        private String packageName = null;
        private int lastState = -1;

        public MediaCB(String packageName) {
            this.packageName = packageName;
        }

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
            MediaCallbackService.lastCbMs = System.currentTimeMillis();
            if (state.getState() != this.lastState) {
                this.lastState = state.getState();
            } else {
                Log.d(TAG, "onPlaybackStateChanged - encountered duplicated state " + state.getState());
                return;
            }
            Log.d(TAG, "onPlaybackStateChanged " + state.getState());

            if (!MediaCallbackService.isJobScheduled) {
                MediaCallbackService.isJobScheduled = true;
                Message msg = serviceHandler.obtainMessage();
                msg.obj = MediaCB.this;
                serviceHandler.sendMessage(msg);
                Log.d(TAG, "Started background job");
            }

            /*Intent intent = new Intent(MediaCallbackService.this, MediaCallbackService.class);
            MediaCallbackService.this.startService(intent);
            Log.d(TAG, "Re-registered CB");*/
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
    }

    ;

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            MediaCB callback = (MediaCB) msg.obj;
            final long DELAY_MS = 200;
            while (true) {
                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                final long now = System.currentTimeMillis();
                if ((now - DELAY_MS) > MediaCallbackService.lastCbMs) {
                    Log.d(TAG, "Got out of the loop");
                    break;
                } else {
                    Log.d(TAG, "Still too fresh, waiting again - " + MediaCallbackService.lastCbMs + " - " + now);
                }
            }
            if (REREGISTER_AT_END_OF_HANDLER) {
                registerCallback(callback.packageName, null);
            }

            MediaCallbackService.isJobScheduled = false;
        }
    }


    public MediaCallbackService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        MediaSessionManager mm = (MediaSessionManager) this.getSystemService(
                Context.MEDIA_SESSION_SERVICE);
        List<MediaController> controllers = mm.getActiveSessions(
                new ComponentName(getApplicationContext(), NotificationListenerExampleService.class));
        this.registerCallbacks(controllers);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle(getText(R.string.notification_title))
                        .setContentText(getText(R.string.notification_message))
                        //.setSmallIcon(R.drawable.icon)
                        .setContentIntent(pendingIntent)
                        //.setTicker(getText(R.string.ticker_text))
                        .build();

        startForeground(ONGOING_NOTIFICATION_ID, notification);


        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        // Start up the thread running the service. Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block. We also make it
        // background priority so CPU-intensive work doesn't disrupt our UI.
        HandlerThread thread = new HandlerThread("MediaCBHandlerThread",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        serviceLooper = thread.getLooper();
        serviceHandler = new ServiceHandler(serviceLooper);

        MediaSessionManager mm = (MediaSessionManager) this.getSystemService(
                Context.MEDIA_SESSION_SERVICE);
        mm.addOnActiveSessionsChangedListener(new MediaSessionManager.OnActiveSessionsChangedListener() {
            @Override
            public void onActiveSessionsChanged(@Nullable List<MediaController> controllers) {
                Log.d(TAG, "onActiveSessionsChanged():");
                for (MediaController c : controllers) {
                    Log.d(TAG, c.getPackageName() + " " + c.getSessionToken().toString());
                }
                if (REGISTER_ON_SESSION_CHANGE) {
                    registerCallbacks(controllers);
                }
            }
        }, new ComponentName(getApplicationContext(), NotificationListenerExampleService.class));
    }


    private void registerCallbacks(List<MediaController> controllers) {
        for (MediaController controller : controllers) {
            MediaSession.Token test = controller.getSessionToken();
            Log.d(TAG, "registerCallbacks(): controller " + controller.getPackageName() + " " + test.toString());
            if (REREGISTER_ONLY_NEW_PLAYERS && callbacks.containsKey(controller.getPackageName())) {
                Log.d(TAG, "registerCallbacks(): skipping registration of KNOWN player" + controller.getPackageName());
                continue;
            }
            registerCallback(controller.getPackageName(), controller);
        }
    }

    private void registerCallback(String packageName, MediaController controller) {
        MediaCB callback = null;
        if (this.callbacks.containsKey(packageName)) {
            callback = this.callbacks.get(packageName);
        } else {
            callback = new MediaCB(packageName);
            this.callbacks.put(packageName, callback);
        }

        if (controller == null) {
            MediaSessionManager mm = (MediaSessionManager) this.getSystemService(
                    Context.MEDIA_SESSION_SERVICE);
            List<MediaController> controllers = mm.getActiveSessions(
                    new ComponentName(getApplicationContext(), NotificationListenerExampleService.class));
            for (MediaController c : controllers) {
                if (c.getPackageName().equals(packageName)) {
                    controller = c;
                    break;
                }
            }

        }

        if (controller != null) {
            controller.unregisterCallback(callback);
            controller.registerCallback(callback);
            Log.d(TAG, "(Re) registered CB for controller: " + controller.toString() + " " + packageName);
        }
    }
}
