package de.augmentedmind.stopit.db

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.android.parcel.Parcelize

@Entity(tableName = "audio_bookmarks")
@Parcelize
class Bookmark(
        @PrimaryKey(autoGenerate = true) val id: Int,
        val createdAt: Long,
        val artist: String?,
        val album: String?,
        val track: String?,
        val timestampSeconds: Int,
        val playerPackage: String,
        val metadata: String?) : Parcelable