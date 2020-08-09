package de.augmentedmind.stopit.service

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import de.augmentedmind.stopit.db.Bookmark
import de.augmentedmind.stopit.utils.BookmarkPlaybackSupport
import de.augmentedmind.stopit.utils.PlaybackSupportState

class PlaybackStateChangeProcessor(val onBookmarkDetected: (Bookmark) -> (Unit), val applicationContext: Context) {
    // These values will be overwritten from the SharedPreferences, and will be kept up to date
    // by the SharedPreferences listener
    var thresholdMaxMs = 2000
    var thresholdMinMs = 500
    var thresholdNotificationMs = 2000
    private var seekModeActive = false
    private var seekModeStartTimestampMs: Long = 0
    private var seekedBookmark: Bookmark? = null
    private var lastPauseTimestampMs: Long = 0
    private var lastMetaData: AudioMetadata? = null

    fun processStateChange(newState: Int, timestampSeconds: Int, controller: MediaController?,
                           cachedMetadata: AudioMetadata?, cachedMediaId: String?) {
        val metaData: AudioMetadata?
        val mediaId: String?
        val mediaMetadata = controller!!.metadata
        if (mediaMetadata == null) {
            Log.e(TAG, "processStateChange(): Unable to get metadata (null was returned), " +
                    "using cached one instead")
            metaData = cachedMetadata
            mediaId = cachedMediaId
        } else {
            metaData = Utils.getAudioMetadataFromMediaMetadata(mediaMetadata)
            mediaId = mediaMetadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        }
        if (isSeekModeActive()) {
            if (newState == PlaybackState.STATE_PLAYING && controller.packageName == seekedBookmark!!.playerPackage && metaData!!.track == seekedBookmark!!.track) {
                controller.transportControls.seekTo(1000 * seekedBookmark!!.timestampSeconds.toLong())
                Log.d(MediaCallbackService.TAG, "Seek to " + seekedBookmark!!.timestampSeconds + " completed for "
                        + seekedBookmark!!.track)
                disableSeekMode()
            } else {
                Log.d(MediaCallbackService.TAG, "In seek mode, but something was not right")
            }
        } else {
            // Normal mode, check whether we should create a new bookmark
            if (newState == PlaybackState.STATE_PAUSED) {
                lastPauseTimestampMs = SystemClock.elapsedRealtime()
                lastMetaData = metaData
            } else {
                if (lastMetaData == null) {
                    Log.d(MediaCallbackService.TAG, "Warning: lastMetaData is null!")
                    return
                }
                if (metaData != lastMetaData) {
                    Log.d(MediaCallbackService.TAG, "Current: " + metaData.toString())
                    Log.d(MediaCallbackService.TAG, "Old: " + lastMetaData.toString())
                    return
                }
                Log.d(MediaCallbackService.TAG, "Play = True. Last TS: $lastPauseTimestampMs")
                val currentTimeMs = SystemClock.elapsedRealtime()
                val difference = currentTimeMs - lastPauseTimestampMs
                if (difference < thresholdMinMs) {
                    Log.d(MediaCallbackService.TAG, "Discarding event " + metaData?.track + " - difference is too small: " + difference)
                    return
                }
                if (hasRecentNotificationHappened(currentTimeMs, controller.packageName)) {
                    Log.d(MediaCallbackService.TAG, "Discarding event " + metaData?.track + " - there recently " +
                            "was a notification!")
                    return
                }
                if (difference < thresholdMaxMs) {
                    Log.d(MediaCallbackService.TAG, "Triggered event: " + metaData?.track + " ID: " + mediaId)
                    val bookmark = Bookmark(id = 0, createdAt = System.currentTimeMillis(), artist = metaData?.artist, album = metaData?.album, track = metaData?.track, timestampSeconds = timestampSeconds, playerPackage = controller.packageName, metadata = mediaId)
                    onBookmarkDetected(bookmark)
                } else {
                    Log.d(MediaCallbackService.TAG, "Difference too large: $difference")
                }
            }
        }
    }

    fun playBookmark(bookmark: Bookmark, controllers: MutableMap<String, MediaController>) {
        val supportState = BookmarkPlaybackSupport.isPlaybackSupportedForPackage(bookmark.playerPackage)
        if (supportState == PlaybackSupportState.SUPPORTED_BY_MEDIA_ID || supportState == PlaybackSupportState.SUPPORTED_BY_QUERY) {
            val playerControllers = controllers.filterKeys { it.startsWith(bookmark.playerPackage) }
            if (playerControllers.isEmpty()) {
                Toast.makeText(applicationContext, String.format("App %s is not started, please start it " +
                        "first", bookmark.playerPackage), Toast.LENGTH_SHORT).show()
                return
            }
            // An audio player might have several(!) controllers - simply use the first one, as we
            // wouldn't know how they are different anyway
            val controller: MediaController = playerControllers.values.first()
            val controls = controller.transportControls
            if (supportState == PlaybackSupportState.SUPPORTED_BY_MEDIA_ID) {
                val mediaId = bookmark.metadata
                if (mediaId != null) {
                    controls.playFromMediaId(mediaId, null)
                    enableSeekMode(bookmark)
                    Log.d(MediaCallbackService.TAG, "Play action executed!$mediaId")
                }
            } else {
                // PlaybackSupportState.SUPPORTED_BY_QUERY
                controls.playFromSearch(bookmark.track, null)
                enableSeekMode(bookmark)
                Log.d(MediaCallbackService.TAG, "Play action executed!" + bookmark.track)
            }
        }
    }

    private fun disableSeekMode() {
        seekModeActive = false
        seekedBookmark = null
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
                Log.d(MediaCallbackService.TAG, "Seek-mode auto-disabled due to old timestamp: $now $seekModeStartTimestampMs")
                disableSeekMode()
            }
        }
        return seekModeActive
    }

    private fun hasRecentNotificationHappened(currentTimeMs: Long, ignoredPackageName: String): Boolean {
        val timeDiffs = StopitNotificationListenerService.lastNotificationTimestampsMs
                .filterKeys { it != ignoredPackageName }
                .mapValues { currentTimeMs - it.value }
        val filtered = timeDiffs.filterValues { it < thresholdNotificationMs }
        if (filtered.count() > 0) {
            Log.d(TAG, "Recent notifications: " + filtered.toString())
        }
        return filtered.count() > 0
    }

    companion object {
        const val TAG = "PbStateChangeProcessor"
    }
}