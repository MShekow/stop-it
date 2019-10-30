package com.example.audiobookmark;

import java.util.HashMap;
import java.util.Map;


public class PlaybackBookmarkSupport {

    /**
     * Maps from app package name to true (search by media ID) or false (search by query)
     */
    private static final Map<String, PlaybackSupportState> supportedPackages = new HashMap<>();

    static {
        supportedPackages.put("au.com.shiftyjelly.pocketcasts",
                PlaybackSupportState.SUPPORTED_BY_MEDIA_ID);
        supportedPackages.put("fm.castbox.audiobook.radio.podcast",
                PlaybackSupportState.SUPPORTED_BY_QUERY);

        // Now the unsupported ones
        supportedPackages.put("com.snoggdoggler.android.applications.doggcatcher.v1_0",
                PlaybackSupportState.UNSUPPORTED);

        // TODO: we could add support by using the Spotify SDK, though
        supportedPackages.put("com.spotify.music", PlaybackSupportState.UNSUPPORTED);

        // For the time being, as the YouTube app does not seem to provide either a media ID
        // nor a URI, I contacted support, let's see what happens (probably nothing)
        supportedPackages.put("com.google.android.youtube", PlaybackSupportState.UNSUPPORTED);

        // TODO add other players
    }

    public static PlaybackSupportState isPlaybackSupportedForPackage(String packageName) {
        if (supportedPackages.containsKey(packageName)) {
            return supportedPackages.get(packageName);
        }
        return PlaybackSupportState.UNKNOWN;
    }
}
