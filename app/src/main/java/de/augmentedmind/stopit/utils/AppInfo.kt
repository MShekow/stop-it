package de.augmentedmind.stopit.utils

import android.graphics.Bitmap
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class AppInfo(val isInstalled: Boolean, val bitmap: Bitmap, val name: String,
                   val packageName: String) : Parcelable