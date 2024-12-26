package au.provokke.halyard

import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean

class Scheduler(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val sleeper: (Duration) -> Unit = { Thread.sleep(it.toMillis()) },
) {
    private class Job(val name: String, val cron: CronExpression, val action: () -> Unit) {
        var nextRun: ZonedDateTime? = null
    }

    private val jobs = mutableListOf<Job>()
    private val running = AtomicBoolean(false)

    fun schedule(name: String, expression: String, action: () -> Unit) {
        jobs.add(Job(name, CronExpression.parse(expression), action))
    }

    fun stop() {
        running.set(false)
    }

    fun runDue(): List<String> {
        val now = ZonedDateTime.now(clock)
        val fired = mutableListOf<String>()
        for (job in jobs) {
            val due = job.nextRun ?: job.cron.next(now)
            job.nextRun = due
            if (due != null && !due.isAfter(now)) {
                job.action()
                fired.add(job.name)
                job.nextRun = job.cron.next(now)
            }
        }
        return fired
    }

    fun run() {
        running.set(true)
        while (running.get()) {
            runDue()
            val now = ZonedDateTime.now(clock)
            val upcoming = jobs.mapNotNull { it.nextRun }.minOrNull() ?: break
            val wait = Duration.between(now, upcoming)
            if (!wait.isNegative && !wait.isZero) sleeper(wait)
        }
        running.set(false)
    }
}
