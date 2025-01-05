<div align="center">

# halyard

A cron expression parser and next-run calculator for Kotlin, with a small scheduler loop. Nothing beyond the JDK and `java.time`.

![License](https://img.shields.io/badge/LICENSE-MIT-blue?style=for-the-badge)  ![Language](https://img.shields.io/badge/KOTLIN-7f52ff?style=for-the-badge&logo=kotlin&logoColor=white)  ![Tests](https://img.shields.io/badge/TESTS-13-brightgreen?style=for-the-badge)

Refer to [LICENSE](LICENSE) for the legal bits.

</div>

## What's in it

- **`CronExpression.parse`** reads a standard five-field expression: minute, hour, day of month, month and day of week. Fields accept `*`, single values, comma lists, ranges and steps such as `*/15` or `5-25/5`. Months and weekdays can be names (`JAN`, `MON-FRI`), and Sunday can be 0 or 7.
- **`next(after)`** returns the first matching `ZonedDateTime` strictly after the one you give it, in that value's own time zone. **`matches`** checks a single `LocalDateTime`.
- **`CronParseException`** with a message naming the field and the bad item.
- **`Scheduler`**, a single-threaded loop that runs registered jobs. It takes a `Clock` and a sleeper function, so it can be driven without waiting in real time.

```kotlin
val cron = CronExpression.parse("30 8 * * MON-FRI")
val next = cron.next(ZonedDateTime.now())

val scheduler = Scheduler()
scheduler.schedule("report", "*/10 * * * *") { println("running") }
scheduler.run()
```

`run` blocks until `stop` is called from another thread or from inside a job.

## 🏗️ Architecture

```
 "30 8 * * MON-FRI"
        │  CronExpression.parse
        │    split on whitespace -> parseField x5 (list, range, step, names)
        v
 CronExpression { minutes, hours, daysOfMonth, months, daysOfWeek : Set<Int> }
        │
        │  next(after): walk a LocalDateTime forward
        │     wrong month -> jump to the 1st of next month
        │     wrong day   -> jump to the next midnight
        │     wrong hour -> jump to the next hour
        │     wrong minute -> +1 minute
        │     ZonedDateTime.of(...) must round-trip (DST gap) and be after `after`
        v
 ZonedDateTime?  (null after eight years with no match)

 Scheduler(clock, sleeper)
   jobs: name + CronExpression + action + nextRun
   runDue() -> names fired      run() -> runDue, sleep until the earliest nextRun

 src/main/kotlin/au/provokke/halyard/  CronExpression.kt  Scheduler.kt
 src/test/kotlin/au/provokke/halyard/  CronExpressionTest.kt   build.gradle.kts
```

- When both day of month and day of week are restricted, a time matches if either one does, as in Vixie cron.
- The search skips whole months, days and hours rather than stepping a minute at a time, and gives up after eight years. That returns `null` for dates such as 31 February.
- Local times that don't exist in a daylight saving gap are skipped. Times that occur twice in an overlap fire once.
- The scheduler is deliberately simple: one thread, jobs run in order and a slow job delays the others. There's no persistence and no missed-run catch-up.
- Seconds and extensions such as `@daily`, `L` and `#` aren't supported.

## 🚀 Quick start

```
gradle test
```

There's no Gradle wrapper, so use a local Gradle 8 with JDK 21. For example, from 10:07:30 UTC on 1 March 2026, `*/15 * * * *` gives:

```
2026-03-01T10:15
```

## 🧪 Testing

13 tests, all passing (JUnit via `kotlin.test`):

- Parsing and `next`: every minute, steps and lists, ranges rolling over the hour, month and weekday names, Sunday as 0 or 7, day of month and weekday being OR-ed and a 29 February waiting for 2028.
- Edge cases: an impossible date (31 February) returns `null`, the result is strictly after the start and the Australia/Sydney zone is preserved.
- Rejection: ten malformed expressions all throw `CronParseException` (wrong field count, out of range, zero step, backwards range, junk, empty list item and unknown name).
- `matches` checks every field.
- Scheduler: a fake clock and sleeper drive due jobs, nothing fires early, a job fires once per slot and `stop` from inside a job ends `run`.

## Licence

MIT.
