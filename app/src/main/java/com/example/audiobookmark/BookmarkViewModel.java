package com.example.audiobookmark;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

public class BookmarkViewModel extends AndroidViewModel {
    private BookmarkRepository repository;
    private LiveData<List<Bookmark>> allBookmarks;

    public BookmarkViewModel(Application application) {
        super(application);
        repository = new BookmarkRepository(application);
        allBookmarks = repository.getAllBookmarks();
    }

    LiveData<List<Bookmark>> getAllBookmarks() { return allBookmarks; }

    public void insert(Bookmark bookmark) { repository.insert(bookmark); }
}
