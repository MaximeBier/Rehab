package rehab.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rehab.app.service.RedirectPolicy.Action
import rehab.domain.model.BlockReason
import rehab.domain.model.Decision
import rehab.rules.Bounds
import rehab.rules.RedirectTab
import java.time.Instant

class RedirectPolicyTest {
    private val pkg = "com.instagram.android"
    private val tab = Bounds(432, 2148, 648, 2274)
    private val quota = Decision.Block(BlockReason.Quota, Instant.parse("2026-09-23T20:00:00Z"))
    private val night = Decision.Block(BlockReason.Night, Instant.parse("2026-09-24T05:30:00Z"))
    private val policy = RedirectPolicy()

    private fun decide(
        now: Long,
        decision: Decision = quota,
        target: Boolean = true,
        redirectTab: RedirectTab? = RedirectTab.Messages,
        bounds: Bounds? = tab,
    ) = policy.decide(pkg, decision, target, redirectTab, { bounds }, now)

    @Test fun `premiere tentative touche l onglet`() {
        assertEquals(Action.Redirect(tab), decide(1_000))
    }

    @Test fun `tentative en cours attend`() {
        decide(1_000)
        assertEquals(Action.Wait, decide(1_300))
        assertEquals(Action.Wait, decide(2_499))
    }

    @Test fun `cible toujours la apres 1,5 s overlay et plus de tentative`() {
        decide(1_000)
        assertEquals(Action.ShowOverlay, decide(2_500))
        assertEquals(Action.ShowOverlay, decide(3_500))
        assertEquals(Action.ShowOverlay, decide(60_000))
    }

    @Test fun `cible quittee remet a zero`() {
        decide(1_000)
        assertEquals(Action.ShowOverlay, decide(2_500))
        assertEquals(Action.None, decide(3_000, target = false))
        assertEquals(Action.Redirect(tab), decide(4_000))
    }

    @Test fun `reset explicite remet a zero`() {
        decide(1_000)
        decide(2_500)
        policy.resetAll()
        assertEquals(Action.Redirect(tab), decide(3_000))
    }

    @Test fun `nuit overlay sans bascule`() {
        assertEquals(Action.ShowOverlay, decide(1_000, decision = night))
    }

    @Test fun `passage du quota a la nuit remet a zero`() {
        decide(1_000)
        assertEquals(Action.ShowOverlay, decide(1_200, decision = night))
        assertEquals(Action.Redirect(tab), decide(1_400))
    }

    @Test fun `onglet desactive overlay`() {
        assertEquals(Action.ShowOverlay, decide(1_000, redirectTab = null))
    }

    @Test fun `bounds absents overlay puis tentative quand l onglet reapparait`() {
        assertEquals(Action.ShowOverlay, decide(1_000, bounds = null))
        // Aucun toucher n'a eu lieu : pas de risque de boucle, on tente dès que la barre revient.
        assertEquals(Action.Redirect(tab), decide(2_000))
    }

    @Test fun `joker actif ne fait rien et remet a zero`() {
        decide(1_000)
        assertEquals(Action.None, decide(1_200, decision = Decision.Allow))
        assertEquals(Action.Redirect(tab), decide(1_400))
    }

    @Test fun `echec du geste overlay sans attendre`() {
        decide(1_000)
        policy.markFailed(pkg)
        assertEquals(Action.ShowOverlay, decide(1_100))
    }

    @Test fun `etat par package`() {
        decide(1_000)
        decide(2_500)
        val other = policy.decide("com.twitter.android", quota, true, RedirectTab.Messages, { tab }, 2_600)
        assertEquals(Action.Redirect(tab), other)
    }

    @Test fun `statut publie pour l ecran debug`() {
        assertNull(policy.lastAttempt)
        decide(1_000)
        assertEquals(RedirectAttempt(pkg, RedirectTab.Messages, 1_000, failed = false), policy.lastAttempt)
        decide(2_500)
        assertEquals(RedirectAttempt(pkg, RedirectTab.Messages, 1_000, failed = true), policy.lastAttempt)
    }
}
