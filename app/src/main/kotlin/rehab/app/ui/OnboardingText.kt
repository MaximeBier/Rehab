package rehab.app.ui

import rehab.app.service.AppStatus

/** Textes de l'écran Onboarding (tâche 8), au même style que Réglages/Accueil. */
object OnboardingText {
    /**
     * Valeur + ton de pastille d'une ligne app, format abrégé aux deux premiers composants de
     * version (comme `HomeText.outOfRangeAlert`).
     */
    fun appValue(s: AppStatus): Pair<String, PillTone> {
        if (!s.installed) return "non installée" to PillTone.Muted
        val v = s.version?.split('.', '-')?.take(2)?.joinToString(".") ?: "?"
        return if (s.inRange) "$v · reconnue" to PillTone.Accent else "$v · hors plage" to PillTone.Warn
    }
}
