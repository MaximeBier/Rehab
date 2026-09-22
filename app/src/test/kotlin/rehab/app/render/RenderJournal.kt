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
import rehab.app.ui.JournalContent
import rehab.app.ui.JournalText
import rehab.domain.model.BlockReason
import rehab.domain.model.Event
import rehab.domain.model.TargetId
import rehab.domain.model.UsageInterval
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Rendu de `JournalContent` (tâche 5), en PNG (app/build/renders) pour relecture visuelle contre
 * docs/design/screens/Journal.png. Aucune assertion sur les pixels ; ignoré sauf
 * -Prehab.render=true. Les journées viennent du vrai pipeline `JournalText.rows`/`days` (jamais
 * construites à la main) : ce test vérifie le rendu du pipeline réel, pas une reconstitution
 * manuelle du visuel de la maquette.
 *
 * Deux écarts volontaires avec la maquette, documentés dans le rapport de tâche 5 :
 *  - le blocage quota (`Event.Block`) écrit toujours une ligne de début en plus de la ligne de fin
 *    une fois passé (règle testée dans `JournalTextTest`) ; la maquette n'affiche que la fin. Le
 *    début est placé ici à 08:00 pour apparaître en dernière ligne d'Aujourd'hui, sans perturber
 *    l'ordre des 5 autres lignes.
 *  - le groupe Hier trie `service réactivé` (00:05, nuit du 20) avant `service désactivé` (23:58,
 *    19) : plus récent d'abord en temps réel, alors que la maquette les affiche dans l'autre ordre.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class RenderJournal {
    @get:Rule val compose = createComposeRule()

    @Before fun gate() = assumeRendering()

    private val zone = ZoneId.of("Europe/Paris")
    private fun at(d: Int, h: Int, m: Int, s: Int = 0) = ZonedDateTime.of(2026, 9, d, h, m, s, 0, zone).toInstant()
    private val dayOf: (Instant) -> LocalDate = { it.atZone(zone).minusHours(7).minusMinutes(30).toLocalDate() }
    private val now = at(20, 23, 0)

    @Test fun journal() = compose.renderPng("journal") {
        val events = listOf(
            Event.Block(at(20, 8, 0), BlockReason.Quota, at(20, 22, 18)),
            Event.Relapse(at(20, 21, 47), at(20, 22, 2)),
            Event.Joker(at(20, 20, 10), at(20, 20, 15)),
            Event.ServiceOff(at(19, 23, 58)),
            Event.ServiceOn(at(20, 0, 5)),
            Event.RulesOutOfRange(at(19, 19, 40), "com.instagram.android", "412.0.0.35.104"),
        )
        val usage = listOf(
            UsageInterval(1, TargetId("InstagramReels"), at(20, 21, 32), at(20, 21, 36, 12), open = false),
            UsageInterval(2, TargetId("TwitterHome"), at(20, 18, 2), at(20, 18, 4, 5), open = false),
            UsageInterval(3, TargetId("InstagramSuggested"), at(19, 12, 11), at(19, 12, 12, 48), open = false),
        )
        val rows = JournalText.rows(events, usage, jokersPerDay = 2, dayOf = dayOf, now = now, zone = zone)
        val days = JournalText.days(rows, dayOf, today = LocalDate.of(2026, 9, 20))
        Box(Modifier.size(390.dp, 844.dp)) { JournalContent(days) }
    }
}
