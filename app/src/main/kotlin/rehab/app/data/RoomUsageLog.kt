package rehab.app.data

import rehab.app.data.db.UsageIntervalDao
import rehab.app.data.db.UsageIntervalEntity
import rehab.domain.model.TargetId
import rehab.domain.model.UsageInterval
import rehab.domain.ports.UsageLog
import java.time.Instant

class RoomUsageLog(private val dao: UsageIntervalDao) : UsageLog {
    override fun intervalsSince(from: Instant) = dao.since(from.toEpochMilli()).map { it.toDomain() }
    override fun openInterval() = dao.open()?.toDomain()
    override fun open(target: TargetId, start: Instant): UsageInterval {
        val id = dao.insert(UsageIntervalEntity(targetId = target.value, startUtc = start.toEpochMilli(), endUtc = start.toEpochMilli(), open = true))
        return UsageInterval(id, target, start, start, open = true)
    }
    override fun update(interval: UsageInterval) = dao.update(interval.toEntity())
    override fun delete(id: Long) = dao.delete(id)
    override fun purgeBefore(instant: Instant) = dao.purgeBefore(instant.toEpochMilli())
    fun latest(limit: Int) = dao.latest(limit).map { it.toDomain() }

    private fun UsageIntervalEntity.toDomain() =
        UsageInterval(id, TargetId(targetId), Instant.ofEpochMilli(startUtc), Instant.ofEpochMilli(endUtc), open)

    private fun UsageInterval.toEntity() =
        UsageIntervalEntity(id, target.value, start.toEpochMilli(), end.toEpochMilli(), open)
}
