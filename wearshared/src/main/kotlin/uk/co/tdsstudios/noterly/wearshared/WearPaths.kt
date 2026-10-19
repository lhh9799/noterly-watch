package uk.co.tdsstudios.noterly.wearshared

/** Data Layer paths and keys shared by the phone and the watch. See docs/ in this repository for the protocol. */
object WearPaths {
    /** Phone → watch. One DataItem holding the whole list, settings and rev. */
    const val SNAPSHOT = "/noterly/snapshot"

    /** Watch → phone. One DataItem per command, at `/noterly/cmd/{commandId}`. The phone deletes it once applied. */
    const val COMMAND_PREFIX = "/noterly/cmd"

    /** Watch → phone message asking for a fresh snapshot when the watch app starts or the phone reconnects. */
    const val SYNC_REQUEST = "/noterly/sync-request"

    /** DataMap key holding the JSON string of a snapshot or a command. */
    const val KEY_JSON = "json"

    /** Capability the phone app declares in res/values/wear.xml. */
    const val CAPABILITY_PHONE = "noterly_phone"

    /** Capability the watch app declares in res/values/wear.xml. */
    const val CAPABILITY_WATCH = "noterly_watch"

    fun commandPath(commandId: String) = "$COMMAND_PREFIX/$commandId"
}
