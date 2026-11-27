package uk.co.tdsstudios.noterly.wear.data

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import android.util.Log

/**
 * Shows the watch app in the phone app's language. A language picked for the phone app in Android's settings becomes
 * the watch app's own language (Android's per-app language, which survives restarts); without one the watch app
 * follows the watch's language, which a Galaxy Watch takes from the phone anyway.
 */
object AppLanguage {
    private const val TAG = "AppLanguage"

    /** Applies [language], a BCP 47 tag, or null for the watch's language. */
    fun follow(context: Context, language: String?) {
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        val wanted = if (language.isNullOrBlank()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language)
        if (manager.applicationLocales == wanted) return

        Log.i(TAG, "Language ${manager.applicationLocales.toLanguageTags().ifEmpty { "(watch)" }} -> ${wanted.toLanguageTags().ifEmpty { "(watch)" }}")
        // Android recreates the open screens in the new language
        manager.applicationLocales = wanted
    }
}
