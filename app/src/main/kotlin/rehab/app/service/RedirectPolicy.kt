package rehab.app.service

import rehab.domain.model.BlockReason
import rehab.domain.model.Decision
import rehab.rules.Bounds
import rehab.rules.RedirectTab

/** Dernière tentative de bascule, publiée pour l'écran Debug (ligne « Bascule »). */
data class RedirectAttempt(val packageName: String, val tab: RedirectTab, val atMillis: Long, val failed: Boolean)

/**
 * Bascule automatique au blocage quota (v0.3.0, spec `2026-09-23-v0.3-bascule-dm-design.md`) : au lieu
 * d'afficher l'overlay, Rehab touche l'onglet de repli de l'app (Messages par défaut). Kotlin pur, sans
 * API Android, pour être testable sans Robolectric (voir `RedirectPolicyTest`).
 *
 * Une seule tentative par passage sur la cible : si, [attemptTimeoutMillis] après le toucher, la cible est
 * toujours à l'écran, la tentative est marquée en échec et l'overlay s'affiche jusqu'à ce que l'utilisateur
 * quitte la cible — sans quoi un onglet qui ne réagit pas provoquerait une boucle de touchers. L'état est
 * remis à zéro dès que la cible disparaît ou que la décision n'est plus `Block(Quota)`.
 *
 * Pas de synchronisation : confinée au thread "rehab-engine", comme `usageTracker`/`degraded`.
 */
class RedirectPolicy(private val attemptTimeoutMillis: Long = 1500) {

    sealed interface Action {
        /** Toucher l'onglet de repli au centre de [bounds]. */
        data class Redirect(val bounds: Bounds) : Action
        /** Afficher l'overlay de blocage (nuit, bascule désactivée, onglet introuvable ou échec). */
        data object ShowOverlay : Action
        /** Tentative en cours (moins de [attemptTimeoutMillis]) : ni overlay, ni nouveau toucher. */
        data object Wait : Action
        /** Pas de blocage quota sur une cible : rien à faire côté bascule. */
        data object None : Action
    }

    private class State(val tab: RedirectTab, val attemptAtMillis: Long, var failed: Boolean = false)

    private val states = mutableMapOf<String, State>()

    var lastAttempt: RedirectAttempt? = null
        private set

    /**
     * [boundsOf] n'est appelé que lorsqu'une tentative est réellement envisagée (quota, onglet configuré,
     * aucune tentative en cours) : le parcours du snapshot est évité le reste du temps.
     */
    fun decide(
        packageName: String,
        decision: Decision,
        targetPresent: Boolean,
        tab: RedirectTab?,
        boundsOf: (RedirectTab) -> Bounds?,
        nowMillis: Long,
    ): Action {
        if (!targetPresent || decision !is Decision.Block) {
            states.remove(packageName)
            return Action.None
        }
        if (decision.reason != BlockReason.Quota) {
            states.remove(packageName)
            return Action.ShowOverlay
        }
        val state = states[packageName]
        if (state != null) {
            if (state.failed) return Action.ShowOverlay
            if (nowMillis - state.attemptAtMillis < attemptTimeoutMillis) return Action.Wait
            fail(packageName, state)
            return Action.ShowOverlay
        }
        if (tab == null) return Action.ShowOverlay
        // Onglet absent de l'arbre (barres masquées) : aucun toucher n'a eu lieu, donc pas d'état — on
        // retentera dès que l'onglet réapparaît, sans risque de boucle.
        val bounds = boundsOf(tab) ?: return Action.ShowOverlay
        states[packageName] = State(tab, nowMillis)
        lastAttempt = RedirectAttempt(packageName, tab, nowMillis, failed = false)
        return Action.Redirect(bounds)
    }

    /** Le geste n'a pas pu être envoyé ou a été annulé : overlay dès le prochain tick, sans attendre le délai. */
    fun markFailed(packageName: String) {
        states[packageName]?.let { fail(packageName, it) }
    }

    fun reset(packageName: String) { states.remove(packageName) }

    fun resetAll() { states.clear() }

    private fun fail(packageName: String, state: State) {
        state.failed = true
        lastAttempt = RedirectAttempt(packageName, state.tab, state.attemptAtMillis, failed = true)
    }
}
