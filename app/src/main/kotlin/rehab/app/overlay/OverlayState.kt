package rehab.app.overlay

import rehab.domain.model.BlockReason
import rehab.domain.policy.PressOutcome
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class OverlayState(
    val reason: BlockReason,
    val unlockAtMillis: Long,
    val streak: Int,
    val best: Int,
    val outcome: PressOutcome,
    val holdMillis: Long,
    val navBarTop: Int?,
    val zone: ZoneId,
    val jokerMinutes: Long = 5,
)

object OverlayText {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    fun reason(state: OverlayState, nowMillis: Long): String = when (state.reason) {
        BlockReason.Night -> "Nuit · déblocage à " + Instant.ofEpochMilli(state.unlockAtMillis).atZone(state.zone).format(hm)
        BlockReason.Quota -> {
            val remaining = maxOf(0L, state.unlockAtMillis - nowMillis) / 1000
            "Quota atteint · %d min %02d s".format(remaining / 60, remaining % 60)
        }
    }

    fun button(outcome: PressOutcome, holdSeconds: Long, jokerMinutes: Long): String = when (outcome) {
        is PressOutcome.Joker -> {
            val rest = if (outcome.remainingAfter == 0) "dernier" else "${outcome.remainingAfter} restant" + if (outcome.remainingAfter > 1) "s" else ""
            "Maintenir $holdSeconds s · Joker +$jokerMinutes min · $rest"
        }
        is PressOutcome.Relapse -> "Maintenir $holdSeconds s · RELAPSE · ton streak de ${outcome.streakLost} jours tombe"
    }

    fun streak(state: OverlayState): String = "${state.streak} jours · record ${state.best}"
}
