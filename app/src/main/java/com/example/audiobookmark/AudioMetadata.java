package com.example.audiobookmark;

import java.util.Objects;

public class AudioMetadata {
    public String artist;
    public String album;
    public String track;

    public AudioMetadata(String artist, String album, String track) {
        this.artist = artist;
        this.album = album;
        this.track = track;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioMetadata that = (AudioMetadata) o;

        if (artist == null) {
            if (that.artist != null) return false;
        } else {
            if (!artist.equals(that.artist)) return false;
        }

        if (album == null) {
            if (that.album != null) return false;
        } else {
            if (!album.equals(that.album)) return false;
        }

        if (track == null) {
            if (that.track != null) return false;
        } else {
            if (!track.equals(that.track)) return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(artist, album, track);
    }

    @Override
    public String toString() {
        return this.artist + " - " + this.album + " - " + this.track;
    }
}
