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
        overlayBottom: Int? = null,
    ) = policy.decide(pkg, decision, target, redirectTab, { bounds }, now, overlayBottom)

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

    @Test fun `cible quittee apres un echec remet a zero une fois le delai de grace passe`() {
        decide(1_000)
        assertEquals(Action.ShowOverlay, decide(2_500))
        assertEquals(Action.None, decide(3_000, target = false))
        assertEquals(Action.Redirect(tab), decide(1_000 + RedirectPolicy.FAILURE_COOLDOWN_MILLIS))
    }

    @Test fun `reset explicite remet a zero apres l intervalle minimal`() {
        decide(1_000)
        policy.resetAll()
        assertEquals(Action.Redirect(tab), decide(1_000 + RedirectPolicy.MIN_INTERVAL_MILLIS))
    }

    @Test fun `nuit overlay sans bascule`() {
        assertEquals(Action.ShowOverlay, decide(1_000, decision = night))
    }

    @Test fun `passage du quota a la nuit remet a zero`() {
        decide(1_000)
        assertEquals(Action.ShowOverlay, decide(1_200, decision = night))
        assertEquals(Action.Redirect(tab), decide(1_000 + RedirectPolicy.MIN_INTERVAL_MILLIS))
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
        assertEquals(Action.Redirect(tab), decide(1_000 + RedirectPolicy.MIN_INTERVAL_MILLIS))
    }

    @Test fun `echec du geste overlay sans attendre`() {
        decide(1_000)
        policy.markFailed(pkg)
        assertEquals(Action.ShowOverlay, decide(1_100))
    }

    @Test fun `etat par package`() {
        decide(1_000)
        decide(2_500)
        val other = policy.decide("com.twitter.android", quota, true, RedirectTab.Messages, { tab }, 2_600, null)
        assertEquals(Action.Redirect(tab), other)
    }

    @Test fun `statut publie pour l ecran debug`() {
        assertNull(policy.lastAttempt)
        decide(1_000)
        assertEquals(RedirectAttempt(pkg, RedirectTab.Messages, 1_000, failed = false), policy.lastAttempt)
        decide(2_500)
        assertEquals(RedirectAttempt(pkg, RedirectTab.Messages, 1_000, failed = true), policy.lastAttempt)
    }

    // ---- Fix round 1 : délai de grâce qui survit aux remises à zéro, overlay plein écran ----

    @Test fun `echec puis tick sans cible puis cible pas de nouveau toucher`() {
        decide(1_000)
        assertEquals(Action.ShowOverlay, decide(2_500))
        // Tick transitoire sans cible (racine nulle, arbre partiel, volet de notifications).
        assertEquals(Action.None, decide(2_800, target = false))
        assertEquals(Action.ShowOverlay, decide(3_000))
        assertEquals(Action.ShowOverlay, decide(3_000 + 30_000))
    }

    @Test fun `echec puis resetAll garde le delai de grace`() {
        decide(1_000)
        decide(2_500)
        policy.resetAll()
        assertEquals(Action.ShowOverlay, decide(3_000))
    }

    @Test fun `apres le delai de grace une nouvelle tentative est permise`() {
        decide(1_000)
        decide(2_500)
        decide(2_800, target = false)
        assertEquals(Action.Redirect(tab), decide(1_000 + RedirectPolicy.FAILURE_COOLDOWN_MILLIS))
    }

    @Test fun `intervalle minimal entre deux tentatives meme reussies`() {
        decide(1_000)
        // Bascule réussie : la cible disparaît, puis l'utilisateur revient aussitôt sur le fil.
        assertEquals(Action.None, decide(1_800, target = false))
        assertEquals(Action.ShowOverlay, decide(2_000))
        assertEquals(Action.Redirect(tab), decide(1_000 + RedirectPolicy.MIN_INTERVAL_MILLIS))
    }

    @Test fun `overlay plein ecran pas de toucher`() {
        assertEquals(Action.ShowOverlay, decide(1_000, overlayBottom = Int.MAX_VALUE))
        assertNull(policy.lastAttempt)
    }

    @Test fun `onglet sous l overlay affiche toucher permis`() {
        // Overlay arrêté au-dessus de la barre (navBarTop = 2148) : le centre de l'onglet (y = 2211) est dessous.
        assertEquals(Action.Redirect(tab), decide(1_000, overlayBottom = 2148))
    }

    @Test fun `onglet couvert par l overlay pas de toucher`() {
        assertEquals(Action.ShowOverlay, decide(1_000, overlayBottom = 2250))
    }

    @Test fun `quitter la cible efface l echec affiche`() {
        decide(1_000)
        decide(2_500)
        decide(2_800, target = false)
        assertNull(policy.lastAttempt)
    }
}
