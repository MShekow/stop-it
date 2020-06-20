package de.augmentedmind.stopit

import java.util.*

object PlaybackBookmarkSupport {
    /**
     * Maps from app package name to true (search by media ID) or false (search by query)
     */
    private val supportedPackages: MutableMap<String?, PlaybackSupportState> = HashMap()
    fun isPlaybackSupportedForPackage(packageName: String?): PlaybackSupportState? {
        return if (supportedPackages.containsKey(packageName)) {
            supportedPackages[packageName]
        } else PlaybackSupportState.UNKNOWN
    }

    init {
        supportedPackages["au.com.shiftyjelly.pocketcasts"] = PlaybackSupportState.SUPPORTED_BY_MEDIA_ID
        supportedPackages["fm.castbox.audiobook.radio.podcast"] = PlaybackSupportState.SUPPORTED_BY_QUERY

        // Now the unsupported ones
        supportedPackages["com.snoggdoggler.android.applications.doggcatcher.v1_0"] = PlaybackSupportState.UNSUPPORTED

        // TODO: we could add support by using the Spotify SDK, though
        supportedPackages["com.spotify.music"] = PlaybackSupportState.UNSUPPORTED

        // For the time being, as the YouTube app does not seem to provide either a media ID
        // nor a URI, I contacted support, let's see what happens (probably nothing)
        supportedPackages["com.google.android.youtube"] = PlaybackSupportState.UNSUPPORTED
    }
}