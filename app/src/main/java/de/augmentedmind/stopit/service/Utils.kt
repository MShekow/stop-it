package de.augmentedmind.stopit.service

import android.media.MediaMetadata
import android.media.session.MediaController

class Utils {
    companion object {
        fun getPackageAndSessionName(controller: MediaController) = (controller.packageName + "-"
                + Integer.toHexString(controller.sessionToken.hashCode()))

        fun getAudioMetadataFromMediaMetadata(mediaMetadata: MediaMetadata): AudioMetadata {
            var artist = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            val album = mediaMetadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
            val track = mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            if (artist == null && album != null) {
                artist = album
            } else if (artist == null && album == null) {
                val description = mediaMetadata.description
                if (description.subtitle != null) artist = description.subtitle.toString()
            }
            val mediaId = mediaMetadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            return AudioMetadata(artist, album, track, mediaId)
        }
    }
}