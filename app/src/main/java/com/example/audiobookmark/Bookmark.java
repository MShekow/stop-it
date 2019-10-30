package com.example.audiobookmark;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "audio_bookmarks")
public class Bookmark implements Parcelable {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public long createdAt;
    public String artist;
    public String album;
    public String track;
    public int timestampSeconds;
    public String playerPackage;
    public String metadata;

    public Bookmark() {}

    public Bookmark(Parcel in) {
        String[] stringData = new String[5];
        in.readStringArray(stringData);
        this.artist = stringData[0];
        this.album = stringData[1];
        this.track = stringData[2];
        this.playerPackage = stringData[3];
        this.metadata = stringData[4];
        int[] intData = new int[2];
        in.readIntArray(intData);
        this.id = intData[0];
        this.timestampSeconds = intData[1];
        this.createdAt = in.readLong();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringArray(new String[]{this.artist, this.album, this.track, this.playerPackage, this.metadata});
        dest.writeIntArray(new int[] {this.id, this.timestampSeconds});
        dest.writeLong(this.createdAt);
    }

    public static final Parcelable.Creator<Bookmark> CREATOR = new Parcelable.Creator<Bookmark>() {

        @Override
        public Bookmark createFromParcel(Parcel source) {
            return new Bookmark(source);
        }

        @Override
        public Bookmark[] newArray(int size) {
            return new Bookmark[size];
        }
    };
}
