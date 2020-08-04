package de.augmentedmind.stopit.service

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat


class StopitNotificationListenerService : NotificationListenerService() {

    companion object {
        var lastNotificationTimestampsMs = mutableMapOf<String, Long>()
        const val TAG = "StopitNotifListenerServ"
        fun isEnabled(context: Context): Boolean {
            return NotificationManagerCompat
                    .getEnabledListenerPackages(context)
                    .contains(context.packageName)
        }

        private fun cleanOutdatedNotificationTimestamps() {
            // Makes sure that lastNotificationTimestampsMs doesn't grow indefinitely - keeping just
            // the few latest notification timestamps from a few apps is enough!
            while (lastNotificationTimestampsMs.size > 5) {
                lastNotificationTimestampsMs.remove(lastNotificationTimestampsMs.minBy { it.value }!!.key)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == "android") {
            return
        }

        lastNotificationTimestampsMs[sbn.packageName] = SystemClock.elapsedRealtime()
        cleanOutdatedNotificationTimestamps()
        Log.d(TAG, "onNotificationPosted(): updated timestamp! " + sbn.toString())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // This method is called when the user enables the Notification access in the settings!
        // But it is also called in other occasions, and also if it was already enabled
        // Thus we have to consider it with care!
        Log.d(TAG, "onListenerConnected()")
        val intent = Intent(this, MediaCallbackService::class.java)
                .setAction(MediaCallbackService.START_ACTION)
        startService(intent)
    }
}