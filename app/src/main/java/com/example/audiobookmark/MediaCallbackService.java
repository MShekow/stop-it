package com.example.audiobookmark;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MediaCallbackService extends Service {
    public static final String START_ACTION = "START_ACTION";
    public static final String PLAY_ACTION = "PLAY_ACTION";
    static final String TAG = "MediaCallbackService";
    private static final int ONGOING_NOTIFICATION_ID = 21434;
    public static final String CHANNEL_ID = "MediaCallbackServiceChannel";
    private static final int THRESHOLD_MAX_MS = 2000;
    private static final int THRESHOLD_MIN_MS = 500;

    private Map<String, MediaController> controllers = new HashMap<>();
    private Map<String, MediaCB> callbacks = new HashMap<>();
    private MediaSessionManager sessionManager = null;
    private boolean initialized = false;
    private long lastPauseTimestampMs = 0;
    private AudioMetadata lastMetaData = null;
    private BookmarkRepository repository = null;
    private SoundPool shortPlayer;
    private int beepSoundId = 0;

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
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            if (state == null) {
                return;
            }

            int newState = state.getState();
            if (newState == lastState) {
                return;
            }
            if (newState != PlaybackState.STATE_PAUSED && newState != PlaybackState.STATE_PLAYING) {
                Log.d(TAG, "onPlaybackStateChanged: ignoring irrelevant state " + newState);
                return;
            }

            Log.d(TAG, "pos " + state.getPosition() + " last updttime " + state.getLastPositionUpdateTime());
            int timestampSeconds = (int) (state.getPosition() / 1000);
            Log.d(TAG, "timestampSeconds " + timestampSeconds);
            MediaController correspondingController = controllers.get(packageName);
            processStateChange(newState, timestampSeconds, correspondingController);
            lastState = newState;
            Log.d(TAG, "onPlaybackStateChanged " + state.getState());
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
        if (intent == null) {
            Log.d(TAG, "onStartCommand(): Warning: intent was null, DAFUQ!");
            return START_STICKY;
        }

        if (intent.getAction() == null) {
            Log.d(TAG, "onStartCommand(): Warning: intent without action!");
            return START_STICKY;
        }

        Log.d(TAG, "onStartCommand " + intent.getAction());
        if (intent.getAction().equals(MediaCallbackService.START_ACTION)) {
            if (this.initialized) {
                Log.d(TAG, "returning, already initialized!");
                return START_STICKY;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel chan = new NotificationChannel(CHANNEL_ID,
                        "StopIt Service", NotificationManager.IMPORTANCE_NONE);
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                assert manager != null;
                manager.createNotificationChannel(chan);
            }

            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent =
                    PendingIntent.getActivity(this, 0, notificationIntent, 0);

            Notification notification =
                    new NotificationCompat.Builder(this, CHANNEL_ID)
                            .setContentTitle(getText(R.string.notification_title))
                            .setContentText(getText(R.string.notification_message))
                            .setSmallIcon(R.drawable.ic_settings_black_24dp)
                            .setContentIntent(pendingIntent)
                            .setTicker("Some ticker text")
                            .setChannelId(CHANNEL_ID)
                            .build();

            startForeground(ONGOING_NOTIFICATION_ID, notification);

            registerAllCallbacks();

            this.initialized = true;
        } else if (intent.getAction().equals(MediaCallbackService.PLAY_ACTION)) {
            // TODO build a whitelist of players where we know this works, and then also do the
            // seeking a bit later, as it won't have any effect if done immediately AND switching a
            // track

            String packageName = intent.getStringExtra("package");
            if (controllers.containsKey(packageName)) {
                String mediaId = intent.getStringExtra("mediaid");
                if (mediaId != null) {
                    MediaController controller = controllers.get(packageName);
                    MediaController.TransportControls controls = controller.getTransportControls();
                    controls.playFromMediaId(mediaId, null);
                    int timestampSeconds = intent.getIntExtra("timestamp", 0);
                    controls.seekTo(1000 * timestampSeconds);
                    Log.d(TAG, "Play action executed!" + mediaId);
                }
            }
        }


        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        repository = new BookmarkRepository(getApplicationContext());
        shortPlayer = new SoundPool.Builder().setMaxStreams(10).build();
        /*shortPlayer.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId,
                                       int status) {
                Log.d(TAG, "Load complete " + sampleId + " " + status);
            }
        });*/
        beepSoundId = shortPlayer.load(this, R.raw.beepogg, 1);
        sessionManager = (MediaSessionManager) this.getSystemService(Context.MEDIA_SESSION_SERVICE);
        sessionManager.addOnActiveSessionsChangedListener(new MediaSessionManager.OnActiveSessionsChangedListener() {
            @Override
            public void onActiveSessionsChanged(@Nullable List<MediaController> controllers) {
                if (controllers == null) {
                    return;
                }

                Log.d(TAG, "onActiveSessionsChanged():");
                Set<String> newPackageNames = new HashSet<>();
                for (MediaController c : controllers) {
                    Log.d(TAG, c.getPackageName() + " " + c.getSessionToken().toString());
                    newPackageNames.add(c.getPackageName());
                }

                // Unregister the ones we no longer need
                if (controllers.isEmpty()) {
                    unregisterAllCallbacks();
                } else {
                    // Note: we make a COPY of the keySet to avoid a ConcurrentModificationException
                    for (String packageName : MediaCallbackService.this.controllers.keySet().toArray(new String[0])) {
                        if (!newPackageNames.contains(packageName)) {
                            unregisterCallback(packageName);
                        }
                    }
                }

                // Register those we haven't registered yet
                for (MediaController c : controllers) {
                    if (!MediaCallbackService.this.controllers.containsKey(c.getPackageName())) {
                        registerCallback(c);
                    }
                }
            }
        }, new ComponentName(getApplicationContext(), NotificationListenerExampleService.class));
    }

    @Override
    public void onDestroy() {
        unregisterAllCallbacks();
        super.onDestroy();
    }

    private void unregisterAllCallbacks() {
        // Note: we make a COPY of the keySet to avoid a ConcurrentModificationException
        for (String packageName : controllers.keySet().toArray(new String[0])) {
            this.unregisterCallback(packageName);
        }
    }

    private void registerAllCallbacks() {
        List<MediaController> controllers = sessionManager.getActiveSessions(
                new ComponentName(this, NotificationListenerExampleService.class));
        for (MediaController controller : controllers) {
            registerCallback(controller);
        }
    }

    private void unregisterCallback(String packageName) {
        if (controllers.containsKey(packageName)) {
            MediaController controller = controllers.get(packageName);
            Log.d(TAG, "Unregistering controller callback for " + packageName);
            controller.unregisterCallback(callbacks.get(packageName));
            controllers.remove(packageName);
            callbacks.remove(packageName);
        }
    }

    private void registerCallback(MediaController controller) {
        MediaCB callback = new MediaCB(controller.getPackageName());
        MediaController ownController = new MediaController(getApplicationContext(),
                controller.getSessionToken());
        ownController.registerCallback(callback);
        controllers.put(controller.getPackageName(), ownController);
        callbacks.put(controller.getPackageName(), callback);
        Log.d(TAG, "Registered new controller callback " + controller.getPackageName());
    }

    private void processStateChange(int newState, int timestampSeconds, MediaController controller) {
        MediaMetadata mediaMetadata = controller.getMetadata();
        if (mediaMetadata == null) {
            Log.e(TAG, "processStateChange(): Unable to get metadata (null was returned)");
            return;
        }

        String artist = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        String album = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
        String track = mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE);

        AudioMetadata metaData = new AudioMetadata(artist, album, track);

        if (newState == PlaybackState.STATE_PAUSED) {
            lastPauseTimestampMs = SystemClock.elapsedRealtime();
            lastMetaData = metaData;
        } else {
            if (!metaData.equals(lastMetaData)) {
                Log.d(TAG, "Current: " + metaData.toString());
                if (lastMetaData == null) {
                    Log.d(TAG, "Old: null");
                } else {
                    Log.d(TAG, "Old: " + lastMetaData.toString());
                }
                return;
            }

            Log.d(TAG, "Play = True. Last TS: " + lastPauseTimestampMs);
            long currentTimeMs = SystemClock.elapsedRealtime();
            long difference = currentTimeMs - lastPauseTimestampMs;
            if (difference < THRESHOLD_MIN_MS) {
                Log.d(TAG, "Discarding event " + track + " - difference is too small: " + difference);
                return;
            }
            if (difference < THRESHOLD_MAX_MS) {
                Log.d(TAG, "Triggered event: " + track);
                Bookmark bookmark = new Bookmark();
                bookmark.createdAt = System.currentTimeMillis();
                bookmark.artist = artist;
                bookmark.album = album;
                bookmark.track = track;
                bookmark.timestampSeconds = timestampSeconds;
                bookmark.playerPackage = controller.getPackageName();
                bookmark.metadata = mediaMetadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
                repository.insert(bookmark);
                //MediaPlayer mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.beep);
                //mediaPlayer.start();
                playBeepSound();
            } else {
                Log.d(TAG, "Difference too large: " + difference);
            }
        }
    }

    private void playBeepSound() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        float actualVolume = (float) audioManager
                .getStreamVolume(AudioManager.STREAM_MUSIC);
        float maxVolume = (float) audioManager
                .getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float volume = actualVolume / maxVolume;
        shortPlayer.play(beepSoundId, volume, volume, 1, 0, 1f);

        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(500); // for 500 ms
        }
    }
}
