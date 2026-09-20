package rehab.domain.policy

import rehab.domain.model.QuotaWindow
import rehab.domain.model.TargetId
import rehab.domain.model.UsageInterval
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlidingQuotaTest {
    private val t0 = Instant.parse("2026-09-21T10:00:00Z")
    private val reels = TargetId("InstagramReels")
    private val quota = SlidingQuota()
    private val w30 = QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(5))
    private val w6h = QuotaWindow(Duration.ofHours(6), Duration.ofMinutes(30))

    private fun iv(startMin: Long, endMin: Long, open: Boolean = false) =
        UsageInterval(0, reels, t0.plusSeconds(startMin * 60), t0.plusSeconds(endMin * 60), open)

    @Test fun `somme des intervalles dans la fenetre`() {
        val now = t0.plusSeconds(40 * 60)
        val used = quota.usedIn(listOf(iv(0, 3), iv(20, 22)), now.minus(Duration.ofMinutes(30)), now, now)
        assertEquals(Duration.ofMinutes(2), used) // iv(0,3) est sorti de la fenêtre [10,40]
    }

    @Test fun `intervalle chevauchant la borne est tronque`() {
        val now = t0.plusSeconds(40 * 60)
        val used = quota.usedIn(listOf(iv(5, 15)), now.minus(Duration.ofMinutes(30)), now, now)
        assertEquals(Duration.ofMinutes(5), used)
    }

    @Test fun `intervalle ouvert court jusqu a now`() {
        val now = t0.plusSeconds(4 * 60)
        val used = quota.usedIn(listOf(iv(0, 1, open = true)), now.minus(Duration.ofMinutes(30)), now, now)
        assertEquals(Duration.ofMinutes(4), used)
    }

    @Test fun `sous le plafond n est pas depasse`() {
        val r = quota.evaluate(listOf(w30, w6h), listOf(iv(0, 4)), t0.plusSeconds(4 * 60))
        assertFalse(r.exceeded)
        assertNull(r.unlockAt)
    }

    @Test fun `exactement au plafond est depasse`() {
        val r = quota.evaluate(listOf(w30), listOf(iv(0, 5)), t0.plusSeconds(5 * 60))
        assertTrue(r.exceeded)
    }

    @Test fun `unlockAt est quand l usage repasse sous le plafond`() {
        // 5 min d'usage de 0 à 5. À t=30 min, la fenêtre [0,30] contient encore 5 min → bloqué.
        // À t=30min+1s, la fenêtre [0:01, 30:01] contient 4:59 → libre.
        val r = quota.evaluate(listOf(w30), listOf(iv(0, 5)), t0.plusSeconds(5 * 60))
        assertEquals(t0.plusSeconds(30 * 60 + 1), r.unlockAt)
    }

    @Test fun `unlockAt global est le max des fenetres depassees`() {
        // 30 min d'usage continu : les deux fenêtres sont dépassées.
        val r = quota.evaluate(listOf(w30, w6h), listOf(iv(0, 30)), t0.plusSeconds(30 * 60))
        assertTrue(r.exceeded)
        // Fenêtre 6 h : il faut que 30 min sortent → t = 6h + 1s après t0.
        assertEquals(t0.plusSeconds(6 * 3600 + 1), r.unlockAt)
    }

    @Test fun `perWindow expose la consommation`() {
        val r = quota.evaluate(listOf(w30, w6h), listOf(iv(0, 3)), t0.plusSeconds(3 * 60))
        assertEquals(Duration.ofMinutes(3), r.perWindow[0].used)
        assertEquals(Duration.ofMinutes(3), r.perWindow[1].used)
    }
}
