package rehab.app.data

import rehab.app.data.db.EventDao
import rehab.app.data.db.EventEntity
import rehab.domain.model.BlockReason
import rehab.domain.model.Event
import rehab.domain.ports.EventLog
import java.time.Instant

class RoomEventLog(private val dao: EventDao, private val maxErrors: Int = 500) : EventLog {

    override fun append(event: Event) {
        dao.insert(event.toEntity())
        if (event is Event.Error) {
            val excess = dao.countErrors() - maxErrors
            if (excess > 0) dao.deleteOldestErrors(excess)
        }
    }

    override fun all(): List<Event> = dao.all().mapNotNull { it.toDomain() }

    override fun since(from: Instant): List<Event> = dao.since(from.toEpochMilli()).mapNotNull { it.toDomain() }

    private fun Event.toEntity(): EventEntity = when (this) {
        is Event.Joker -> EventEntity(type = "JOKER", atUtc = at.toEpochMilli(), unlockUntilUtc = unlockUntil.toEpochMilli())
        is Event.Relapse -> EventEntity(type = "RELAPSE", atUtc = at.toEpochMilli(), unlockUntilUtc = unlockUntil.toEpochMilli())
        is Event.ServiceOn -> EventEntity(type = "SERVICE_ON", atUtc = at.toEpochMilli())
        is Event.ServiceOff -> EventEntity(type = "SERVICE_OFF", atUtc = at.toEpochMilli())
        is Event.RulesOutOfRange -> EventEntity(type = "RULES_OUT_OF_RANGE", atUtc = at.toEpochMilli(), packageName = packageName, version = version)
        is Event.Error -> EventEntity(type = "ERROR", atUtc = at.toEpochMilli(), message = message)
        is Event.Block -> EventEntity(type = "BLOCK", atUtc = at.toEpochMilli(), unlockUntilUtc = until.toEpochMilli(), message = reason.name)
    }

    private fun EventEntity.toDomain(): Event? {
        val at = Instant.ofEpochMilli(atUtc)
        return when (type) {
            "JOKER" -> Event.Joker(at, Instant.ofEpochMilli(unlockUntilUtc ?: return null))
            "RELAPSE" -> Event.Relapse(at, Instant.ofEpochMilli(unlockUntilUtc ?: return null))
            "SERVICE_ON" -> Event.ServiceOn(at)
            "SERVICE_OFF" -> Event.ServiceOff(at)
            "RULES_OUT_OF_RANGE" -> Event.RulesOutOfRange(at, packageName ?: return null, version ?: return null)
            "ERROR" -> Event.Error(at, message ?: "")
            "BLOCK" -> Event.Block(
                at,
                BlockReason.entries.firstOrNull { it.name == message } ?: return null,
                Instant.ofEpochMilli(unlockUntilUtc ?: return null),
            )
            else -> null
        }
    }
}
