package klite.jobs

import ch.tutteli.atrium.api.fluent.en_GB.notToEqualNull
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import klite.Server
import klite.jdbc.Transaction
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS
import javax.sql.DataSource

class JobRunnerTest {
  val db = mockk<DataSource>()
  val executor = mockk<ScheduledExecutorService>(relaxed = true)
  val job = MyJob()
  val runner = JobRunner(db, executor)

  class MyJob: Job {
    override suspend fun run() {
      expect(Transaction.current()).notToEqualNull()
    }
  }

  @Test fun `adds shutdown handler`() {
    val server = mockk<Server>(relaxed = true)
    runner.install(server)
    verify { server.onStop(any()) }
  }

  @Test fun runInTransaction() {
    runner.runInTransaction("My job", job)
  }

  @Test fun schedule() {
    runner.schedule(job, 10, 20, SECONDS)
    verify { executor.scheduleAtFixedRate(any(), 10, 20, SECONDS) }
  }

  @Test fun `scheduleDaily with delay`() {
    runner.scheduleDaily(job, delayMinutes = 10)
    verify { executor.scheduleAtFixedRate(any(), 10, 24 * 60, MINUTES) }
  }

  @Test fun `scheduleDaily at time`() {
    runner.scheduleDaily(job, LocalTime.of(6, 30), LocalTime.of(6, 45))
    verify(exactly = 2) { executor.scheduleAtFixedRate(any(), match { it > 0 }, 24 * 60, MINUTES) }
  }

  @Test fun `scheduleMonthly today`() {
    val job = mockk<Job>(relaxed = true)
    runner.scheduleMonthly(job, LocalDate.now().dayOfMonth, LocalTime.of(6, 30))
    val dailyJob = slot<Runnable>()
    verify { executor.scheduleAtFixedRate(capture(dailyJob), match { it > 0 }, 24 * 60, MINUTES) }
    dailyJob.captured.run()
    coVerify { job.run() }
  }

  @Test fun `scheduleMonthly not today`() {
    val job = mockk<Job>(relaxed = true)
    runner.scheduleMonthly(job, LocalDate.now().dayOfMonth + 1, LocalTime.of(6, 30))
    val dailyJob = slot<Runnable>()
    verify { executor.scheduleAtFixedRate(capture(dailyJob), match { it > 0 }, 24 * 60, MINUTES) }
    dailyJob.captured.run()
    coVerify(exactly = 0) { job.run() }
  }
}
