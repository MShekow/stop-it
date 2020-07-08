package de.augmentedmind.stopit.ui

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.augmentedmind.stopit.db.BookmarkRepository
import de.augmentedmind.stopit.R
import de.augmentedmind.stopit.ui.BookmarkListAdapter.BookmarkViewHolder
import de.augmentedmind.stopit.db.Bookmark
import de.augmentedmind.stopit.db.BookmarkRoomDatabase
import de.augmentedmind.stopit.service.MediaCallbackService
import de.augmentedmind.stopit.utils.BookmarkPlaybackSupport
import de.augmentedmind.stopit.utils.PlaybackSupportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BookmarkListAdapter internal constructor(private val context: Context) : RecyclerView.Adapter<BookmarkViewHolder>() {
    inner class BookmarkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val trackTextView: TextView = itemView.findViewById(R.id.trackTextView)
        val playButtonView: ImageView = itemView.findViewById(R.id.playButtonView)
        val positionTextView: TextView = itemView.findViewById(R.id.positionTextView)
        val artistTextView: TextView = itemView.findViewById(R.id.artistTextView)
        val deleteImageView: ImageView = itemView.findViewById(R.id.deleteImageView)
    }

    private val layoutInflater = LayoutInflater.from(context)
    private var bookmarks = emptyList<Bookmark>()
    private val coroScope = CoroutineScope(Dispatchers.IO)
    private val repository: BookmarkRepository
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val itemView = layoutInflater.inflate(R.layout.recyclerview_item, parent, false)
        return BookmarkViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        if (bookmarks.isNotEmpty()) {
            val currentBookmark = bookmarks[position]
            holder.trackTextView.text = currentBookmark.track
            holder.positionTextView.text = convertTimestamp(currentBookmark.timestampSeconds)
            holder.artistTextView.text = currentBookmark.artist
            val supportState = BookmarkPlaybackSupport.isPlaybackSupportedForPackage(currentBookmark.playerPackage)
            val disablePlayButton = supportState == PlaybackSupportState.UNKNOWN || supportState == PlaybackSupportState.UNSUPPORTED
            if (disablePlayButton) {
                val disabledArrow = context.resources.getDrawable(R.drawable.ic_play_arrow_gray_24dp, null)
                holder.playButtonView.setImageDrawable(disabledArrow)
                holder.playButtonView.setOnClickListener { }
            } else {
                val enableArrow = context.resources.getDrawable(R.drawable.ic_play_arrow_black_24dp, null)
                holder.playButtonView.setImageDrawable(enableArrow)
                holder.playButtonView.setOnClickListener {
                    val intent = Intent(context, MediaCallbackService::class.java)
                            .setAction(MediaCallbackService.PLAY_ACTION)
                            .putExtra("bookmark", currentBookmark)
                    context.startService(intent)
                }
            }
            holder.deleteImageView.setOnClickListener {
                coroScope.launch { repository.delete(currentBookmark) }
            }
        } else {
            // Covers the case of data not being ready yet.
            holder.trackTextView.text = context.getString(R.string.no_bookmarks_yet)
        }
    }

    fun setBookmarks(bookmarks: List<Bookmark>) {
        this.bookmarks = bookmarks
        notifyDataSetChanged()
    }

    private fun convertTimestamp(timestamp: Int): String {
        val minutes = timestamp / 60
        val seconds = timestamp - (60 * minutes)
        var secondsStr = seconds.toString()
        if (secondsStr.length == 1) {
            secondsStr = "0$secondsStr"
        }
        return "$minutes:$secondsStr"
    }

    override fun getItemCount() = bookmarks.size

    init {
        val bookmarkDao = BookmarkRoomDatabase.getDatabase(context).dao()
        repository = BookmarkRepository(bookmarkDao)
    }
}