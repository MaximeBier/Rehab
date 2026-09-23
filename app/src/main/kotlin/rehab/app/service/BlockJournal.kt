package rehab.app.service

import rehab.domain.model.BlockReason
import rehab.domain.model.Decision
import rehab.domain.model.Event
import rehab.domain.ports.EventLog
import java.time.Duration
import java.time.Instant

/**
 * Écrit un seul [Event.Block] par blocage (IMPORTANT 2, revue finale). Extrait de
 * `RehabAccessibilityService.logBlockOnce` pour être testable sans Robolectric : ne dépend que du
 * port [EventLog] (domain pur), aucune API Android.
 *
 * [lastKey] n'est posé qu'après un `append` réussi (ou quand un doublon post-redémarrage est
 * détecté) : si `eventLog.append` lève, rien n'est retenu et le tick suivant retentera l'écriture,
 * au lieu d'oublier silencieusement ce blocage pour toujours.
 */
class BlockJournal(private val eventLog: EventLog) {
    private var lastKey: Pair<BlockReason, Instant>? = null

    fun logOnce(decision: Decision.Block, now: Instant) {
        val key = decision.reason to decision.unlockAt
        if (key == lastKey) return
        // Après un redémarrage du service, ne pas réécrire un blocage déjà journalisé (tolérance d'une minute sur la fin).
        val previous = eventLog.since(now.minus(Duration.ofDays(1))).filterIsInstance<Event.Block>().lastOrNull()
        if (previous != null && previous.reason == decision.reason &&
            Duration.between(previous.until, decision.unlockAt).abs() < Duration.ofMinutes(1)
        ) {
            lastKey = key
            return
        }
        eventLog.append(Event.Block(now, decision.reason, decision.unlockAt))
        lastKey = key
    }
}
