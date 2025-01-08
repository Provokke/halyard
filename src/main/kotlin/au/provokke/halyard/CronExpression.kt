package au.provokke.halyard

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class CronParseException(message: String) : IllegalArgumentException(message)

class CronExpression private constructor(
    private val minutes: Set<Int>,
    private val hours: Set<Int>,
    private val daysOfMonth: Set<Int>,
    private val months: Set<Int>,
    private val daysOfWeek: Set<Int>,
    private val dayOfMonthRestricted: Boolean,
    private val dayOfWeekRestricted: Boolean,
    val source: String,
) {
    fun matches(time: LocalDateTime): Boolean =
        time.minute in minutes &&
            time.hour in hours &&
            time.monthValue in months &&
            dayMatches(time)

    private fun dayMatches(time: LocalDateTime): Boolean {
        val domMatch = time.dayOfMonth in daysOfMonth
        val dowMatch = (time.dayOfWeek.value % 7) in daysOfWeek
        return if (dayOfMonthRestricted && dayOfWeekRestricted) domMatch || dowMatch else domMatch && dowMatch
    }

    fun next(after: ZonedDateTime): ZonedDateTime? {
        val zone: ZoneId = after.zone
        var candidate = after.toLocalDateTime().truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        val limit = candidate.plusYears(8)
        while (candidate.isBefore(limit)) {
            if (candidate.monthValue !in months) {
                candidate = candidate.withDayOfMonth(1).toLocalDate().plusMonths(1).atStartOfDay()
                continue
            }
            if (!dayMatches(candidate)) {
                candidate = candidate.toLocalDate().plusDays(1).atStartOfDay()
                continue
            }
            if (candidate.hour !in hours) {
                candidate = candidate.truncatedTo(ChronoUnit.HOURS).plusHours(1)
                continue
            }
            if (candidate.minute !in minutes) {
                candidate = candidate.plusMinutes(1)
                continue
            }
            val resolved = ZonedDateTime.of(candidate, zone)
            if (resolved.toLocalDateTime() == candidate && resolved.isAfter(after)) return resolved
            candidate = candidate.plusMinutes(1)
        }
        return null
    }

    override fun toString(): String = source

    companion object {
        private val MONTH_NAMES = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        private val DAY_NAMES = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

        fun parse(expression: String): CronExpression {
            val parts = expression.trim().split(Regex("\\s+"))
            if (parts.size != 5) {
                throw CronParseException("expected 5 fields but found ${parts.size}")
            }
            val minutes = parseField(parts[0], 0, 59, emptyList(), "minute")
            val hours = parseField(parts[1], 0, 23, emptyList(), "hour")
            val days = parseField(parts[2], 1, 31, emptyList(), "day of month")
            val months = parseField(parts[3], 1, 12, MONTH_NAMES, "month", nameOffset = 1)
            val weekdays = parseField(parts[4], 0, 7, DAY_NAMES, "day of week").map { it % 7 }.toSet()
            return CronExpression(
                minutes, hours, days, months, weekdays,
                dayOfMonthRestricted = !parts[2].startsWith("*"),
                dayOfWeekRestricted = !parts[4].startsWith("*"),
                source = parts.joinToString(" "),
            )
        }

        private fun parseField(
            field: String,
            min: Int,
            max: Int,
            names: List<String>,
            label: String,
            nameOffset: Int = 0,
        ): Set<Int> {
            val result = sortedSetOf<Int>()
            for (item in field.split(",")) {
                if (item.isEmpty()) throw CronParseException("empty item in $label field")
                val stepSplit = item.split("/")
                if (stepSplit.size > 2) throw CronParseException("bad step in $label field: $item")
                val step = if (stepSplit.size == 2) {
                    stepSplit[1].toIntOrNull()?.takeIf { it > 0 }
                        ?: throw CronParseException("bad step in $label field: $item")
                } else {
                    1
                }
                val range = stepSplit[0]
                val (low, high) = when {
                    range == "*" -> min to max
                    "-" in range -> {
                        val ends = range.split("-")
                        if (ends.size != 2) throw CronParseException("bad range in $label field: $item")
                        value(ends[0], names, nameOffset, label) to value(ends[1], names, nameOffset, label)
                    }
                    stepSplit.size == 2 -> value(range, names, nameOffset, label) to max
                    else -> value(range, names, nameOffset, label).let { it to it }
                }
                if (low < min || high > max || low > high) {
                    throw CronParseException("$label value out of range ($min-$max): $item")
                }
                for (v in low..high step step) result.add(v)
            }
            return result
        }

        private fun value(text: String, names: List<String>, nameOffset: Int, label: String): Int {
            text.toIntOrNull()?.let { return it }
            val index = names.indexOf(text.uppercase())
            if (index < 0) throw CronParseException("unknown $label value: $text")
            return index + nameOffset
        }
    }
}

