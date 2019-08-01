package com.example.audiobookmark;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BookmarkListAdapter extends RecyclerView.Adapter<BookmarkListAdapter.BookmarkViewHolder> {

    class BookmarkViewHolder extends RecyclerView.ViewHolder {
        private final TextView trackTextView;
        private final ImageView playButtonView;
        private final TextView positionTextView;
        private final TextView artistTextView;
        private final ImageView deleteImageView;

        private BookmarkViewHolder(View itemView) {
            super(itemView);
            trackTextView = itemView.findViewById(R.id.trackTextView);
            playButtonView = itemView.findViewById(R.id.playButtonView);
            positionTextView = itemView.findViewById(R.id.positionTextView);
            artistTextView = itemView.findViewById(R.id.artistTextView);
            deleteImageView = itemView.findViewById(R.id.deleteImageView);
        }
    }

    private final LayoutInflater layoutInflater;
    private List<Bookmark> mBookmarks; // Cached copy of words
    private Context context;
    private BookmarkRepository repository;

    BookmarkListAdapter(Context context) {
        this.layoutInflater = LayoutInflater.from(context);
        this.context = context;
        this.repository = new BookmarkRepository(context);
    }

    @Override
    @NonNull
    public BookmarkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = layoutInflater.inflate(R.layout.recyclerview_item, parent, false);
        return new BookmarkViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull BookmarkViewHolder holder, int position) {
        if (mBookmarks != null) {
            final Bookmark currentBookmark = mBookmarks.get(position);
            holder.trackTextView.setText(currentBookmark.track);
            holder.positionTextView.setText(convertTimestamp(currentBookmark.timestampSeconds));
            holder.artistTextView.setText(currentBookmark.artist);

            holder.playButtonView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (currentBookmark.playerPackage.contains("spotify")) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(currentBookmark.metadata));
                        intent.putExtra(Intent.EXTRA_REFERRER,
                                Uri.parse("android-app://" + context.getPackageName()));
                        context.startActivity(intent);
                    }
                    Toast.makeText(BookmarkListAdapter.this.context, currentBookmark.track, Toast.LENGTH_SHORT).show();
                }
            });

            holder.deleteImageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    repository.delete(currentBookmark);
                }
            });
        } else {
            // Covers the case of data not being ready yet.
            holder.trackTextView.setText("No bookmarks stored yet!");
        }
    }

    void setWords(List<Bookmark> bookmarks) {
        mBookmarks = bookmarks;
        notifyDataSetChanged();
    }

    private String convertTimestamp(int timestamp) {
        int minutes = timestamp / 60;
        int seconds = timestamp - (60 * minutes);
        String secondsStr = String.valueOf(seconds);
        if (secondsStr.length() == 1) {
            secondsStr = "0" + secondsStr;
        }
        return minutes + ":" + secondsStr;
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