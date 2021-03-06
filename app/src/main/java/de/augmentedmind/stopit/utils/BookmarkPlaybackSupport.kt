package de.augmentedmind.stopit.utils

import java.util.*

object BookmarkPlaybackSupport {
    /**
     * Helper class that indicates for which players our app can actually start playing back a
     * previously saved bookmark (and seek to the captured position).
     */

    /**
     * Maps from app package name to the supported PlaybackSupportState.
     */
    private val supportedPackages: MutableMap<String, PlaybackSupportState> = HashMap()
    fun isPlaybackSupportedForPackage(packageName: String): PlaybackSupportState {
        return supportedPackages[packageName] ?: PlaybackSupportState.UNKNOWN
    }

    init {
        supportedPackages["au.com.shiftyjelly.pocketcasts"] = PlaybackSupportState.SUPPORTED_BY_MEDIA_ID
        supportedPackages["fm.castbox.audiobook.radio.podcast"] = PlaybackSupportState.SUPPORTED_BY_QUERY

        // Now the unsupported ones
        supportedPackages["com.snoggdoggler.android.applications.doggcatcher.v1_0"] = PlaybackSupportState.UNSUPPORTED

        // TODO: see #4
        supportedPackages["com.spotify.music"] = PlaybackSupportState.UNSUPPORTED

        // For the time being, as the YouTube app does not seem to provide either a media ID
        // nor a URI, I contacted support, let's see what happens (probably nothing)
        supportedPackages["com.google.android.youtube"] = PlaybackSupportState.UNSUPPORTED
    }
}