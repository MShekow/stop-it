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
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import de.augmentedmind.stopit.R
import de.augmentedmind.stopit.db.Bookmark
import de.augmentedmind.stopit.service.StopitNotificationListenerService.Companion.isEnabled
import de.augmentedmind.stopit.ui.MainActivity
import java.util.*

/**
 * Uses the Media API to get access to MediaControllers, and registers callbacks.
 */
class MediaCallbackService : Service(), OnSharedPreferenceChangeListener {
    // Maps from audio app's package name + session name to our own MediaController instance copy
    private val controllers: MutableMap<String, MediaController> = HashMap()
    // Maps from audio app's package name + session name to our own MediaController instance copy
    private val callbacks: MutableMap<String, MediaCB> = HashMap()
    private lateinit var sessionManager: MediaSessionManager
    private var initialized = false
    private var isNotificationActive = false
    private lateinit var stateChangeProcessor: PlaybackStateChangeProcessor

    // useForeground will be overwritten in onCreate(), and will be kept up to date by the
    // SharedPreferences listener
    private var useForeground = true
    private val sessionListener = OnActiveSessionsChangedListener { controllers ->
        if (controllers == null) {
            return@OnActiveSessionsChangedListener
        }

        val newPackageNames = controllers.associateBy(keySelector = { Utils.getPackageAndSessionName(it) }, valueTransform = { it })
        Log.d(TAG, "onActiveSessionsChanged(): ${newPackageNames.keys}")

        // Unregister the ones we no longer need
        if (controllers.isEmpty()) {
            unregisterAllCallbacks()
            // If the user removed Notification listener permissions, this would also trigger this
            // OnActiveSessionsChangedListener callback
            if (!isEnabled(applicationContext)) {
                initialized = false
                createUpdateOrRemoveForegroundNotification()
                Log.d(TAG, "onActiveSessionsChanged(): lost Notification listener permissions!")
            }
        } else {
            val removedControllers = this@MediaCallbackService.controllers.keys - newPackageNames.keys
            removedControllers.forEach { unregisterCallback(it) }

            val addedControllers = newPackageNames.keys - this@MediaCallbackService.controllers.keys
            addedControllers.forEach { registerCallback(newPackageNames[it]!!) }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == getString(R.string.key_enable_foreground)) {
            useForeground = sharedPreferences.getBoolean(key, false)
            Log.d(TAG, "Foreground pref changed: $useForeground")
            createUpdateOrRemoveForegroundNotification()
        }
    }

    private inner class MediaCB internal constructor(private val packageAndSessionName: String) : MediaController.Callback() {
        private var lastState = -1
        private var cachedMetadata: AudioMetadata? = null
        fun setInitialCachedMetadata(metadata: AudioMetadata) {
            cachedMetadata = metadata
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
            Log.d(TAG, "onPlaybackStateChanged $newState (last: $lastState)")
            if (newState == lastState) {
                Log.d(TAG, "onPlaybackStateChanged(): ignoring new state $newState because " +
                        "it matches the old state")
                return
            }
            if (newState != PlaybackState.STATE_PAUSED && newState != PlaybackState.STATE_PLAYING) {
                Log.d(TAG, "onPlaybackStateChanged(): ignoring irrelevant state $newState")
                return
            }
            val correspondingController = controllers[packageAndSessionName]!!
            stateChangeProcessor.processStateChange(state, correspondingController,
                    this.cachedMetadata)
            lastState = newState
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (metadata != null) {
                cachedMetadata = Utils.getAudioMetadataFromMediaMetadata(metadata)
                Log.d(TAG, "onMetadataChanged $cachedMetadata")
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
            val bookmark: Bookmark? = intent.getParcelableExtra("bookmark")
            if (bookmark != null) {
                stateChangeProcessor.playBookmark(bookmark, controllers)
            }
        }

        // If we get killed, after returning from here, restart
        return START_STICKY
    }

    override fun onCreate() {
        stateChangeProcessor = PlaybackStateChangeProcessor(applicationContext)
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPrefs.registerOnSharedPreferenceChangeListener(this)
        sharedPrefs.all.forEach { updateValueFromPreference(it.key, it.value!!) }
        sessionManager = this.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    override fun onDestroy() {
        unregisterAllCallbacks()
        stateChangeProcessor.releaseResources()
        super.onDestroy()
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
        if (key == getString(R.string.key_enable_foreground)) {
            useForeground = value as Boolean
        }
    }

    private fun unregisterAllCallbacks() {
        // Note: we make a COPY of the keys to avoid a ConcurrentModificationException
        controllers.keys.toList().forEach { packageAndSessionName -> unregisterCallback(packageAndSessionName) }
    }

    private fun registerAllCallbacks() {
        val controllers = sessionManager.getActiveSessions(
                ComponentName(this, StopitNotificationListenerService::class.java))
        controllers.forEach { registerCallback(it) }
    }

    private fun unregisterCallback(packageAndSessionName: String) {
        val controller = controllers[packageAndSessionName] ?: return
        val mediaCb = callbacks[packageAndSessionName] ?: return
        Log.d(TAG, "Unregistering controller callback for $packageAndSessionName")
        controller.unregisterCallback(mediaCb)
        controllers.remove(packageAndSessionName)
        callbacks.remove(packageAndSessionName)
    }

    private fun registerCallback(controller: MediaController) {
        val ownController = MediaController(applicationContext,
                controller.sessionToken)
        val callback = MediaCB(Utils.getPackageAndSessionName(controller))
        ownController.registerCallback(callback)
        controllers[Utils.getPackageAndSessionName(controller)] = ownController
        callbacks[Utils.getPackageAndSessionName(controller)] = callback
        Log.d(TAG, "Registered new controller callback " + Utils.getPackageAndSessionName(controller))
        val mediaMetadata = controller.metadata
        if (mediaMetadata != null) {
            callback.setInitialCachedMetadata(Utils.getAudioMetadataFromMediaMetadata(mediaMetadata))
        }
    }

    companion object {
        const val START_ACTION = "START_ACTION"
        const val PLAY_ACTION = "PLAY_ACTION"
        const val TAG = "MediaCallbackService"
        private const val ONGOING_NOTIFICATION_ID = 21434
        const val CHANNEL_ID = "MediaCallbackServiceChannel"
    }
}