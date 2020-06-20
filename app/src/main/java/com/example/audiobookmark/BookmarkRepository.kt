package com.example.audiobookmark

import androidx.lifecycle.LiveData

class BookmarkRepository(private val dao: BookmarkDao) {
    val allBookmarks: LiveData<List<Bookmark>> = dao.getAllBookmarks()

    suspend fun insert(bookmark: Bookmark) {
        dao.insert(bookmark)
    }

    suspend fun delete(bookmark: Bookmark) {
        dao.delete(bookmark)
    }

}