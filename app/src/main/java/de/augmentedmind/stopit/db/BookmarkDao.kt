package de.augmentedmind.stopit.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BookmarkDao {
    @Insert
    suspend fun insert(bookmark: Bookmark)

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM audio_bookmarks")
    suspend fun deleteAll()

    @Query("SELECT * FROM audio_bookmarks ORDER BY createdAt ASC")
    fun getAllBookmarks(): LiveData<List<Bookmark>>
}