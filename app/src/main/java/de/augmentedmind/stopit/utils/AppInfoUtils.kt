package de.augmentedmind.stopit.utils

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import de.augmentedmind.stopit.R
import java.util.*
import kotlin.math.roundToInt

object AppInfoUtils {
    private val UNKNOWN_KEY = "UNKNOWN"
    private val appIconCache: MutableMap<String, Bitmap> = HashMap()
    private val appNameCache: MutableMap<String, String> = HashMap()

    /**
     *
     */
    fun retrieveAppInfo(packageName: String, packageManager: PackageManager, resources: Resources): AppInfo {
        if (!appIconCache.containsKey(packageName)) {
            val info: ApplicationInfo
            try {
                info = packageManager.getApplicationInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                val unknownAppString = resources.getString(R.string.unknown_app_name)
                return AppInfo(false, getUnknownAppBitmap(resources), unknownAppString, packageName)
            }
            val appName = info.loadLabel(packageManager).toString()
            val appIcon = info.loadIcon(packageManager)
            val scaledAppBitmap = convertDrawable(appIcon, resources.getDimension(R.dimen.app_icon_width))
            appIconCache[packageName] = scaledAppBitmap
            appNameCache[packageName] = appName
        }

        return AppInfo(true, appIconCache[packageName]!!, appNameCache[packageName]!!, packageName)
    }

    /**
     * Converts a [Drawable] to a optionally scaled [Bitmap].
     *
     * @param drawable The [Drawable] to convert to a Bitmap.
     * @param scaledDims indicates the maximum dimensions (width/height) the image should be scaled
     * to in dp. 0F means not to scale it
     * @return An optionally scaled Bitmap - if the [drawable] is smaller than [scaledDims], it will
     * not be up-scaled
     */
    private fun convertDrawable(drawable: Drawable, scaledDims: Float = 0F): Bitmap {
        val bitmap: Bitmap
        if (drawable is BitmapDrawable) {
            bitmap = drawable.bitmap
        } else {
            bitmap = Bitmap.createBitmap(drawable.intrinsicWidth,
                    drawable.intrinsicHeight,
                    Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
        if (scaledDims == 0F) {
            return bitmap
        }
        return if (bitmap.height > scaledDims || bitmap.width > scaledDims) {
            // Which needs to be scaled to fit.
            val height = bitmap.height
            val width = bitmap.width
            val scaleHeight: Int
            val scaleWidth: Int

            // Calculate the new size based on which dimension is larger.
            if (height > width) {
                scaleHeight = scaledDims.roundToInt()
                scaleWidth = (width * scaledDims / height).toInt()
            } else {
                scaleWidth = scaledDims.roundToInt()
                scaleHeight = (height * scaledDims / width).toInt()
            }
            Bitmap.createScaledBitmap(bitmap, scaleWidth, scaleHeight, false)
        } else {
            bitmap
        }
    }

    private fun getUnknownAppBitmap(resources: Resources): Bitmap {
        if (!appIconCache.containsKey(UNKNOWN_KEY)) {
            val drawable = resources.getDrawable(R.drawable.unknown_app_icon, null)
            appIconCache[UNKNOWN_KEY] = convertDrawable(drawable, resources.getDimension(R.dimen.app_icon_width))
        }
        return appIconCache[UNKNOWN_KEY]!!
    }
}