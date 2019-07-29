package com.example.audiobookmark;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface BookmarkDao {

    @Insert
    void insert(Bookmark bookmark);

    @Query("DELETE FROM audio_bookmarks")
    void deleteAll();

    @Query("SELECT * FROM audio_bookmarks ORDER BY createdAt ASC")
    LiveData<List<Bookmark>> getAllBookmarks();
}
