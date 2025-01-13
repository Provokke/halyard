package au.provokke.halyard

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CronExpressionTest {
    private val utc = ZoneOffset.UTC

    private fun at(text: String) = ZonedDateTime.parse(text).withZoneSameInstant(utc)

    private fun next(expression: String, from: String) =
        CronExpression.parse(expression).next(at(from))?.toLocalDateTime().toString()

    @Test
    fun everyMinuteAdvancesOneMinute() {
        assertEquals("2026-03-01T10:01", next("* * * * *", "2026-03-01T10:00:00Z"))
    }

    @Test
    fun stepsAndLists() {
        assertEquals("2026-03-01T10:15", next("*/15 * * * *", "2026-03-01T10:07:30Z"))
        assertEquals("2026-03-01T11:00", next("0,30 * * * *", "2026-03-01T10:45:00Z"))
        assertEquals("2026-03-01T10:20", next("5-25/5 * * * *", "2026-03-01T10:16:00Z"))
    }

    @Test
    fun rangesRollOverHours() {
        assertEquals("2026-03-02T09:00", next("0 9-17 * * *", "2026-03-01T17:30:00Z"))
    }

    @Test
    fun namesForMonthsAndWeekdays() {
        assertEquals("2026-12-01T00:00", next("0 0 1 dec *", "2026-03-01T00:00:00Z"))
        assertEquals("2026-03-02T08:30", next("30 8 * * MON-FRI", "2026-02-28T12:00:00Z"))
    }

    @Test
    fun sundayIsZeroOrSeven() {
        assertEquals(next("0 12 * * 0", "2026-03-01T13:00:00Z"), next("0 12 * * 7", "2026-03-01T13:00:00Z"))
        assertEquals("2026-03-08T12:00", next("0 12 * * 7", "2026-03-01T13:00:00Z"))
    }

    @Test
    fun dayOfMonthAndWeekdayAreOred() {
        assertEquals("2026-03-02T00:00", next("0 0 15 * MON", "2026-03-01T00:00:00Z"))
        assertEquals("2026-03-15T00:00", next("0 0 15 * MON", "2026-03-14T00:00:00Z"))
    }

    @Test
    fun leapDayWaitsForLeapYear() {
        assertEquals("2028-02-29T00:00", next("0 0 29 2 *", "2026-03-01T00:00:00Z"))
    }

    @Test
    fun impossibleDateReturnsNull() {
        assertNull(CronExpression.parse("0 0 31 2 *").next(at("2026-01-01T00:00:00Z")))
    }

    @Test
    fun resultIsStrictlyAfterStart() {
        val start = at("2026-03-01T10:00:00Z")
        assertTrue(CronExpression.parse("0 10 * * *").next(start)!!.isAfter(start))
    }

    @Test
    fun respectsTimeZone() {
        val sydney = ZoneId.of("Australia/Sydney")
        val from = ZonedDateTime.of(2026, 7, 1, 8, 59, 0, 0, sydney)
        val result = CronExpression.parse("0 9 * * *").next(from)!!
        assertEquals(9, result.hour)
        assertEquals(sydney, result.zone)
    }

    @Test
    fun rejectsBadExpressions() {
        listOf("* * * *", "60 * * * *", "* 24 * * *", "* * 0 * *", "* * * 13 *", "*/0 * * * *",
            "5-1 * * * *", "a * * * *", "1,,2 * * * *", "* * * foo *").forEach {
            assertFailsWith<CronParseException>(it) { CronExpression.parse(it) }
        }
    }

    @Test
    fun matchesChecksEveryField() {
        val cron = CronExpression.parse("30 14 * * *")
        assertTrue(cron.matches(at("2026-03-01T14:30:00Z").toLocalDateTime()))
        assertEquals(false, cron.matches(at("2026-03-01T14:31:00Z").toLocalDateTime()))
    }

    @Test
    fun schedulerFiresDueJobs() {
        var now = Instant.parse("2026-03-01T10:00:00Z")
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = now
        }
        val fired = mutableListOf<String>()
        val scheduler = Scheduler(clock) { wait -> now = now.plus(wait) }
        scheduler.schedule("tick", "*/5 * * * *") { fired.add("tick") }
        assertEquals(emptyList(), scheduler.runDue())
        now = now.plus(Duration.ofMinutes(5))
        assertEquals(listOf("tick"), scheduler.runDue())
        assertEquals(emptyList(), scheduler.runDue())
        scheduler.schedule("stopper", "*/5 * * * *") { scheduler.stop() }
        scheduler.run()
        assertTrue(fired.size >= 2)
    }
}

