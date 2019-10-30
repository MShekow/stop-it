package com.example.audiobookmark;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

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

            PlaybackSupportState supportState = PlaybackBookmarkSupport.isPlaybackSupportedForPackage(currentBookmark.playerPackage);
            boolean disablePlayButton = supportState == PlaybackSupportState.UNKNOWN || supportState == PlaybackSupportState.UNSUPPORTED;
            if (disablePlayButton) {
                Drawable disabledArrow = context.getResources().getDrawable(R.drawable.ic_play_arrow_gray_24dp, null);
                holder.playButtonView.setImageDrawable(disabledArrow);
                holder.playButtonView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {}
                });
            } else {
                Drawable enableArrow = context.getResources().getDrawable(R.drawable.ic_play_arrow_black_24dp, null);
                holder.playButtonView.setImageDrawable(enableArrow);

                holder.playButtonView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(context, MediaCallbackService.class)
                                .setAction(MediaCallbackService.PLAY_ACTION)
                                .putExtra("bookmark", currentBookmark);
                        context.startService(intent);
                    }
                });
            }

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