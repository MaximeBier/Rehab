package rehab.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import rehab.domain.model.NightWindow
import rehab.domain.model.QuotaWindow
import rehab.domain.model.Settings
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

@Serializable
private data class NightDto(val bedtime: String, val wakeup: String)

@Serializable
private data class QuotaDto(val durationMinutes: Long, val capMinutes: Long)

@Serializable
private data class SettingsDto(
    val nights: Map<String, NightDto>,
    val quotaWindows: List<QuotaDto>,
    val jokerMinutes: Long,
    val relapseMinutes: Long,
    val jokersPerDay: Int,
    val holdSeconds: Long,
)

object SettingsCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(s: Settings): String = json.encodeToString(
        SettingsDto.serializer(),
        SettingsDto(
            nights = s.nights.entries.associate { (d, w) -> d.name to NightDto(w.bedtime.toString(), w.wakeup.toString()) },
            quotaWindows = s.quotaWindows.map { QuotaDto(it.duration.toMinutes(), it.cap.toMinutes()) },
            jokerMinutes = s.jokerDuration.toMinutes(),
            relapseMinutes = s.relapseDuration.toMinutes(),
            jokersPerDay = s.jokersPerDay,
            holdSeconds = s.holdDuration.seconds,
        ),
    )

    fun decode(text: String): Settings {
        val dto = json.decodeFromString(SettingsDto.serializer(), text)
        return Settings(
            nights = dto.nights.entries.associate { (d, w) -> DayOfWeek.valueOf(d) to NightWindow(LocalTime.parse(w.bedtime), LocalTime.parse(w.wakeup)) },
            quotaWindows = dto.quotaWindows.map { QuotaWindow(Duration.ofMinutes(it.durationMinutes), Duration.ofMinutes(it.capMinutes)) },
            jokerDuration = Duration.ofMinutes(dto.jokerMinutes),
            relapseDuration = Duration.ofMinutes(dto.relapseMinutes),
            jokersPerDay = dto.jokersPerDay,
            holdDuration = Duration.ofSeconds(dto.holdSeconds),
        )
    }

    fun decodeOrDefault(text: String?): Settings =
        text?.let { runCatching { decode(it) }.getOrNull() } ?: Settings.DEFAULT
}
