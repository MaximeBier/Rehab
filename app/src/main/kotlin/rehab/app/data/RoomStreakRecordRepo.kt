package rehab.app.data

import rehab.app.data.db.StreakRecordDao
import rehab.app.data.db.StreakRecordEntity
import rehab.domain.ports.StreakRecordRepo
import rehab.domain.time.Clock
import java.time.Instant

class RoomStreakRecordRepo(private val dao: StreakRecordDao, private val clock: Clock) : StreakRecordRepo {
    private fun row(): StreakRecordEntity =
        dao.get() ?: StreakRecordEntity(bestDays = 0, installedAtUtc = clock.now().toEpochMilli()).also(dao::upsert)

    override fun bestDays() = row().bestDays
    override fun setBestDays(days: Int) = dao.upsert(row().copy(bestDays = days))
    override fun installedAt(): Instant = Instant.ofEpochMilli(row().installedAtUtc)
}
