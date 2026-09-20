package rehab.domain.model

import java.time.Instant

sealed interface Event {
    val at: Instant

    data class Joker(override val at: Instant, val unlockUntil: Instant) : Event
    data class Relapse(override val at: Instant, val unlockUntil: Instant) : Event
    data class ServiceOn(override val at: Instant) : Event
    data class ServiceOff(override val at: Instant) : Event
    data class RulesOutOfRange(override val at: Instant, val packageName: String, val version: String) : Event
    data class Error(override val at: Instant, val message: String) : Event
}
