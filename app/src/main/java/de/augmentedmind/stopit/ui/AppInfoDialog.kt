package de.augmentedmind.stopit.ui

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import de.augmentedmind.stopit.R
import de.augmentedmind.stopit.utils.AppInfo


class AppInfoDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(it)
            val appInfo = arguments?.getParcelable<AppInfo>(DATA_KEY) ?: return builder.create()

            builder.setTitle(R.string.app_info_title)
                    .setPositiveButton(R.string.app_info_close_button_text, null)
            if (appInfo.isInstalled) {
                builder.setNeutralButton(R.string.open_app) { dialog, id ->
                    val launchIntent: Intent? = requireActivity()
                            .packageManager.getLaunchIntentForPackage(appInfo.packageName)
                    startActivity(launchIntent)
                }
            }
            val inflater = requireActivity().layoutInflater
            val dialogView = inflater.inflate(R.layout.app_info_dialog, null)
            val appNameTextView: TextView = dialogView.findViewById(R.id.appNameTextView)
            appNameTextView.text = appInfo.name
            val appIconImageView: ImageView = dialogView.findViewById(R.id.appIconImageView)
            appIconImageView.setImageBitmap(appInfo.bitmap)
            val packageTextView: TextView = dialogView.findViewById(R.id.packageTextView)
            packageTextView.text = appInfo.packageName
            builder.setView(dialogView)
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    companion object {
        const val DATA_KEY = "data"

        fun newInstance(appInfo: AppInfo): AppInfoDialogFragment {
            val f = AppInfoDialogFragment()
            val args = Bundle()
            args.putParcelable(DATA_KEY, appInfo)
            f.arguments = args
            return f
        }
    }
}
