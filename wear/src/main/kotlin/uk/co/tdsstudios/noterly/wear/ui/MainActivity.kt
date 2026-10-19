package uk.co.tdsstudios.noterly.wear.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val viewModel: WatchViewModel by viewModels()

    /** Item to open, from a notification or the tile. Consumed by the navigation. */
    private val openItem = MutableStateFlow<String?>(null)
    private val openNew = MutableStateFlow(false)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            NoterlyWearUi(viewModel = viewModel, openItem = openItem, openNew = openNew)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Ask the phone for the latest list every time the app comes up
        viewModel.refresh()
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_ITEM_ID)?.let { openItem.value = it }
        if (intent?.getBooleanExtra(EXTRA_NEW, false) == true) openNew.value = true
    }

    companion object {
        private const val EXTRA_ITEM_ID = "itemId"
        private const val EXTRA_NEW = "new"

        fun detailIntent(context: Context, itemId: String) =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_ITEM_ID, itemId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun newNoteIntent(context: Context) =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_NEW, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
