package rehab.app.overlay

import rehab.app.ui.HomeText
import rehab.app.ui.SettingsText
import rehab.domain.model.BlockReason
import rehab.domain.model.NightWindow
import rehab.domain.model.QuotaWindow
import rehab.domain.policy.PressOutcome
import rehab.domain.policy.StreakSummary
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Tout ce que l'overlay affiche, calculé par le service sur le thread "rehab-engine".
 * [detail] : ligne sous le titre (plage nocturne, fenêtre de quota dépassée ou jokers épuisés).
 */
data class OverlayState(
    val reason: BlockReason,
    val unlockAtMillis: Long,
    val detail: String,
    val streak: StreakSummary,
    val outcome: PressOutcome,
    val holdMillis: Long,
    val jokerMinutes: Long,
    val relapseMinutes: Long,
    val navBarTop: Int?,
    val zone: ZoneId,
)

/** Texte d'annonce au repos : [emphasis] est mis en gras (et en bronze pour un joker). */
data class Caption(val prefix: String, val emphasis: String, val suffix: String)

/** Textes de l'overlay (DESIGN §5), en français. */
object OverlayText {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    /** Statique (DESIGN §6.1) : [shownAtMillis] est l'instant d'apparition de l'overlay, pas « maintenant ». */
    fun title(s: OverlayState, shownAtMillis: Long): String = when (s.reason) {
        BlockReason.Night -> "Nuit · déblocage à " + Instant.ofEpochMilli(s.unlockAtMillis).atZone(s.zone).format(hm)
        BlockReason.Quota -> "Quota atteint · " + HomeText.waitText(Instant.ofEpochMilli(shownAtMillis), Instant.ofEpochMilli(s.unlockAtMillis))
    }

    fun nightDetail(row: DayOfWeek, w: NightWindow) =
        "Plage nocturne du ${SettingsText.dayName(row).lowercase(Locale.FRENCH)} : ${w.bedtime.format(hm)} → ${w.wakeup.format(hm)}"

    fun quotaDetail(w: QuotaWindow): String {
        val m = w.duration.toMinutes()
        val span = when {
            m == 60L -> "sur la dernière heure"
            m % 60 == 0L -> "sur les ${m / 60} dernières heures"
            else -> "sur les $m dernières minutes"
        }
        return "${HomeText.duration(w.cap)} $span, toutes cibles"
    }

    fun jokersExhausted(perDay: Int) = if (perDay == 0) "Aucun joker prévu (0/0)" else "Jokers du jour épuisés ($perDay/$perDay)"

    fun streakLine(s: StreakSummary) =
        if (s.inRecord) "Record en cours depuis " + HomeText.plural(s.current - s.previousBest, "jour")
        else "Sans relapse depuis " + HomeText.plural(s.current, "jour") + " · record ${s.best}"

    fun doneStreakLine(s: StreakSummary) = "Série remise à zéro · record ${maxOf(s.best, s.current)} conservé"

    private fun holdSeconds(s: OverlayState) = s.holdMillis / 1000

    /** « ton streak » quand la série en cours est le record, « ta série » sinon (DESIGN §5.5). */
    private fun loss(s: OverlayState, lost: Int) =
        if (s.streak.inRecord) "ton streak de " + HomeText.plural(lost, "jour") else "ta série de " + HomeText.plural(lost, "jour")

    fun caption(s: OverlayState): Caption = when (val o = s.outcome) {
        is PressOutcome.Joker -> Caption(
            "Maintenir ${holdSeconds(s)} s · ", "Joker +${s.jokerMinutes} min",
            if (o.remainingAfter == 0) " · dernier joker du jour" else " · ${HomeText.plural(o.remainingAfter, "restant")} aujourd’hui",
        )
        is PressOutcome.Relapse -> Caption("Maintenir ${holdSeconds(s)} s · ", "RELAPSE", " · ${loss(s, o.streakLost)} tombe")
    }

    fun holdingCaption(s: OverlayState, secondsLeft: Int): String = when (val o = s.outcome) {
        is PressOutcome.Joker -> "Encore $secondsLeft s · Joker +${s.jokerMinutes} min"
        is PressOutcome.Relapse -> "Encore $secondsLeft s et ${loss(s, o.streakLost)} tombe"
    }

    fun doneCaption(s: OverlayState): String = when (s.outcome) {
        is PressOutcome.Joker -> "Joker activé · déblocage ${s.jokerMinutes} min"
        is PressOutcome.Relapse -> "Relapse enregistré · déblocage ${s.relapseMinutes} min"
    }
}
