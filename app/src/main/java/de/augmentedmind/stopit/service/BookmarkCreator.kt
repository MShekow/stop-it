package de.augmentedmind.stopit.service

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.preference.PreferenceManager
import de.augmentedmind.stopit.R
import de.augmentedmind.stopit.db.Bookmark
import de.augmentedmind.stopit.db.BookmarkRepository
import de.augmentedmind.stopit.db.BookmarkRoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BookmarkCreator(val applicationContext: Context) : SharedPreferences.OnSharedPreferenceChangeListener {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var repository: BookmarkRepository
    private var shortPlayer: SoundPool
    private var beepSoundId = 0

    // These values will be overwritten in onCreate() by the values from the SharedPreferences, and
    // will be kept up to date by the SharedPreferences listener
    private var shouldPlaySound = true
    private var vibrationDurationMs = 500
    private var shouldVibrate = true

    init {
        val bookmarkDao = BookmarkRoomDatabase.getDatabase(applicationContext).dao()
        repository = BookmarkRepository(bookmarkDao)
        shortPlayer = SoundPool.Builder().setMaxStreams(10).build()
        beepSoundId = shortPlayer.load(applicationContext, R.raw.beepogg, 1)
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        sharedPrefs.registerOnSharedPreferenceChangeListener(this)
        sharedPrefs.all.forEach { updateValueFromPreference(it.key, it.value!!) }
    }

    fun storeAndAnnounceBookmark(bookmark: Bookmark) {
        serviceScope.launch { repository.insert(bookmark) }
        if (shouldPlaySound) {
            playBeepSound()
        }
        if (shouldVibrate) {
            vibrate()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        updateValueFromPreference(key, sharedPreferences.all[key]!!)
    }

    private fun updateValueFromPreference(key: String, value: Any) {
        if (key == applicationContext.getString(R.string.key_vibrate)) {
            shouldVibrate = value as Boolean
        } else if (key == applicationContext.getString(R.string.key_vibrate_duration)) {
            vibrationDurationMs = value as Int
        } else if (key == applicationContext.getString(R.string.key_play_sound)) {
            shouldPlaySound = value as Boolean
        }
    }

    private fun playBeepSound() {
        val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val actualVolume = audioManager
                .getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        val maxVolume = audioManager
                .getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        val volume = actualVolume / maxVolume
        shortPlayer.play(beepSoundId, volume, volume, 1, 0, 1f)
    }

    private fun vibrate() {
        val vibrator = applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(vibrationDurationMs.toLong(),
                        VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(vibrationDurationMs.toLong())
            }
        }
    }

    fun releaseResources() {
        shortPlayer.release()
    }
}