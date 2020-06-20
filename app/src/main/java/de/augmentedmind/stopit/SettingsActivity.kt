package de.augmentedmind.stopit

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import de.augmentedmind.stopit.StopitNotificationListenerService.Companion.isEnabled
import java.util.*

class SettingsActivity : AppCompatActivity() {
    private lateinit var settingsFragment: SettingsFragment

    companion object {
        private var secondSeekbarIds: MutableMap<Int, Int> = HashMap()
        private const val TAG = "SettingsActivity"

        init {
            secondSeekbarIds[R.string.key_pause_play_min_delay_millis] = R.string.pause_play_min_delay_summary
            secondSeekbarIds[R.string.key_pause_play_max_delay_millis] = R.string.pause_play_max_delay_summary
            secondSeekbarIds[R.string.key_pause_play_notification_lookback] = R.string.max_notification_lookback_millis_summary
            secondSeekbarIds[R.string.key_vibrate_duration] = R.string.vibrate_summary
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsFragment = SettingsFragment()
        setContentView(R.layout.settings_activity)
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, settingsFragment)
                .commit()
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private class PrefChangeListener internal constructor(private val summaryTextTemplate: String) : Preference.OnPreferenceChangeListener {
        override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
            val rawValue = newValue as Int
            val seconds = rawValue / 1000f
            val summary = String.format(summaryTextTemplate, seconds)
            preference.summary = summary
            return true
        }

    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            // Make sure that the summary texts of seekbars that show sections (and their
            // fractions) are updated whenever the user moves the seekbar's slider
            for (preference_key_resource_id in secondSeekbarIds.keys) {
                val pref = findPreference<SeekBarPreference>(getString(preference_key_resource_id))
                val summary_text_resource_id = secondSeekbarIds[preference_key_resource_id]!!
                val summaryTextTemplate = getString(summary_text_resource_id)
                pref!!.onPreferenceChangeListener = PrefChangeListener(summaryTextTemplate)
            }
        }
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        // Update the summary of the second-showing seekbars, as the setOnPreferenceChangeListener()
        // call above only does so on a value CHANGE
        for (preference_key_resource_id in secondSeekbarIds.keys) {
            val pref = settingsFragment.findPreference<SeekBarPreference>(getString(preference_key_resource_id))
            if (pref != null) {
                val seconds = pref.value / 1000f
                val summary_text_resource_id = secondSeekbarIds[preference_key_resource_id]!!
                val summary = getString(summary_text_resource_id, seconds)
                pref.summary = summary
            }
        }
        val permissionPreference = settingsFragment.findPreference<SwitchPreferenceCompat>(getString(R.string.key_service_state))
        permissionPreference!!.isChecked = isEnabled(applicationContext)
        permissionPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference ->
            val pref = preference as SwitchPreferenceCompat
            Log.d(TAG, "Checked: " + pref.isChecked)
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            true
        }
    }
}