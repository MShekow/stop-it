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
        return artist.equals(that.artist) &&
                album.equals(that.album) &&
                track.equals(that.track);
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
