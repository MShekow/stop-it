package com.example.audiobookmark;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "audio_bookmarks")
public class Bookmark {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public long createdAt;
    public String artist;
    public String album;
    public String track;
    public int timestampSeconds;
    public String playerPackage;
    public String metadata;
}
