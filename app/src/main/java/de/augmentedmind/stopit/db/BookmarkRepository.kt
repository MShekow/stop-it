package de.augmentedmind.stopit.db

import androidx.lifecycle.LiveData
import de.augmentedmind.stopit.db.Bookmark
import de.augmentedmind.stopit.db.BookmarkDao

class BookmarkRepository(private val dao: BookmarkDao) {
    val allBookmarks: LiveData<List<Bookmark>> = dao.getAllBookmarks()

    suspend fun insert(bookmark: Bookmark) {
        dao.insert(bookmark)
    }

    suspend fun delete(bookmark: Bookmark) {
        dao.delete(bookmark)
    }

}