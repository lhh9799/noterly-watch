package uk.co.tdsstudios.noterly.wearshared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RecurrenceTest {
    private val cases: List<JsonObject> by lazy {
        val text = javaClass.classLoader.getResource("recurrence_cases.json")!!.readText()
        Json.parseToJsonElement(text).jsonObject["cases"]!!.jsonArray.map { it.jsonObject }
    }

    @Test
    fun sharedCasesMatchThePhone() {
        var checked = 0
        for (case in cases) {
            val name = case["name"]!!.jsonPrimitive.content
            val zone = ZoneId.of(case["zone"]!!.jsonPrimitive.content)
            val repetition = WearJson.decodeFromJsonElement<RepetitionData>(case["repetition"]!!)
            val start = LocalDateTime.parse(case["start"]!!.jsonPrimitive.content)

            case["next"]?.jsonArray?.let { expected ->
                var current = start
                for (value in expected) {
                    current = Recurrence.next(repetition, current, zone)
                    assertEquals(name, LocalDateTime.parse(value.jsonPrimitive.content), current)
                    checked++
                }
            }

            case["advance"]?.let { expected ->
                val now = LocalDateTime.parse(case["now"]!!.jsonPrimitive.content)
                assertEquals(name, LocalDateTime.parse(expected.jsonPrimitive.content), Recurrence.advancePast(repetition, start, now, zone))
                checked++
            }
        }
        assert(checked > 20)
    }

    @Test
    fun alignToWeekdaysMovesToTheNextSelectedDay() {
        val repetition = RepetitionData.of(Repetition.WEEKLY, 1, listOf(3, 5))
        // 2026-10-19 is a Monday
        assertEquals(LocalDateTime.parse("2026-10-21T08:00:00"), Recurrence.alignToWeekdays(repetition, LocalDateTime.parse("2026-10-19T08:00:00")))
        assertEquals(LocalDateTime.parse("2026-10-23T08:00:00"), Recurrence.alignToWeekdays(repetition, LocalDateTime.parse("2026-10-23T08:00:00")))
    }

    @Test
    fun upcomingListsFutureRepeats() {
        val zone = ZoneId.of("Asia/Seoul")
        val repetition = RepetitionData.of(Repetition.DAILY, 1)
        val upcoming = Recurrence.upcoming(repetition, LocalDateTime.parse("2026-10-01T09:00:00"), LocalDateTime.parse("2026-10-19T10:00:00"), 3, zone)
        assertEquals(
            listOf("2026-10-20T09:00", "2026-10-21T09:00", "2026-10-22T09:00").map(LocalDateTime::parse),
            upcoming,
        )
    }
}
