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
 * toujours à l'écran, la tentative est marquée en échec et l'overlay s'affiche. L'état de la tentative en
 * cours est remis à zéro dès que la cible disparaît ou que la décision n'est plus `Block(Quota)`.
 *
 * Garde-fous contre une boucle de touchers (revue v0.3.0, fix 1) : un seul tick sans cible (racine nulle,
 * arbre partiel, volet de notifications) suffit à remettre l'état à zéro. L'historique des tentatives
 * survit donc à [reset]/[resetAll] : pas de nouvelle tentative moins de [FAILURE_COOLDOWN_MILLIS] après une
 * tentative échouée, ni moins de [MIN_INTERVAL_MILLIS] après n'importe quelle tentative.
 *
 * Pas de toucher sur un overlay (fix 2) : si l'overlay affiché couvre le centre de l'onglet
 * (`overlayBottomPx`, `Int.MAX_VALUE` pour un overlay plein écran), le toucher atterrirait sur l'overlay
 * lui-même (p. ex. sur « Quitter ») : on garde l'overlay.
 *
 * Pas de synchronisation : confinée au thread "rehab-engine", comme `usageTracker`/`degraded`.
 */
class RedirectPolicy(private val attemptTimeoutMillis: Long = 1500) {

    companion object {
        /** Aucune nouvelle tentative pendant ce délai après une tentative échouée, remises à zéro comprises. */
        const val FAILURE_COOLDOWN_MILLIS = 60_000L
        /** Écart minimal entre deux tentatives, même réussies (détection qui oscille pendant une transition). */
        const val MIN_INTERVAL_MILLIS = 5_000L
    }

    sealed interface Action {
        /** Toucher l'onglet de repli au centre de [bounds]. */
        data class Redirect(val bounds: Bounds) : Action
        /** Afficher l'overlay de blocage (nuit, bascule désactivée, onglet introuvable ou couvert, échec, délai de grâce). */
        data object ShowOverlay : Action
        /** Tentative en cours (moins de [attemptTimeoutMillis]) : ni overlay, ni nouveau toucher. */
        data object Wait : Action
        /** Pas de blocage quota sur une cible : rien à faire côté bascule. */
        data object None : Action
    }

    private class State(val tab: RedirectTab, val attemptAtMillis: Long, var failed: Boolean = false)

    /** Tentative en cours par package, remise à zéro quand la cible disparaît. */
    private val states = mutableMapOf<String, State>()
    /** Historique qui survit aux remises à zéro : dernière tentative, dernière tentative échouée. */
    private val lastAttemptAt = mutableMapOf<String, Long>()
    private val lastFailedAttemptAt = mutableMapOf<String, Long>()

    var lastAttempt: RedirectAttempt? = null
        private set

    /**
     * [boundsOf] n'est appelé que lorsqu'une tentative est réellement envisagée (quota, onglet configuré,
     * aucune tentative en cours) : le parcours du snapshot est évité le reste du temps.
     * [overlayBottomPx] : bas de l'overlay demandé en dernier (`null` = aucun overlay, `Int.MAX_VALUE` = plein écran).
     */
    fun decide(
        packageName: String,
        decision: Decision,
        targetPresent: Boolean,
        tab: RedirectTab?,
        boundsOf: (RedirectTab) -> Bounds?,
        nowMillis: Long,
        overlayBottomPx: Int?,
    ): Action {
        if (!targetPresent || decision !is Decision.Block) {
            reset(packageName)
            return Action.None
        }
        if (decision.reason != BlockReason.Quota) {
            reset(packageName)
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
        lastFailedAttemptAt[packageName]?.let { if (nowMillis - it < FAILURE_COOLDOWN_MILLIS) return Action.ShowOverlay }
        lastAttemptAt[packageName]?.let { if (nowMillis - it < MIN_INTERVAL_MILLIS) return Action.ShowOverlay }
        // Onglet absent de l'arbre (barres masquées) : aucun toucher n'a eu lieu, donc pas d'état — on
        // retentera dès que l'onglet réapparaît, sans risque de boucle.
        val bounds = boundsOf(tab) ?: return Action.ShowOverlay
        val centerY = (bounds.top + bounds.bottom) / 2
        if (overlayBottomPx != null && centerY < overlayBottomPx) return Action.ShowOverlay
        states[packageName] = State(tab, nowMillis)
        lastAttemptAt[packageName] = nowMillis
        lastAttempt = RedirectAttempt(packageName, tab, nowMillis, failed = false)
        return Action.Redirect(bounds)
    }

    /** Le geste n'a pas pu être envoyé ou a été annulé : overlay dès le prochain tick, sans attendre le délai. */
    fun markFailed(packageName: String) {
        states[packageName]?.let { fail(packageName, it) }
    }

    /**
     * Oublie la tentative en cours (cible quittée), pas l'historique qui porte les délais de grâce. Efface
     * aussi un « échec → overlay » affiché dans Debug pour ce package, qui ne décrit plus l'écran courant.
     */
    fun reset(packageName: String) {
        states.remove(packageName)
        if (lastAttempt?.let { it.packageName == packageName && it.failed } == true) lastAttempt = null
    }

    fun resetAll() {
        states.keys.toList().forEach(::reset)
        if (lastAttempt?.failed == true) lastAttempt = null
    }

    private fun fail(packageName: String, state: State) {
        state.failed = true
        lastFailedAttemptAt[packageName] = state.attemptAtMillis
        lastAttempt = RedirectAttempt(packageName, state.tab, state.attemptAtMillis, failed = true)
    }
}
