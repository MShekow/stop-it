package com.example.audiobookmark

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BookmarkViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: BookmarkRepository
    val allBookmarks: LiveData<List<Bookmark>>

    fun insert(bookmark: Bookmark) = viewModelScope.launch(Dispatchers.IO) {
        repository.insert(bookmark)
    }

    init {
        val bookmarkDao = BookmarkRoomDatabase.getDatabase(application).dao()
        repository = BookmarkRepository(bookmarkDao)
        allBookmarks = repository.allBookmarks
    }
}