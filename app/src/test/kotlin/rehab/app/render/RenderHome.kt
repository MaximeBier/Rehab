package rehab.app.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rehab.app.ui.AlertAction
import rehab.app.ui.HomeScreen
import rehab.app.ui.HomeText
import rehab.app.ui.HomeUiState
import rehab.domain.model.BlockReason
import rehab.domain.model.Decision
import rehab.domain.model.QuotaWindow
import rehab.domain.policy.Schedule
import rehab.domain.policy.SlidingQuota
import rehab.domain.policy.StreakSummary
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Rendus de `HomeScreen` (tâche 3), en PNG (app/build/renders) pour relecture visuelle contre
 * docs/design/screens/Main.png, Accueil-Bloque.png, Accueil-Alertes.png. Aucune assertion sur les
 * pixels ; ignoré sauf -Prehab.render=true. La barre de navigation est dessinée par `RehabApp`, pas
 * par `HomeScreen` : elle n'apparaît donc pas dans ces rendus.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class RenderHome {
    @get:Rule val compose = createComposeRule()

    @Before fun gate() = assumeRendering()

    private val zone = ZoneId.of("Europe/Paris")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 9, 20, h, m, 0, 0, zone).toInstant()

    private val w30 = QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(5))
    private val w6h = QuotaWindow(Duration.ofHours(6), Duration.ofMinutes(30))

    @Test fun `home libre record`() = compose.renderPng("home-libre-record") {
        val now = at(12, 0)
        val night = Schedule.NightPeriod(now.atZone(zone).toLocalDate(), at(23, 0), at(23, 0).plus(Duration.ofMinutes(510)))
        val state = HomeUiState(
            loaded = true,
            pill = HomeText.pill(true, Decision.Allow, null, zone),
            alerts = emptyList(),
            streak = StreakSummary(24, 24, 23),
            statusLine = HomeText.statusLine(Decision.Allow, null, now, zone),
            jokersLeft = 2,
            jokersPerDay = 2,
            gauges = listOf(
                HomeText.gauge(SlidingQuota.WindowUsage(w30, Duration.ofMinutes(3))),
                HomeText.gauge(SlidingQuota.WindowUsage(w6h, Duration.ofMinutes(12))),
            ),
            night = HomeText.night(night, now, zone),
            serviceConnected = true,
        )
        Box(Modifier.size(390.dp, 844.dp)) { HomeScreen(state) {} }
    }

    @Test fun `home bloque quota`() = compose.renderPng("home-bloque-quota") {
        val now = at(22, 0)
        val unlockAt = at(22, 18)
        val night = Schedule.NightPeriod(now.atZone(zone).toLocalDate(), at(23, 0), at(23, 0).plus(Duration.ofMinutes(510)))
        val decision = Decision.Block(BlockReason.Quota, unlockAt)
        val state = HomeUiState(
            loaded = true,
            pill = HomeText.pill(true, decision, null, zone),
            alerts = emptyList(),
            streak = StreakSummary(24, 24, 23),
            statusLine = HomeText.statusLine(decision, null, now, zone),
            jokersLeft = 1,
            jokersPerDay = 2,
            gauges = listOf(
                HomeText.gauge(SlidingQuota.WindowUsage(w30, Duration.ofMinutes(5))),
                HomeText.gauge(SlidingQuota.WindowUsage(w6h, Duration.ofMinutes(22))),
            ),
            night = HomeText.night(night, now, zone),
            serviceConnected = true,
        )
        Box(Modifier.size(390.dp, 844.dp)) { HomeScreen(state) {} }
    }

    @Test fun `home alertes hors record`() = compose.renderPng("home-alertes-hors-record") {
        val now = at(12, 0)
        val night = Schedule.NightPeriod(now.atZone(zone).toLocalDate(), at(23, 0), at(23, 0).plus(Duration.ofMinutes(510)))
        val state = HomeUiState(
            loaded = true,
            pill = HomeText.pill(false, Decision.Allow, null, zone),
            alerts = listOf(
                HomeText.serviceAlert(),
                HomeText.outOfRangeAlert("com.instagram.android", "412.0.0.35.104"),
            ),
            streak = StreakSummary(12, 23, 23),
            statusLine = HomeText.statusLine(Decision.Allow, null, now, zone),
            jokersLeft = 2,
            jokersPerDay = 2,
            gauges = listOf(
                HomeText.gauge(SlidingQuota.WindowUsage(w30, Duration.ZERO)),
                HomeText.gauge(SlidingQuota.WindowUsage(w6h, Duration.ofMinutes(4))),
            ),
            night = HomeText.night(night, now, zone),
            serviceConnected = false,
        )
        Box(Modifier.size(390.dp, 844.dp)) { HomeScreen(state) { _: AlertAction -> } }
    }
}
