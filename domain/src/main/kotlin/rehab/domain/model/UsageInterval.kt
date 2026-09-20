package rehab.domain.model

import java.time.Instant

/** [end] est la dernière borne connue ; si [open], l'intervalle est encore en cours et [end] vaut le dernier tick persisté. */
data class UsageInterval(
    val id: Long,
    val target: TargetId,
    val start: Instant,
    val end: Instant,
    val open: Boolean,
)
