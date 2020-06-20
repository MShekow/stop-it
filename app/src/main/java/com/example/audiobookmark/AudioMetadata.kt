package com.example.audiobookmark

import java.util.*

class AudioMetadata(var artist: String?, var album: String?, var track: String?) {
    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as AudioMetadata
        if (artist == null) {
            if (that.artist != null) return false
        } else {
            if (artist != that.artist) return false
        }
        if (album == null) {
            if (that.album != null) return false
        } else {
            if (album != that.album) return false
        }
        if (track == null) {
            if (that.track != null) return false
        } else {
            if (track != that.track) return false
        }
        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(artist, album, track)
    }

    override fun toString(): String {
        return "$artist - $album - $track"
    }

}