package de.augmentedmind.stopit.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import de.augmentedmind.stopit.db.Bookmark
import de.augmentedmind.stopit.db.BookmarkRepository
import de.augmentedmind.stopit.db.BookmarkRoomDatabase

class BookmarkViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: BookmarkRepository
    val allBookmarks: LiveData<List<Bookmark>>

    init {
        val bookmarkDao = BookmarkRoomDatabase.getDatabase(application).dao()
        repository = BookmarkRepository(bookmarkDao)
        allBookmarks = repository.allBookmarks
    }
}