package uk.co.tdsstudios.noterly.wear.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.co.tdsstudios.noterly.wear.repository

/** Alarms do not survive a reboot or an app update, so set them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                context.repository.refreshOutputs()
            } finally {
                result.finish()
            }
        }
    }
}
