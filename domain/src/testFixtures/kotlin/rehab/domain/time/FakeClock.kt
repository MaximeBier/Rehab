package rehab.domain.time

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Déplacée en `java-test-fixtures` (revue finale, mineur) : cette classe et `InMemory*` (voir
 * `rehab.domain.fakes`) vivaient dans `src/main`, donc partaient dans l'APK — le build release n'étant pas
 * minifié (`isMinifyEnabled = false`), rien ne les en retirait.
 */
class FakeClock(start: Instant, private val zoneId: ZoneId = ZoneId.of("Europe/Paris")) : Clock {
    private var current: Instant = start
    override fun now(): Instant = current
    override fun zone(): ZoneId = zoneId
    fun advance(d: Duration) { current = current.plus(d) }
    fun set(instant: Instant) { current = instant }
}
