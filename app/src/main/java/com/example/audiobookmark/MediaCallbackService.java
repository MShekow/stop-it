package com.example.audiobookmark;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaDescription;
import android.media.MediaMetadata;
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
import androidx.preference.PreferenceManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MediaCallbackService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String START_ACTION = "START_ACTION";
    public static final String PLAY_ACTION = "PLAY_ACTION";
    static final String TAG = "MediaCallbackService";
    private static final int ONGOING_NOTIFICATION_ID = 21434;
    public static final String CHANNEL_ID = "MediaCallbackServiceChannel";

    private Map<String, MediaController> controllers = new HashMap<>();
    private Map<String, MediaCB> callbacks = new HashMap<>();
    private MediaSessionManager sessionManager = null;
    private boolean initialized = false;
    private long lastPauseTimestampMs = 0;
    private AudioMetadata lastMetaData = null;
    private BookmarkRepository repository = null;
    private SoundPool shortPlayer;
    private int beepSoundId = 0;
    private boolean isNotificationActive = false;

    // These values will be overwritten in onCreate() by the values from the SharedPreferences, and
    // will be kept up to date by the SharedPreferences listener
    private int thresholdMaxMs = 2000;
    private int thresholdMinMs = 500;
    private int thresholdNotificationMs = 2000;
    private boolean useForeground = true;
    private boolean shouldPlaySound = true;
    private boolean shouldVibrate = true;

    private MediaSessionManager.OnActiveSessionsChangedListener sessionListener = new MediaSessionManager.OnActiveSessionsChangedListener() {
        @Override
        public void onActiveSessionsChanged(@Nullable List<MediaController> controllers) {
            if (controllers == null) {
                return;
            }

            Log.d(TAG, "onActiveSessionsChanged():");
            Set<String> newPackageNames = new HashSet<>();
            for (MediaController c : controllers) {
                Log.d(TAG, getPackageAndSessionName(c));
                newPackageNames.add(getPackageAndSessionName(c));
            }

            // Unregister the ones we no longer need
            if (controllers.isEmpty()) {
                unregisterAllCallbacks();
                // When the user removes Notification listener permissions, this is also what happens!
                if (!StopitNotificationListenerService.isEnabled(getApplicationContext())) {
                    initialized = false;
                    createUpdateOrRemoveForegroundNotification();
                    Log.d(TAG, "onActiveSessionsChanged(): lost initialization!");
                }
            } else {
                // Note: we make a COPY of the keySet to avoid a ConcurrentModificationException
                for (String packageAndSessionName : MediaCallbackService.this.controllers.keySet().toArray(new String[0])) {
                    if (!newPackageNames.contains(packageAndSessionName)) {
                        unregisterCallback(packageAndSessionName);
                    }
                }

                // Register those we haven't registered yet
                for (MediaController c : controllers) {
                    if (!MediaCallbackService.this.controllers.containsKey(getPackageAndSessionName(c))) {
                        registerCallback(c);
                    }
                }
            }
        }
    };

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.key_enable_foreground))) {
            useForeground = sharedPreferences.getBoolean(key, false);
            Log.d(TAG, "Foreground pref changed: " + useForeground);
            createUpdateOrRemoveForegroundNotification();
        } else {
            updateValueFromPreference(key, sharedPreferences.getAll().get(key));
        }
    }

    private class MediaCB extends MediaController.Callback {
        private String packageAndSessionName;
        private int lastState = -1;
        private AudioMetadata cachedMetadata = null;
        private String cachedMediaId = null;

        MediaCB(String packageAndSessionName) {
            this.packageAndSessionName = packageAndSessionName;
        }

        void updateCachedMetadata(AudioMetadata metadata, String mediaId) {
            this.cachedMetadata = metadata;
            this.cachedMediaId = mediaId;
        }

        @Override
        public void onSessionDestroyed() {
            Log.d(TAG, "onSessionDestroyed");
            unregisterCallback(packageAndSessionName);
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
            MediaController correspondingController = controllers.get(packageAndSessionName);
            processStateChange(newState, timestampSeconds, correspondingController, this);
            lastState = newState;
            Log.d(TAG, "onPlaybackStateChanged " + state.getState());
        }

        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            if (metadata != null) {
                cachedMetadata = getAudioMetadataFromMediaMetadata(metadata);
                cachedMediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
                Log.d(TAG, "onMetadataChanged " + cachedMetadata + " ID: " + cachedMediaId);
            }
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
            if (initialized) {
                if (!StopitNotificationListenerService.isEnabled(getApplicationContext())) {
                    initialized = false;
                    createUpdateOrRemoveForegroundNotification();
                    Log.d(TAG, "returning, lost initialization!");
                } else {
                    Log.d(TAG, "returning, already initialized!");
                }

                return START_STICKY;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel chan = new NotificationChannel(CHANNEL_ID,
                        "StopIt Service", NotificationManager.IMPORTANCE_NONE);
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                assert manager != null;
                manager.createNotificationChannel(chan);
            }

            if (useForeground) {
                createUpdateOrRemoveForegroundNotification();
            }
            setup();
        } else if (intent.getAction().equals(MediaCallbackService.PLAY_ACTION)) {
            // TODO build a whitelist of players where we know this works, and then also do the
            // seeking a bit later, as it won't have any effect if done immediately AND switching a
            // track
            // For instance, for Pocket Casts, searching by ID works fine
            // But for Cast Box, only playFromSearch() works

            if (!initialized) {
                Log.d(TAG, "Cannot execute PLAY action, not initialized!");
                return START_STICKY;
            }

            String packageName = intent.getStringExtra("package");
            for (String packageAndSessionName : controllers.keySet()) {
                if (packageAndSessionName.startsWith(packageName)) {
                    // Simply try the first matching controller
                    String mediaId = intent.getStringExtra("mediaid");
                    if (mediaId != null) {
                        MediaController controller = controllers.get(packageAndSessionName);
                        MediaController.TransportControls controls = controller.getTransportControls();
                        controls.playFromMediaId(mediaId, null);
                        int timestampSeconds = intent.getIntExtra("timestamp", 0);
                        controls.seekTo(1000 * timestampSeconds);
                        Log.d(TAG, "Play action executed!" + mediaId);
                    }
                    break;
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
        beepSoundId = shortPlayer.load(this, R.raw.beepogg, 1);
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPrefs.registerOnSharedPreferenceChangeListener(this);
        Map<String, ?> prefsMap = sharedPrefs.getAll();
        for (Map.Entry<String, ?> entry : prefsMap.entrySet()) {
            updateValueFromPreference(entry.getKey(), entry.getValue());
        }
        sessionManager = (MediaSessionManager) this.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    @Override
    public void onDestroy() {
        unregisterAllCallbacks();
        shortPlayer.release();
        super.onDestroy();
    }

    private void setup() {
        if (StopitNotificationListenerService.isEnabled(getApplicationContext())) {
            sessionManager.addOnActiveSessionsChangedListener(sessionListener,
                    new ComponentName(getApplicationContext(), StopitNotificationListenerService.class));
            registerAllCallbacks();
            initialized = true;
        }
    }

    private void createUpdateOrRemoveForegroundNotification() {
        if (!useForeground) {
            stopForeground(true);
            return;
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        CharSequence content;
        if (StopitNotificationListenerService.isEnabled(getApplicationContext())) {
            content = getText(R.string.notification_message_ok);
        } else {
            content = getText(R.string.notification_message_perms_missing);
        }

        Notification notification =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle(getText(R.string.notification_title))
                        .setContentText(content)
                        .setSmallIcon(R.drawable.ic_settings_black_24dp)
                        .setContentIntent(pendingIntent)
                        .setTicker("Some ticker text")
                        .setChannelId(CHANNEL_ID)
                        .build();

        if (!isNotificationActive) {
            startForeground(ONGOING_NOTIFICATION_ID, notification);
            isNotificationActive = true;
        } else {
            // Update existing notification
            NotificationManager notManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notManager.notify(ONGOING_NOTIFICATION_ID, notification);
        }
    }

    private void updateValueFromPreference(String key, Object value) {
        if (key.equals(getString(R.string.key_pause_play_min_delay_millis))) {
            thresholdMinMs = (Integer) value;
        } else if (key.equals(getString(R.string.key_pause_play_max_delay_millis))) {
            thresholdMaxMs = (Integer) value;
        } else if (key.equals(getString(R.string.key_pause_play_notification_lookback))) {
            thresholdNotificationMs = (Integer) value;
        } else if (key.equals(getString(R.string.key_vibrate))) {
            shouldVibrate = (Boolean) value;
        } else if (key.equals(getString(R.string.key_play_sound))) {
            shouldPlaySound = (Boolean) value;
        } else if (key.equals(getString(R.string.key_enable_foreground))) {
            useForeground = (Boolean) value;
        }
    }

    private void unregisterAllCallbacks() {
        // Note: we make a COPY of the keySet to avoid a ConcurrentModificationException
        for (String packageAndSessionName : controllers.keySet().toArray(new String[0])) {
            this.unregisterCallback(packageAndSessionName);
        }
    }

    private void registerAllCallbacks() {
        List<MediaController> controllers = sessionManager.getActiveSessions(
                new ComponentName(this, StopitNotificationListenerService.class));
        for (MediaController controller : controllers) {
            registerCallback(controller);
        }
    }

    private void unregisterCallback(String packageAndSessionName) {
        if (controllers.containsKey(packageAndSessionName)) {
            MediaController controller = controllers.get(packageAndSessionName);
            Log.d(TAG, "Unregistering controller callback for " + packageAndSessionName);
            controller.unregisterCallback(callbacks.get(packageAndSessionName));
            controllers.remove(packageAndSessionName);
            callbacks.remove(packageAndSessionName);
        }
    }

    private void registerCallback(MediaController controller) {
        MediaController ownController = new MediaController(getApplicationContext(),
                controller.getSessionToken());
        MediaCB callback = new MediaCB(getPackageAndSessionName(controller));
        ownController.registerCallback(callback);
        controllers.put(getPackageAndSessionName(controller), ownController);
        callbacks.put(getPackageAndSessionName(controller), callback);
        Log.d(TAG, "Registered new controller callback " + getPackageAndSessionName(controller));
        MediaMetadata mediaMetadata = controller.getMetadata();
        if (mediaMetadata != null) {
            String mediaId = mediaMetadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
            callback.updateCachedMetadata(getAudioMetadataFromMediaMetadata(mediaMetadata), mediaId);
        }
    }

    private void processStateChange(int newState, int timestampSeconds, MediaController controller, MediaCB callback) {
        AudioMetadata metaData;
        String mediaId = null;
        MediaMetadata mediaMetadata = controller.getMetadata();
        if (mediaMetadata == null) {
            Log.e(TAG, "processStateChange(): Unable to get metadata (null was returned), " +
                    "using cached one instead");
            metaData = callback.cachedMetadata;
            mediaId = callback.cachedMediaId;
        } else {
            metaData = getAudioMetadataFromMediaMetadata(mediaMetadata);
            mediaId = mediaMetadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        }

        if (newState == PlaybackState.STATE_PAUSED) {
            lastPauseTimestampMs = SystemClock.elapsedRealtime();
            lastMetaData = metaData;
        } else {
            if (lastMetaData == null) {
                Log.d(TAG, "Warning: lastMetaData is null!");
                return;
            }

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
            if (difference < thresholdMinMs) {
                Log.d(TAG, "Discarding event " + metaData.track + " - difference is too small: " + difference);
                return;
            }

            if (hasRecentNotificationHappened(currentTimeMs, controller.getPackageName())) {
                Log.d(TAG, "Discarding event " + metaData.track + " - there recently " +
                        "was a notification!");
                return;
            }

            if (difference < thresholdMaxMs) {
                Log.d(TAG, "Triggered event: " + metaData.track);
                Bookmark bookmark = new Bookmark();
                bookmark.createdAt = System.currentTimeMillis();
                bookmark.artist = metaData.artist;
                bookmark.album = metaData.album;
                bookmark.track = metaData.track;
                bookmark.timestampSeconds = timestampSeconds;
                bookmark.playerPackage = controller.getPackageName();
                bookmark.metadata = mediaId;
                repository.insert(bookmark);
                if (shouldPlaySound) {
                    playBeepSound();
                }
                if (shouldVibrate) {
                    vibrate();
                }
            } else {
                Log.d(TAG, "Difference too large: " + difference);
            }
        }
    }

    private boolean hasRecentNotificationHappened(long currentTimeMs, String ignoredPackageName) {
        for (String packageName : StopitNotificationListenerService.lastNotificationTimestampsMs.keySet()) {
            if (packageName.equals(ignoredPackageName)) continue;

            long notificationTimeDiff = currentTimeMs - StopitNotificationListenerService.lastNotificationTimestampsMs.get(packageName);
            if (notificationTimeDiff < thresholdNotificationMs) {
                return true;
            } else {
                Log.d(TAG, "hasRecentNotificationHappened(" + packageName + "): ignored, diff was " + notificationTimeDiff);
            }
        }
        return false;
    }

    private AudioMetadata getAudioMetadataFromMediaMetadata(MediaMetadata mediaMetadata) {
        String artist = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        String album = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
        String track = mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE);

        if (artist == null && album != null) {
            artist = album;
        } else if (artist == null && album == null) {
            MediaDescription description = mediaMetadata.getDescription();
            if (description.getSubtitle() != null) artist = description.getSubtitle().toString();
        }

        return new AudioMetadata(artist, album, track);
    }

    private void playBeepSound() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        float actualVolume = (float) audioManager
                .getStreamVolume(AudioManager.STREAM_MUSIC);
        float maxVolume = (float) audioManager
                .getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float volume = actualVolume / maxVolume;
        shortPlayer.play(beepSoundId, volume, volume, 1, 0, 1f);
    }

    private void vibrate() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(500); // for 500 ms
        }
    }

    private String getPackageAndSessionName(MediaController controller) {
        return controller.getPackageName() + "-"
                + Integer.toHexString(controller.getSessionToken().hashCode());
    }
}
