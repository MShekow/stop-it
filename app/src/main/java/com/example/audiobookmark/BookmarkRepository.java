package com.example.audiobookmark;

import android.content.Context;
import android.os.AsyncTask;

import androidx.lifecycle.LiveData;

import java.util.List;

public class BookmarkRepository {
    private BookmarkDao dao;
    private LiveData<List<Bookmark>> allBookmarks;

    public BookmarkRepository(Context context) {
        BookmarkRoomDatabase db = BookmarkRoomDatabase.getDatabase(context);
        dao = db.dao();
        allBookmarks = dao.getAllBookmarks();
    }

    public LiveData<List<Bookmark>> getAllBookmarks() {
        return allBookmarks;
    }

    public void insert(Bookmark bookmark) {
        new InsertTask(dao).execute(bookmark);
    }

    public void delete(Bookmark bookmark) {
        new DeleteTask(dao).execute(bookmark);
    }

    private static class InsertTask extends AsyncTask<Bookmark, Void, Void> {

        private BookmarkDao dao;

        InsertTask(BookmarkDao dao) {
            this.dao = dao;
        }

        @Override
        protected Void doInBackground(final Bookmark... params) {
            dao.insert(params[0]);
            return null;
        }
    }

    private static class DeleteTask extends AsyncTask<Bookmark, Void, Void> {

        private BookmarkDao dao;

        DeleteTask(BookmarkDao dao) {
            this.dao = dao;
        }

        @Override
        protected Void doInBackground(final Bookmark... params) {
            dao.delete(params[0]);
            return null;
        }
    }
}
