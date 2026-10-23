package uk.co.tdsstudios.noterly.wear

import android.app.Application
import uk.co.tdsstudios.noterly.wear.data.WearRepository
import uk.co.tdsstudios.noterly.wear.notify.WatchNotifications

class NoterlyWearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WatchNotifications.createChannel(this)
        repository.disableBridging()
    }
}

/** The repository is one per process, shared by the activity, services and receivers. */
val android.content.Context.repository: WearRepository
    get() = WearRepository.get(applicationContext)
