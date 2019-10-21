package com.example.audiobookmark;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {

    private SettingsFragment settingsFragment;

    private static Map<Integer, Integer> secondSeekbarIds;

    private static String TAG = "SettingsActivity";

    static {
        secondSeekbarIds = new HashMap<>();
        secondSeekbarIds.put(R.string.key_pause_play_min_delay_millis, R.string.pause_play_min_delay_summary);
        secondSeekbarIds.put(R.string.key_pause_play_max_delay_millis, R.string.pause_play_max_delay_summary);
        secondSeekbarIds.put(R.string.key_pause_play_notification_lookback, R.string.max_notification_lookback_millis_summary);
        secondSeekbarIds.put(R.string.key_vibrate_duration, R.string.vibrate_summary);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsFragment = new SettingsFragment();
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, settingsFragment)
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private static class PrefChangeListener implements Preference.OnPreferenceChangeListener {
        private String summaryTextTemplate;

        PrefChangeListener(String summaryTextTemplate) {
            this.summaryTextTemplate = summaryTextTemplate;
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            int rawValue = (Integer) newValue;
            float seconds = rawValue / 1000f;
            String summary = String.format(summaryTextTemplate, seconds);
            preference.setSummary(summary);
            return true;
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            for (int preference_key_resource_id : secondSeekbarIds.keySet()) {
                SeekBarPreference pref = findPreference(getString(preference_key_resource_id));
                int summary_text_resource_id = secondSeekbarIds.get(preference_key_resource_id);
                String summaryTextTemplate = getString(summary_text_resource_id);
                pref.setOnPreferenceChangeListener(new PrefChangeListener(summaryTextTemplate));
            }
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        for (int preference_key_resource_id : secondSeekbarIds.keySet()) {
            SeekBarPreference pref = settingsFragment.findPreference(getString(preference_key_resource_id));
            if (pref != null) {
                float seconds = pref.getValue() / 1000f;
                int summary_text_resource_id = secondSeekbarIds.get(preference_key_resource_id);
                String summary = getString(summary_text_resource_id, seconds);
                pref.setSummary(summary);
            }
        }

        SwitchPreferenceCompat permissionPreference = settingsFragment.findPreference(getString(R.string.key_service_state));
        permissionPreference.setChecked(StopitNotificationListenerService.isEnabled(getApplicationContext()));
        permissionPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                SwitchPreferenceCompat pref = (SwitchPreferenceCompat) preference;
                Log.d(TAG, "Checked: " + pref.isChecked());
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                return true;
            }
        });
    }
}