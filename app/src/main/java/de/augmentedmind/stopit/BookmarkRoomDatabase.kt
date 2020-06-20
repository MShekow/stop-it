package de.augmentedmind.stopit

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = arrayOf(Bookmark::class), version = 1)
abstract class BookmarkRoomDatabase : RoomDatabase() {

    abstract fun dao(): BookmarkDao

    companion object {
        @Volatile
        private var INSTANCE: BookmarkRoomDatabase? = null

        fun getDatabase(context: Context): BookmarkRoomDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }

            synchronized(this) {
                val instance = Room.databaseBuilder(
                        context.applicationContext,
                        BookmarkRoomDatabase::class.java,
                        "audio_bookmarks_db"
                ).build()
                INSTANCE = instance
                return instance
            }
        }
    }
}