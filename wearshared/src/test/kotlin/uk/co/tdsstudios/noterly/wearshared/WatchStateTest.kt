package uk.co.tdsstudios.noterly.wearshared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class WatchStateTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.parse("2026-10-19T12:00:00")
    private val weekly = RepetitionData.of(Repetition.WEEKLY, 1, listOf(1, 3, 5))

    private val single = NoteItem(id = "a1b2c3d4-0000-4000-8000-000000000001", title = "Call mum", dateTime = "2026-10-19T18:00:00.000")
    private val repeating = NoteItem(
        id = "a1b2c3d4-0000-4000-8000-000000000002", title = "Gym", dateTime = "2026-10-19T09:00:00.000", repetitionData = weekly,
    )
    private val snapshot = Snapshot(rev = 7, items = listOf(single, repeating))

    private fun command(type: String, item: NoteItem, payload: CommandPayload = CommandPayload(), at: LocalDateTime = now) =
        Commands.new(type, item.id, snapshot.rev, at, payload)

    @Test
    fun doneArchivesASingleReminder() {
        val items = WatchState.overlay(snapshot, listOf(command(CommandType.DONE, single)), zone)
        assertTrue(items.first { it.id == single.id }.archived)
        assertEquals(listOf(repeating.id), WatchState.activeSorted(items, zone).map { it.id })
    }

    @Test
    fun doneMovesARepeatToTheNextSelectedWeekday() {
        val done = command(CommandType.DONE, repeating, CommandPayload(seenDateTime = repeating.dateTime))
        val item = WatchState.overlay(snapshot, listOf(done), zone).first { it.id == repeating.id }
        assertEquals("2026-10-21T09:00:00.000", item.dateTime)
        assertFalse(item.archived)
        assertNull(item.snoozeDateTime)
    }

    @Test
    fun doneBeforeARepeatIsDueSkipsThatRepeat() {
        // Wednesday 09:00 is still ahead at Monday noon; done counts for it and moves on to Friday
        val upcoming = repeating.copy(dateTime = "2026-10-21T09:00:00.000")
        val done = command(CommandType.DONE, upcoming, CommandPayload(seenDateTime = upcoming.dateTime))
        val item = WatchState.overlay(snapshot.copy(items = listOf(single, upcoming)), listOf(done), zone).first { it.id == upcoming.id }
        assertEquals("2026-10-23T09:00:00.000", item.dateTime)
    }

    @Test
    fun aRepeatDoneOnBothSidesOnlyMovesOnce() {
        // The phone already marked it as done and moved it on to Wednesday; the watch's command saw Monday
        val phoneDone = snapshot.copy(items = listOf(single, repeating.copy(dateTime = "2026-10-21T09:00:00.000")))
        val done = command(CommandType.DONE, repeating, CommandPayload(seenDateTime = repeating.dateTime))
        val item = WatchState.overlay(phoneDone, listOf(done), zone).first { it.id == repeating.id }
        assertEquals("2026-10-21T09:00:00.000", item.dateTime)
    }

    @Test
    fun snoozeUsesThePhonesSnoozeLength() {
        val withSettings = snapshot.copy(settings = SnapshotSettings(snoozeSeconds = 1800))
        val item = WatchState.overlay(withSettings, listOf(command(CommandType.SNOOZE, single)), zone).first { it.id == single.id }
        assertEquals("2026-10-19T12:30:00.000", item.snoozeDateTime)
        assertEquals(LocalDateTime.parse("2026-10-19T12:30:00"), WatchState.fireTime(item, now, zone))
    }

    @Test
    fun anUpdateOlderThanThePhonesEditIsDropped() {
        val editedOnPhone = snapshot.copy(items = listOf(single.copy(updatedAt = "2026-10-19T12:05:00.000"), repeating))
        val update = command(CommandType.UPDATE, single, CommandPayload(title = "Call dad", dateTime = single.dateTime))
        assertEquals("Call mum", WatchState.overlay(editedOnPhone, listOf(update), zone).first { it.id == single.id }.title)
        assertEquals("Call dad", WatchState.overlay(snapshot, listOf(update), zone).first { it.id == single.id }.title)
    }

    @Test
    fun acknowledgedCommandsAreNotAppliedAgain() {
        val done = command(CommandType.DONE, single)
        val acked = snapshot.copy(rev = 8, appliedCommands = listOf(done.commandId))
        assertEquals(setOf(done.commandId), WatchState.acknowledged(acked, listOf(done)))
        assertFalse(WatchState.overlay(acked, listOf(done), zone).first { it.id == single.id }.archived)
    }

    @Test
    fun olderSnapshotsAreIgnoredUnlessThePhoneWasReset() {
        val stored = snapshot.copy(rev = 40, generatedAt = "2026-10-19T12:00:00.000")
        assertTrue(WatchState.replacesSnapshot(null, snapshot, zone))
        assertTrue(WatchState.replacesSnapshot(stored, stored.copy(rev = 41, generatedAt = "2026-10-19T12:01:00.000"), zone))
        // Out of order: made before the stored one
        assertFalse(WatchState.replacesSnapshot(stored, stored.copy(rev = 39, generatedAt = "2026-10-19T11:59:00.000"), zone))
        // Phone app reinstalled: its rev starts again
        assertTrue(WatchState.replacesSnapshot(stored, stored.copy(rev = 2, generatedAt = "2026-10-19T12:05:00.000"), zone))
        assertFalse(WatchState.replacesSnapshot(stored, stored.copy(rev = 2, generatedAt = null), zone))
    }

    @Test
    fun createDeleteAndRestore() {
        val created = Commands.new(CommandType.CREATE, "a1b2c3d4-0000-4000-8000-000000000003", 7, now,
            CommandPayload(title = "Water plants", dateTime = "2026-10-19T13:00:00.000", colour = 0xFF4CAF50))
        var items = WatchState.overlay(snapshot, listOf(created), zone)
        assertEquals(listOf(single.id, created.itemId, repeating.id).sorted(), items.map { it.id }.sorted())
        assertEquals(0xFF4CAF50, items.first { it.id == created.itemId }.colour)

        items = WatchState.apply(items, command(CommandType.DELETE, single), snapshot.settings, zone)
        assertFalse(items.any { it.id == single.id })

        val archived = snapshot.copy(items = listOf(single.copy(archived = true, archivedDateTime = "2026-10-18T10:00:00.000")))
        assertFalse(WatchState.overlay(archived, listOf(command(CommandType.RESTORE, single)), zone).first().archived)
    }

    @Test
    fun activeListIsImmediateFirstThenByTime() {
        val immediate = NoteItem(id = "i", title = "Now")
        val later = NoteItem(id = "l", title = "Later", dateTime = "2026-10-20T08:00:00.000")
        val snoozed = NoteItem(id = "s", title = "Snoozed", dateTime = "2026-10-19T08:00:00.000", snoozeDateTime = "2026-10-19T13:00:00.000")
        val sorted = WatchState.activeSorted(listOf(later, snoozed, immediate, single), zone).map { it.id }
        assertEquals(listOf("i", "s", single.id, "l"), sorted)
    }

    @Test
    fun activeSectionsFollowThePhone() {
        val immediate = NoteItem(id = "i", title = "Now")
        val later = NoteItem(id = "l", title = "Later", dateTime = "2026-10-20T08:00:00.000")
        val snoozedImmediate = NoteItem(id = "s", title = "Snoozed", snoozeDateTime = "2026-10-19T13:00:00.000")
        val done = NoteItem(id = "d", title = "Done", archived = true)
        val sections = WatchState.activeSections(listOf(repeating, later, snoozedImmediate, done, immediate, single), zone)
            .map { (section, items) -> section to items.map { it.id } }
        assertEquals(
            listOf(
                ListSection.IMMEDIATE to listOf("i", "s"),
                ListSection.SCHEDULED to listOf(single.id, "l"),
                ListSection.REPEATING to listOf(repeating.id),
            ),
            sections,
        )
        assertEquals(listOf(ListSection.SCHEDULED), WatchState.activeSections(listOf(later), zone).map { it.first })
    }

    @Test
    fun noOwnNotificationForPastOrImmediateItems() {
        assertNull(WatchState.fireTime(NoteItem(id = "i", title = "Now"), now, zone))
        assertNull(WatchState.fireTime(repeating, now, zone)) // Monday 09:00 already passed
        assertEquals(LocalDateTime.parse("2026-10-19T18:00:00"), WatchState.fireTime(single, now, zone))
    }

    @Test
    fun notificationIdsAreStableAndDistinct() {
        assertEquals(WatchState.notificationId(single.id), WatchState.notificationId(single.id))
        assertNotEquals(WatchState.notificationId(single.id), WatchState.notificationId(repeating.id))
    }

    @Test
    fun timesRoundTripInDartFormat() {
        assertEquals("2026-10-19T09:00:00.000", DartTime.format(LocalDateTime.parse("2026-10-19T09:00:00")))
        assertEquals("2026-10-19T09:00:00.123456", DartTime.format(LocalDateTime.parse("2026-10-19T09:00:00.123456")))
        assertEquals(LocalDateTime.parse("2026-10-19T09:00:00.123456"), DartTime.parse("2026-10-19T09:00:00.123456"))
        assertEquals(LocalDateTime.parse("2026-10-19T09:00:00"), DartTime.parse("2026-10-19T00:00:00.000Z", zone))
    }

    @Test
    fun snapshotJsonFromThePhoneParses() {
        val json = """
            {"schema":1,"rev":128,"generatedAt":"2026-10-19T09:00:00.000","settings":{"snoozeSeconds":3600,"dismissibleEnabled":true},
             "items":[{"id":"x","title":"T","body":"B","dateTime":"2026-10-20T08:00:00.000","repetitionData":{"type":2,"number":1,"weekdays":[1,3,5]},
               "colour":4294198070,"archived":false,"archivedDateTime":null,"snoozeDateTime":null,"dismissible":true,
               "updatedAt":"2026-10-19T08:55:12.000","upcoming":["2026-10-20T08:00:00.000"],"repeatLabel":"weekly (Mon, Wed, Fri)","unknown":1}],
             "appliedCommands":[]}
        """.trimIndent()
        val parsed = WearJson.decodeFromString(Snapshot.serializer(), json)
        assertEquals(128, parsed.rev)
        assertEquals(0xFFF44336, parsed.items.single().colour)
        assertTrue(parsed.items.single().repetitionData!!.hasWeekdays)
        // This phone app follows the phone's language
        assertNull(parsed.settings.language)
    }

    @Test
    fun theSnapshotCarriesTheLanguagePickedForThePhoneApp() {
        val json = """{"schema":1,"rev":3,"settings":{"snoozeSeconds":3600,"language":"en-GB"},"items":[],"appliedCommands":[]}"""
        assertEquals("en-GB", WearJson.decodeFromString(Snapshot.serializer(), json).settings.language)
    }

    @Test
    fun onlyActiveImmediateRemindersNotifyAtOnce() {
        val immediate = NoteItem(id = "i", title = "Now")
        assertTrue(WatchState.notifiesImmediately(immediate, now, zone))
        assertFalse(WatchState.notifiesImmediately(immediate.copy(archived = true), now, zone))
        assertFalse(WatchState.notifiesImmediately(single, now, zone))
        assertFalse(WatchState.notifiesImmediately(immediate.copy(snoozeDateTime = "2026-10-19T13:00:00.000"), now, zone))
        assertFalse(WatchState.notifiesImmediately(null, now, zone))
    }

    @Test
    fun onlyDismissibleRemindersNotifyOnTheWatch() {
        val on = NoteItem(id = "i", title = "Now", dismissible = true)
        assertTrue(WatchState.notifiesOnWatch(on, SnapshotSettings()))
        assertFalse(WatchState.notifiesOnWatch(on.copy(dismissible = false), SnapshotSettings()))
        assertFalse(WatchState.notifiesOnWatch(on, SnapshotSettings(dismissibleEnabled = false)))
    }

    @Test
    fun aReminderMadeOnTheWatchTakesTheDefaultFromThePhone() {
        val created = Commands.new(CommandType.CREATE, "a1b2c3d4-0000-4000-8000-000000000004", 7, now, CommandPayload(title = "Call"))
        assertFalse(WatchState.apply(emptyList(), created, SnapshotSettings(), zone).single().dismissible)
        assertTrue(WatchState.apply(emptyList(), created, SnapshotSettings(dismissibleByDefault = true), zone).single().dismissible)
    }

    @Test
    fun aRestoredReminderWhoseTimeHasPassedNotifiesAtOnce() {
        // The phone shows a reminder at once when its time or snooze is already over
        val immediate = NoteItem(id = "i", title = "Now")
        assertTrue(WatchState.notifiesImmediately(immediate.copy(snoozeDateTime = "2026-10-19T11:00:00.000"), now, zone))
        assertTrue(WatchState.notifiesImmediately(single.copy(dateTime = "2026-10-19T09:00:00.000"), now, zone))
    }

    @Test
    fun doneAndRestoredKeepsTheSnoozeLikeThePhone() {
        val snoozed = single.copy(snoozeDateTime = "2026-10-19T11:00:00.000")
        val done = command(CommandType.DONE, snoozed)
        val restore = command(CommandType.RESTORE, snoozed)
        val item = WatchState.overlay(snapshot.copy(items = listOf(snoozed)), listOf(done, restore), zone).single()
        assertFalse(item.archived)
        assertEquals("2026-10-19T11:00:00.000", item.snoozeDateTime)
    }

    @Test
    fun immediateOwnNotificationStaysUntilDoneOrGivenALaterTime() {
        val immediate = NoteItem(id = "i", title = "Now")
        val nowMillis = DartTime.toEpochMillis(now, zone)
        assertFalse(WatchState.ownNotificationStale(immediate, null, nowMillis, zone))
        assertTrue(WatchState.ownNotificationStale(null, null, nowMillis, zone))
        assertTrue(WatchState.ownNotificationStale(immediate.copy(archived = true), null, nowMillis, zone))
        assertTrue(WatchState.ownNotificationStale(immediate.copy(snoozeDateTime = "2026-10-19T13:00:00.000"), null, nowMillis, zone))
        assertFalse(WatchState.ownNotificationStale(immediate.copy(dateTime = "2026-10-19T11:00:00.000"), null, nowMillis, zone))
    }

    @Test
    fun scheduledOwnNotificationGoesWhenMovedOn() {
        val shown = DartTime.toEpochMillis(LocalDateTime.parse("2026-10-19T18:00:00"), zone)
        val nowMillis = DartTime.toEpochMillis(now, zone)
        assertFalse(WatchState.ownNotificationStale(single, shown, nowMillis, zone))
        assertTrue(WatchState.ownNotificationStale(single.copy(snoozeDateTime = "2026-10-19T19:00:00.000"), shown, nowMillis, zone))
        assertTrue(WatchState.ownNotificationStale(single.copy(dateTime = null), shown, nowMillis, zone))
    }

    @Test
    fun bridgeTagsMatchThePhone() {
        assertEquals("noterly:id:1792400000000", WatchState.bridgeTag("id", 1792400000000))
        assertEquals("noterly:id:immediate", WatchState.bridgeTag("id", null))
    }
}
