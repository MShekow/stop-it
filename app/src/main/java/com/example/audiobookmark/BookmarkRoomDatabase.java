package com.example.audiobookmark;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;


@Database(entities = {Bookmark.class}, version = 1)
public abstract class BookmarkRoomDatabase extends RoomDatabase {
    private static volatile BookmarkRoomDatabase INSTANCE;

    public abstract BookmarkDao dao();

    static BookmarkRoomDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (BookmarkRoomDatabase.class) {
                if (INSTANCE == null) {
                    // Create database here
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            BookmarkRoomDatabase.class, "audio_bookmarks_db")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
