package de.augmentedmind.stopit.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.SoundPool
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import de.augmentedmind.stopit.*
import de.augmentedmind.stopit.db.Bookmark
import de.augmentedmind.stopit.db.BookmarkRepository
import de.augmentedmind.stopit.db.BookmarkRoomDatabase
import de.augmentedmind.stopit.service.StopitNotificationListenerService.Companion.isEnabled
import de.augmentedmind.stopit.ui.MainActivity
import de.augmentedmind.stopit.utils.BookmarkPlaybackSupport
import de.augmentedmind.stopit.utils.PlaybackSupportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class MediaCallbackService : Service(), OnSharedPreferenceChangeListener {
    private val controllers: MutableMap<String, MediaController> = HashMap()
    private val callbacks: MutableMap<String, MediaCB> = HashMap()
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private lateinit var sessionManager: MediaSessionManager
    private var initialized = false
    private var lastPauseTimestampMs: Long = 0
    private var lastMetaData: AudioMetadata? = null
    private lateinit var repository: BookmarkRepository
    private lateinit var shortPlayer: SoundPool
    private var beepSoundId = 0
    private var isNotificationActive = false
    private var seekModeActive = false
    private var seekModeStartTimestampMs: Long = 0

    // These values will be overwritten in onCreate() by the values from the SharedPreferences, and
    // will be kept up to date by the SharedPreferences listener
    private var thresholdMaxMs = 2000
    private var thresholdMinMs = 500
    private var thresholdNotificationMs = 2000
    private var useForeground = true
    private var shouldPlaySound = true
    private var vibrationDurationMs = 500
    private var shouldVibrate = true
    private var seekedBookmark: Bookmark? = null
    private val sessionListener = OnActiveSessionsChangedListener { controllers ->
        if (controllers == null) {
            return@OnActiveSessionsChangedListener
        }
        Log.d(TAG, "onActiveSessionsChanged():")
        val newPackageNames: MutableSet<String> = HashSet()
        for (c in controllers) {
            Log.d(TAG, getPackageAndSessionName(c))
            newPackageNames.add(getPackageAndSessionName(c))
        }

        // Unregister the ones we no longer need
        if (controllers.isEmpty()) {
            unregisterAllCallbacks()
            // When the user removes Notification listener permissions, this is also what happens!
            if (!isEnabled(applicationContext)) {
                initialized = false
                createUpdateOrRemoveForegroundNotification()
                Log.d(TAG, "onActiveSessionsChanged(): lost initialization!")
            }
        } else {
            // Note: we make a COPY of the keySet to avoid a ConcurrentModificationException
            for (packageAndSessionName in this@MediaCallbackService.controllers.keys.toTypedArray()) {
                if (!newPackageNames.contains(packageAndSessionName)) {
                    unregisterCallback(packageAndSessionName)
                }
            }

            // Register those we haven't registered yet
            for (c in controllers) {
                if (!this@MediaCallbackService.controllers.containsKey(getPackageAndSessionName(c))) {
                    registerCallback(c)
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == getString(R.string.key_enable_foreground)) {
            useForeground = sharedPreferences.getBoolean(key, false)
            Log.d(TAG, "Foreground pref changed: $useForeground")
            createUpdateOrRemoveForegroundNotification()
        } else {
            updateValueFromPreference(key, sharedPreferences.all[key]!!)
        }
    }

    private inner class MediaCB internal constructor(private val packageAndSessionName: String) : MediaController.Callback() {
        private var lastState = -1
        var cachedMetadata: AudioMetadata? = null
        var cachedMediaId: String? = null
        fun updateCachedMetadata(metadata: AudioMetadata?, mediaId: String?) {
            cachedMetadata = metadata
            cachedMediaId = mediaId
        }

        override fun onSessionDestroyed() {
            Log.d(TAG, "onSessionDestroyed")
            unregisterCallback(packageAndSessionName)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (state == null) {
                return
            }
            val newState = state.state
            if (newState == lastState) {
                return
            }
            if (newState != PlaybackState.STATE_PAUSED && newState != PlaybackState.STATE_PLAYING) {
                Log.d(TAG, "onPlaybackStateChanged: ignoring irrelevant state $newState")
                return
            }
            Log.d(TAG, "pos " + state.position + " last updttime " + state.lastPositionUpdateTime)
            val timestampSeconds = (state.position / 1000).toInt()
            Log.d(TAG, "timestampSeconds $timestampSeconds")
            val correspondingController = controllers[packageAndSessionName]
            processStateChange(newState, timestampSeconds, correspondingController, this)
            lastState = newState
            Log.d(TAG, "onPlaybackStateChanged " + state.state)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (metadata != null) {
                cachedMetadata = getAudioMetadataFromMediaMetadata(metadata)
                cachedMediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                Log.d(TAG, "onMetadataChanged $cachedMetadata ID: $cachedMediaId")
            }
        }

    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            Log.d(TAG, "onStartCommand(): Warning: intent was null, DAFUQ!")
            return START_STICKY
        }
        if (intent.action == null) {
            Log.d(TAG, "onStartCommand(): Warning: intent without action!")
            return START_STICKY
        }
        Log.d(TAG, "onStartCommand " + intent.action)
        if (intent.action == START_ACTION) {
            if (initialized) {
                if (!isEnabled(applicationContext)) {
                    initialized = false
                    createUpdateOrRemoveForegroundNotification()
                    Log.d(TAG, "returning, lost initialization!")
                } else {
                    Log.d(TAG, "returning, already initialized!")
                }
                return START_STICKY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val chan = NotificationChannel(CHANNEL_ID,
                        "StopIt Service", NotificationManager.IMPORTANCE_NONE)
                val manager = (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                manager.createNotificationChannel(chan)
            }
            if (useForeground) {
                createUpdateOrRemoveForegroundNotification()
            }
            setup()
        } else if (intent.action == PLAY_ACTION) {
            if (!initialized) {
                Log.d(TAG, "Cannot execute PLAY action, not initialized!")
                return START_STICKY
            }
            val bookmark: Bookmark = intent.getParcelableExtra("bookmark")
            val supportState = BookmarkPlaybackSupport.isPlaybackSupportedForPackage(bookmark.playerPackage)
            if (supportState == PlaybackSupportState.SUPPORTED_BY_MEDIA_ID || supportState == PlaybackSupportState.SUPPORTED_BY_QUERY) {
                var foundController = false
                for (packageAndSessionName in controllers.keys) {
                    if (packageAndSessionName.startsWith(bookmark.playerPackage)) {
                        // Simply try the first matching controller
                        val controller = controllers[packageAndSessionName]
                        val controls = controller!!.transportControls
                        if (supportState == PlaybackSupportState.SUPPORTED_BY_MEDIA_ID) {
                            val mediaId = bookmark.metadata
                            if (mediaId != null) {
                                controls.playFromMediaId(mediaId, null)
                                enableSeekMode(bookmark)
                                Log.d(TAG, "Play action executed!$mediaId")
                            }
                        } else {
                            // PlaybackSupportState.SUPPORTED_BY_QUERY
                            controls.playFromSearch(bookmark.track, null)
                            enableSeekMode(bookmark)
                            Log.d(TAG, "Play action executed!" + bookmark.track)
                        }
                        foundController = true
                        break
                    }
                }
                if (!foundController) {
                    Toast.makeText(applicationContext, String.format("App %s is not started, please start it " +
                            "first", bookmark.playerPackage), Toast.LENGTH_SHORT).show()
                }
            }
        }

        // If we get killed, after returning from here, restart
        return START_STICKY
    }

    override fun onCreate() {
        val bookmarkDao = BookmarkRoomDatabase.getDatabase(application).dao()
        repository = BookmarkRepository(bookmarkDao)
        shortPlayer = SoundPool.Builder().setMaxStreams(10).build()
        beepSoundId = shortPlayer.load(this, R.raw.beepogg, 1)
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPrefs.registerOnSharedPreferenceChangeListener(this)
        val prefsMap = sharedPrefs.all
        for ((key, value) in prefsMap) {
            updateValueFromPreference(key, value!!)
        }
        sessionManager = this.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    override fun onDestroy() {
        unregisterAllCallbacks()
        shortPlayer.release()
        super.onDestroy()
    }

    private fun enableSeekMode(bookmark: Bookmark) {
        seekModeStartTimestampMs = SystemClock.elapsedRealtime()
        seekedBookmark = bookmark
        seekModeActive = true
    }

    private fun isSeekModeActive(): Boolean {
        if (seekModeActive) {
            val now = SystemClock.elapsedRealtime()
            if (now - seekModeStartTimestampMs > 30 * 1000) {
                Log.d(TAG, "Seek-mode auto-disabled due to old timestamp: $now $seekModeStartTimestampMs")
                disableSeekMode()
            }
        }
        return seekModeActive
    }

    private fun disableSeekMode() {
        seekModeActive = false
        seekedBookmark = null
    }

    private fun setup() {
        if (isEnabled(applicationContext)) {
            sessionManager.addOnActiveSessionsChangedListener(sessionListener,
                    ComponentName(applicationContext, StopitNotificationListenerService::class.java))
            registerAllCallbacks()
            initialized = true
        }
    }

    private fun createUpdateOrRemoveForegroundNotification() {
        if (!useForeground) {
            stopForeground(true)
            isNotificationActive = false
            return
        }
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        val content: CharSequence
        content = if (isEnabled(applicationContext)) {
            getText(R.string.notification_message_ok)
        } else {
            getText(R.string.notification_message_perms_missing)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getText(R.string.notification_title))
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_settings_black_24dp)
                .setContentIntent(pendingIntent)
                .setTicker("Some ticker text")
                .setChannelId(CHANNEL_ID)
                .build()
        if (!isNotificationActive) {
            startForeground(ONGOING_NOTIFICATION_ID, notification)
            isNotificationActive = true
        } else {
            // Update existing notification
            val notManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notManager.notify(ONGOING_NOTIFICATION_ID, notification)
        }
    }

    private fun updateValueFromPreference(key: String, value: Any) {
        if (key == getString(R.string.key_pause_play_min_delay_millis)) {
            thresholdMinMs = value as Int
        } else if (key == getString(R.string.key_pause_play_max_delay_millis)) {
            thresholdMaxMs = value as Int
        } else if (key == getString(R.string.key_pause_play_notification_lookback)) {
            thresholdNotificationMs = value as Int
        } else if (key == getString(R.string.key_vibrate)) {
            shouldVibrate = value as Boolean
        } else if (key == getString(R.string.key_vibrate_duration)) {
            vibrationDurationMs = value as Int
        } else if (key == getString(R.string.key_play_sound)) {
            shouldPlaySound = value as Boolean
        } else if (key == getString(R.string.key_enable_foreground)) {
            useForeground = value as Boolean
        }
    }

    private fun unregisterAllCallbacks() {
        // Note: we make a COPY of the keySet to avoid a ConcurrentModificationException
        for (packageAndSessionName in controllers.keys.toTypedArray()) {
            unregisterCallback(packageAndSessionName)
        }
    }

    private fun registerAllCallbacks() {
        val controllers = sessionManager.getActiveSessions(
                ComponentName(this, StopitNotificationListenerService::class.java))
        for (controller in controllers) {
            registerCallback(controller)
        }
    }

    private fun unregisterCallback(packageAndSessionName: String) {
        if (controllers.containsKey(packageAndSessionName)) {
            val controller = controllers[packageAndSessionName]
            Log.d(TAG, "Unregistering controller callback for $packageAndSessionName")
            controller!!.unregisterCallback(callbacks[packageAndSessionName]!!)
            controllers.remove(packageAndSessionName)
            callbacks.remove(packageAndSessionName)
        }
    }

    private fun registerCallback(controller: MediaController) {
        val ownController = MediaController(applicationContext,
                controller.sessionToken)
        val callback = MediaCB(getPackageAndSessionName(controller))
        ownController.registerCallback(callback)
        controllers[getPackageAndSessionName(controller)] = ownController
        callbacks[getPackageAndSessionName(controller)] = callback
        Log.d(TAG, "Registered new controller callback " + getPackageAndSessionName(controller))
        val mediaMetadata = controller.metadata
        if (mediaMetadata != null) {
            val mediaId = mediaMetadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            callback.updateCachedMetadata(getAudioMetadataFromMediaMetadata(mediaMetadata), mediaId)
        }
    }

    private fun processStateChange(newState: Int, timestampSeconds: Int, controller: MediaController?,
                                   callback: MediaCB) {
        val metaData: AudioMetadata?
        val mediaId: String?
        val mediaMetadata = controller!!.metadata
        if (mediaMetadata == null) {
            Log.e(TAG, "processStateChange(): Unable to get metadata (null was returned), " +
                    "using cached one instead")
            metaData = callback.cachedMetadata
            mediaId = callback.cachedMediaId
        } else {
            metaData = getAudioMetadataFromMediaMetadata(mediaMetadata)
            mediaId = mediaMetadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        }
        if (isSeekModeActive()) {
            if (newState == PlaybackState.STATE_PLAYING && controller.packageName == seekedBookmark!!.playerPackage && metaData!!.track == seekedBookmark!!.track) {
                controller.transportControls.seekTo(1000 * seekedBookmark!!.timestampSeconds.toLong())
                Log.d(TAG, "Seek to " + seekedBookmark!!.timestampSeconds + " completed for "
                        + seekedBookmark!!.track)
                disableSeekMode()
            } else {
                Log.d(TAG, "In seek mode, but something was not right")
            }
        } else {
            // Normal mode, check whether we should create a new bookmark
            if (newState == PlaybackState.STATE_PAUSED) {
                lastPauseTimestampMs = SystemClock.elapsedRealtime()
                lastMetaData = metaData
            } else {
                if (lastMetaData == null) {
                    Log.d(TAG, "Warning: lastMetaData is null!")
                    return
                }
                if (metaData != lastMetaData) {
                    Log.d(TAG, "Current: " + metaData.toString())
                    if (lastMetaData == null) {
                        Log.d(TAG, "Old: null")
                    } else {
                        Log.d(TAG, "Old: " + lastMetaData.toString())
                    }
                    return
                }
                Log.d(TAG, "Play = True. Last TS: $lastPauseTimestampMs")
                val currentTimeMs = SystemClock.elapsedRealtime()
                val difference = currentTimeMs - lastPauseTimestampMs
                if (difference < thresholdMinMs) {
                    Log.d(TAG, "Discarding event " + metaData!!.track + " - difference is too small: " + difference)
                    return
                }
                if (hasRecentNotificationHappened(currentTimeMs, controller.packageName)) {
                    Log.d(TAG, "Discarding event " + metaData!!.track + " - there recently " +
                            "was a notification!")
                    return
                }
                if (difference < thresholdMaxMs) {
                    Log.d(TAG, "Triggered event: " + metaData!!.track + " ID: " + mediaId)
                    // FIXME will this work, hard-coding "id = 0" ???
                    val bookmark = Bookmark(id = 0, createdAt = System.currentTimeMillis(), artist = metaData.artist, album = metaData.album, track = metaData.track, timestampSeconds = timestampSeconds, playerPackage = controller.packageName, metadata = mediaId)
                    serviceScope.launch { repository.insert(bookmark) }
                    if (shouldPlaySound) {
                        playBeepSound()
                    }
                    if (shouldVibrate) {
                        vibrate()
                    }
                } else {
                    Log.d(TAG, "Difference too large: $difference")
                }
            }
        }
    }

    private fun hasRecentNotificationHappened(currentTimeMs: Long, ignoredPackageName: String): Boolean {
        for (packageName in StopitNotificationListenerService.lastNotificationTimestampsMs.keys) {
            if (packageName == ignoredPackageName) continue
            val notificationTimeDiff = currentTimeMs - StopitNotificationListenerService.lastNotificationTimestampsMs[packageName]!!
            if (notificationTimeDiff < thresholdNotificationMs) {
                return true
            } else {
                Log.d(TAG, "hasRecentNotificationHappened($packageName): ignored, diff was $notificationTimeDiff")
            }
        }
        return false
    }

    private fun getAudioMetadataFromMediaMetadata(mediaMetadata: MediaMetadata): AudioMetadata {
        var artist = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val album = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val track = mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (artist == null && album != null) {
            artist = album
        } else if (artist == null && album == null) {
            val description = mediaMetadata.description
            if (description.subtitle != null) artist = description.subtitle.toString()
        }
        return AudioMetadata(artist, album, track)
    }

    private fun playBeepSound() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val actualVolume = audioManager
                .getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        val maxVolume = audioManager
                .getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        val volume = actualVolume / maxVolume
        shortPlayer.play(beepSoundId, volume, volume, 1, 0, 1f)
    }

    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            // TODO migrate to modern API
            vibrator.vibrate(vibrationDurationMs.toLong())
        }
    }

    private fun getPackageAndSessionName(controller: MediaController): String {
        return (controller.packageName + "-"
                + Integer.toHexString(controller.sessionToken.hashCode()))
    }

    companion object {
        const val START_ACTION = "START_ACTION"
        const val PLAY_ACTION = "PLAY_ACTION"
        const val TAG = "MediaCallbackService"
        private const val ONGOING_NOTIFICATION_ID = 21434
        const val CHANNEL_ID = "MediaCallbackServiceChannel"
    }
}