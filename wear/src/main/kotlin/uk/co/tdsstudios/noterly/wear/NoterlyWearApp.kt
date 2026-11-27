package uk.co.tdsstudios.noterly.wear

import android.app.Application
import android.content.res.Configuration
import android.os.LocaleList
import uk.co.tdsstudios.noterly.wear.complication.NextReminderComplicationService
import uk.co.tdsstudios.noterly.wear.data.WearRepository
import uk.co.tdsstudios.noterly.wear.notify.WatchNotifications
import uk.co.tdsstudios.noterly.wear.tile.NoterlyTileService

class NoterlyWearApp : Application() {
    private var locales: LocaleList? = null

    override fun onCreate() {
        super.onCreate()
        locales = resources.configuration.locales
        WatchNotifications.createChannel(this)
        repository.disableBridging()
    }

    /**
     * A new language (the phone app's, or the watch's own) applies here, after the screens are recreated in it: name the
     * notification channel and redraw the tile and complication in it too.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.locales == locales) return
        locales = newConfig.locales
        WatchNotifications.createChannel(this)
        NoterlyTileService.requestUpdate(this)
        NextReminderComplicationService.requestUpdate(this)
    }
}

/** The repository is one per process, shared by the activity, services and receivers. */
val android.content.Context.repository: WearRepository
    get() = WearRepository.get(applicationContext)
