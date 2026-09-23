package rehab.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import rehab.domain.model.BlockReason
import rehab.domain.model.Decision
import rehab.domain.model.Event
import rehab.domain.ports.EventLog
import java.time.Instant

/**
 * EventLog en mémoire dont `append` peut être mis en échec à volonté (simule une erreur Room),
 * pour tester le comportement d'échec fermé de [BlockJournal] sans Robolectric.
 */
private class FlakyEventLog : EventLog {
    private val items = mutableListOf<Event>()
    var failNextAppend = false
    var appendCount = 0
        private set

    override fun append(event: Event) {
        appendCount++
        if (failNextAppend) {
            failNextAppend = false
            throw RuntimeException("Room indisponible")
        }
        items += event
    }

    override fun all() = items.sortedBy { it.at }
    override fun since(from: Instant) = all().filter { it.at >= from }
}

/** IMPORTANT 2 (revue finale) : logique extraite de RehabAccessibilityService.logBlockOnce. */
class BlockJournalTest {
    private val now = Instant.parse("2026-09-22T20:00:00Z")

    private fun block(reason: BlockReason = BlockReason.Quota, until: Instant = now.plusSeconds(300)) =
        Decision.Block(reason, until)

    @Test fun `un echec d'ecriture remonte sans etre retenu`() {
        val log = FlakyEventLog().apply { failNextAppend = true }
        val journal = BlockJournal(log)

        assertThrows(RuntimeException::class.java) { journal.logOnce(block(), now) }

        // Rien n'a été écrit et lastKey n'a pas été posé : le tick suivant doit retenter (voir test suivant).
        assertEquals(0, log.all().size)
    }

    @Test fun `apres un echec, l'appel suivant retente l'ecriture`() {
        val log = FlakyEventLog().apply { failNextAppend = true }
        val journal = BlockJournal(log)
        val decision = block()

        assertThrows(RuntimeException::class.java) { journal.logOnce(decision, now) }
        journal.logOnce(decision, now) // même blocage, tick suivant : ne doit pas être ignoré comme "déjà journalisé"

        assertEquals(1, log.all().size)
        assertEquals(2, log.appendCount)
    }

    @Test fun `un meme blocage n'est ecrit qu'une fois`() {
        val log = FlakyEventLog()
        val journal = BlockJournal(log)
        val decision = block()

        journal.logOnce(decision, now)
        journal.logOnce(decision, now.plusSeconds(1)) // rejoué au tick suivant tant que l'overlay reste affiché

        assertEquals(1, log.all().size)
        assertEquals(1, log.appendCount)
    }

    @Test fun `doublon detecte apres redemarrage (tolerance 1 min)`() {
        val log = FlakyEventLog()
        val until = now.plusSeconds(300)
        // Écrit par une "instance précédente" du service, juste avant un redémarrage.
        log.append(Event.Block(now.minusSeconds(5), BlockReason.Quota, until.plusSeconds(30)))
        val journal = BlockJournal(log) // nouvelle instance : lastKey vide, comme après un redémarrage

        journal.logOnce(block(until = until), now)

        assertEquals(1, log.all().size) // pas de second Event.Block
        assertEquals(1, log.appendCount)

        // lastKey a bien été posé par la détection de doublon : un rejeu du même blocage n'écrit toujours pas.
        journal.logOnce(block(until = until), now.plusSeconds(1))
        assertEquals(1, log.appendCount)
    }
}
