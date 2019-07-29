package com.example.audiobookmark;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BookmarkListAdapter extends RecyclerView.Adapter<BookmarkListAdapter.BookmarkViewHolder> {

    class BookmarkViewHolder extends RecyclerView.ViewHolder {
        private final TextView bookmarkItemView;

        private BookmarkViewHolder(View itemView) {
            super(itemView);
            bookmarkItemView = itemView.findViewById(R.id.textView);
        }
    }

    private final LayoutInflater mInflater;
    private List<Bookmark> mBookmarks; // Cached copy of words

    BookmarkListAdapter(Context context) { mInflater = LayoutInflater.from(context); }

    @Override
    @NonNull
    public BookmarkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = mInflater.inflate(R.layout.recyclerview_item, parent, false);
        return new BookmarkViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull BookmarkViewHolder holder, int position) {
        if (mBookmarks != null) {
            Bookmark current = mBookmarks.get(position);
            String bookmarkText = current.track + "\n" + current.artist;
            holder.bookmarkItemView.setText(bookmarkText);
        } else {
            // Covers the case of data not being ready yet.
            holder.bookmarkItemView.setText("No bookmarks stored yet!");
        }
    }

    void setWords(List<Bookmark> bookmarks){
        mBookmarks = bookmarks;
        notifyDataSetChanged();
    }

    // getItemCount() is called many times, and when it is first called,
    // mBookmarks has not been updated (means initially, it's null, and we can't return null).
    @Override
    public int getItemCount() {
        if (mBookmarks != null)
            return mBookmarks.size();
        else return 0;
    }
}