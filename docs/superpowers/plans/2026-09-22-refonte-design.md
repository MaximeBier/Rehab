# Refonte visuelle V1 — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** appliquer à l'app Rehab le design de `docs/design/` (thème bronze mat, quatre onglets, overlay avec appui long animé, onboarding) et ajouter les fonctionnalités que les maquettes montrent mais que l'app n'a pas.

**Architecture:** trois petits ajouts de domaine (événement de blocage, synthèse du record, prochaine nuit, déblocage actif typé), puis un thème et une bibliothèque de composants Compose sans état, puis chaque écran réécrit sur ces composants avec ses textes dans un objet pur testé en JVM. Toute lecture Room reste dans `RehabViewModel` sur `Dispatchers.IO`.

**Tech Stack:** Kotlin 2.4, Jetpack Compose (BOM 2026.09.00, Material 3), Room 2.8, JUnit4 + Robolectric (app), kotlin.test (domain).

**Spec:** `docs/superpowers/specs/2026-09-22-refonte-design-design.md` — à lire avec `docs/design/DESIGN.md`. Les maquettes `docs/design/mockups/<Écran>.dc.html` donnent les valeurs exactes (px → dp, px de police → sp).

## Global Constraints

- Commits : `type: description` en français, **aucun trailer `Co-Authored-By`** (CLAUDE.md). Travail direct sur `main`.
- Modules `domain` et `rules` : Kotlin JVM pur, aucun import `android.*`.
- Shell non-login, avant Gradle : `export JAVA_HOME="$LOCALAPPDATA/Programs/jdk17"; export PATH="$JAVA_HOME/bin:$LOCALAPPDATA/Android/Sdk/platform-tools:$PATH"`.
- Tests : `./gradlew :domain:test`, `./gradlew :app:testDebugUnitTest`, tout : `./gradlew test`. Tous verts à la fin de chaque tâche.
- Aucun Composable n'accède à `graph` ni à Room ; tout I/O passe par `RehabViewModel` (`withContext(Dispatchers.IO)`). Les objets moteur (`usageTracker`, `degraded`, `versionChecker.checkAll()`) restent confinés au thread `rehab-engine` (voir le commentaire de tête de `RehabViewModel`).
- Textes : français correct, accents, apostrophe typographique `’` là où la maquette l'utilise (« Durée d’un joker », « aujourd’hui »). Accord : `HomeText.plural(n, "jour")` (0 et 1 au singulier).
- Tokens (hex exacts) : bg `#171513`, panel `#24211D`, text `#EDE7DB`, muted `#A0978A`, line `#33302B`, accent `#B98457`, accent-hover `#CD9A6C`, danger `#E4614F`, danger-deep `#6B211B`, danger-track `#9A3A30`, danger-text `#F6E4E0`, warn `#E2B45A`, warn-bg `#2A2418`, ring-off `#4A443C`.
- Polices : Chivo (interface) et Chivo Mono (temps, durées, nombres). Étiquette RECORD/SÉRIE à **11 sp** partout (DESIGN §7).
- Pas de retour haptique. Pas de mode clair.
- Après chaque tâche revue : `./gradlew :app:installDebug` sur le Pixel (USB `29081JEGR09520` ou `adb connect 192.168.1.10:5555`), push après chaque lot revu.

## Review Focus

1. **Grandes polices système / textes longs** (réglage Android « taille de police » à 130 %) : les lignes de réglage, bandeaux et lignes de Journal doivent passer à la ligne sans rien tronquer ni chevaucher ; la barre de navigation reste lisible. → Vérification manuelle sur le Pixel dans les tâches 2 à 8 (étape « Vérifier sur le téléphone »).
2. **Valeurs limites des réglages** : zéro fenêtre de quota, `jokersPerDay = 0`, record à 0 (division par zéro de l'arc, « JOKERS 0/0 », overlay directement en relapse). → Tests dans les tâches 3, 4 et 7.
3. **Cycle de vie de l'overlay pendant l'état « terminé »** : masquage demandé pendant les 1,5 s, nouvel état reçu pendant le gel, double complétion. → Tests `OverlayControllerTest` (tâche 7).
4. **Frontières de journée Rehab** : un événement à 01:00 appartient à la journée précédente (en-tête « HIER »), pas au jour civil. → Test `JournalTextTest` (tâche 5).
5. **Refus de `SettingsGuard` pendant l'édition** (un verrou s'active pendant qu'un dialogue est ouvert) : le dialogue reste ouvert et affiche la raison. → Test `SettingsTextTest.rejection` (tâche 4) + chemin de sauvegarde commun.

---

## Task 1: Domaine — blocage journalisé, synthèse du record, prochaine nuit, déblocage typé

**Files:**
- Modify: `domain/src/main/kotlin/rehab/domain/model/Event.kt`
- Modify: `domain/src/main/kotlin/rehab/domain/policy/Streak.kt`
- Modify: `domain/src/main/kotlin/rehab/domain/policy/Schedule.kt`
- Modify: `domain/src/main/kotlin/rehab/domain/policy/UnlockPolicy.kt`
- Modify: `app/src/main/kotlin/rehab/app/data/RoomEventLog.kt`
- Modify: `app/src/main/kotlin/rehab/app/ui/JournalText.kt` (branche `Event.Block` minimale pour que le `when` reste exhaustif ; réécrit en tâche 5)
- Test: `domain/src/test/kotlin/rehab/domain/policy/StreakTest.kt`, `ScheduleTest.kt`, `UnlockPolicyTest.kt`, `app/src/test/kotlin/rehab/app/data/RoomAdaptersTest.kt`

**Interfaces:**
- Produces:
  - `Event.Block(at: Instant, reason: BlockReason, until: Instant)`
  - `data class StreakSummary(val current: Int, val best: Int, val previousBest: Int) { val inRecord: Boolean }` (package `rehab.domain.policy`)
  - `Streak.summary(now: Instant): StreakSummary` (persiste le record comme `recordAndGetBest`)
  - `Schedule.nextNight(now: Instant): Schedule.NightPeriod?` (nuit active ou prochaine)
  - `data class ActiveUnlock(val kind: ActiveUnlock.Kind, val until: Instant) { enum class Kind { Joker, Relapse } }` (package `rehab.domain.policy`)
  - `UnlockPolicy.activeUnlock(now: Instant): ActiveUnlock?` ; `activeUnlockUntil(now)` devient `activeUnlock(now)?.until`

- [ ] **Step 1: Tests de `Streak.summary`** — ajouter à `StreakTest.kt` (mêmes helpers `at`, `streak` que le fichier) :

```kotlin
    @Test fun `summary sans relapse jour d installation est un record naissant`() {
        val (s, record) = streak(at(21, 12))
        val sum = s.summary(at(21, 18))
        assertEquals(StreakSummary(current = 1, best = 1, previousBest = 0), sum)
        assertEquals(true, sum.inRecord)
        assertEquals(1, record.bestDays())
    }

    @Test fun `summary record en cours depasse l ancienne serie`() {
        // Installé le 1er ; relapse le 5 (série passée : 1..4 = 4 jours) ; propre du 6 au 21 = 16 jours.
        val (s, _) = streak(at(1, 12), listOf(Event.Relapse(at(5, 15), at(5, 15, 15))))
        val sum = s.summary(at(21, 18))
        assertEquals(16, sum.current)
        assertEquals(4, sum.previousBest)
        assertEquals(16, sum.best)
        assertEquals(true, sum.inRecord)
    }

    @Test fun `summary hors record quand une serie passee est plus longue`() {
        // Propre du 1 au 15 (15 jours), relapse le 16, propre du 17 au 21 (5 jours).
        val (s, _) = streak(at(1, 12), listOf(Event.Relapse(at(16, 15), at(16, 15, 15))))
        val sum = s.summary(at(21, 18))
        assertEquals(5, sum.current)
        assertEquals(15, sum.previousBest)
        assertEquals(15, sum.best)
        assertEquals(false, sum.inRecord)
    }

    @Test fun `summary egalite avec l ancienne serie n est pas un record`() {
        // Propre du 1 au 5 (5 jours), relapse le 6, propre du 7 au 11 (5 jours).
        val (s, _) = streak(at(1, 12), listOf(Event.Relapse(at(6, 15), at(6, 15, 15))))
        assertEquals(false, s.summary(at(11, 18)).inRecord)
    }

    @Test fun `summary relapse aujourd hui serie a zero`() {
        val (s, _) = streak(at(15, 12), listOf(Event.Relapse(at(21, 15), at(21, 15, 15))))
        val sum = s.summary(at(21, 18))
        assertEquals(0, sum.current)
        assertEquals(6, sum.previousBest)
        assertEquals(false, sum.inRecord)
    }

    @Test fun `summary respecte un record persiste plus grand`() {
        val (s, record) = streak(at(19, 12))
        record.setBestDays(40)
        val sum = s.summary(at(21, 18))
        assertEquals(3, sum.current)
        assertEquals(40, sum.best)
        assertEquals(false, sum.inRecord)
    }
```

- [ ] **Step 2: Lancer** `./gradlew :domain:test --tests '*StreakTest*'` — attendu : échec de compilation (`summary`, `StreakSummary` inconnus).

- [ ] **Step 3: Implémenter** dans `Streak.kt` :

```kotlin
/**
 * [previousBest] : plus longue série passée **terminée** par un relapse. [best] : record persisté
 * (≥ [current] après appel). « Record en cours » = la série actuelle dépasse strictement toutes les
 * séries passées et égale le record persisté ; une égalité avec une série passée n'est pas un record.
 */
data class StreakSummary(val current: Int, val best: Int, val previousBest: Int) {
    val inRecord: Boolean get() = current > 0 && current > previousBest && current >= best
}
```

et dans la classe `Streak` (garder `current` et `recordAndGetBest` ; factoriser l'écriture du record) :

```kotlin
    fun summary(now: Instant): StreakSummary {
        val relapseDays = events.all().filterIsInstance<Event.Relapse>().map { schedule.dayOf(it.at) }.toSet()
        val today = schedule.dayOf(now)
        var day = schedule.dayOf(record.installedAt())
        var run = 0
        var previousBest = 0
        while (day <= today) {
            if (day in relapseDays) {
                previousBest = maxOf(previousBest, run)
                run = 0
            } else {
                run++
            }
            day = day.plusDays(1)
        }
        return StreakSummary(current = run, best = persistBest(run), previousBest = previousBest)
    }

    fun recordAndGetBest(now: Instant): Int = persistBest(current(now))

    /** Voir l'ancien commentaire de `recordAndGetBest` : lecture-modification-écriture sérialisée entre le thread moteur et `Dispatchers.IO`. */
    @Synchronized
    private fun persistBest(c: Int): Int {
        val b = record.bestDays()
        if (c > b) {
            record.setBestDays(c)
            return c
        }
        return b
    }
```

(Déplacer le KDoc existant de `recordAndGetBest` sur `persistBest`.)

- [ ] **Step 4: Tests de `Schedule.nextNight`** — ajouter à `ScheduleTest.kt` (2026-09-21 est un lundi, `weekNights` = 23:00 → 07:30) :

```kotlin
    @Test fun `nextNight a midi renvoie la nuit du soir meme`() {
        val n = schedule.nextNight(at(2026, 9, 21, 12, 0))!!
        assertEquals(at(2026, 9, 21, 23, 0), n.start)
        assertEquals(at(2026, 9, 22, 7, 30), n.end)
    }

    @Test fun `nextNight pendant la nuit renvoie la nuit en cours`() {
        val n = schedule.nextNight(at(2026, 9, 22, 3, 0))!!
        assertEquals(at(2026, 9, 21, 23, 0), n.start)
    }

    @Test fun `nextNight a 7h30 pile passe a la nuit suivante`() {
        val n = schedule.nextNight(at(2026, 9, 22, 7, 30))!!
        assertEquals(at(2026, 9, 22, 23, 0), n.start)
    }

    @Test fun `nextNight saute les nuits desactivees`() {
        val nights = weekNights + (DayOfWeek.MONDAY to NightWindow(LocalTime.of(0, 0), LocalTime.of(0, 0)))
        val n = Schedule({ nights }, zone).nextNight(at(2026, 9, 21, 12, 0))!!
        assertEquals(at(2026, 9, 22, 23, 0), n.start)
    }

    @Test fun `nextNight sans aucune nuit renvoie null`() {
        val none = DayOfWeek.entries.associateWith { NightWindow(LocalTime.of(0, 0), LocalTime.of(0, 0)) }
        assertNull(Schedule({ none }, zone).nextNight(at(2026, 9, 21, 12, 0)))
    }
```

- [ ] **Step 5: Implémenter** dans `Schedule` :

```kotlin
    /** Nuit active à [now], sinon la prochaine (au plus 7 jours devant). Null si toutes les nuits sont désactivées. */
    fun nextNight(now: Instant): NightPeriod? {
        val today = now.atZone(zone).toLocalDate()
        return (-1L..7L).asSequence()
            .mapNotNull { periodForRow(today.plusDays(it)) }
            .firstOrNull { it.end > now }
    }
```

- [ ] **Step 6: Tests de `UnlockPolicy.activeUnlock`** — ajouter à `UnlockPolicyTest.kt` (réutiliser la fabrique du fichier ; si elle n'expose pas le log d'événements, ajouter un `InMemoryEventLog` passé en paramètre) : un joker à `t` de 5 min → `activeUnlock(t + 1 min) == ActiveUnlock(Kind.Joker, t + 5 min)` ; un relapse à `t` de 15 min → `Kind.Relapse` ; à `t + 20 min` → `null` ; joker et relapse superposés → celui dont `until` est le plus tardif.

- [ ] **Step 7: Implémenter** dans `UnlockPolicy.kt` :

```kotlin
data class ActiveUnlock(val kind: Kind, val until: Instant) {
    enum class Kind { Joker, Relapse }
}
```

```kotlin
    fun activeUnlock(now: Instant): ActiveUnlock? {
        val s = settings.get()
        val horizon = now.minus(maxOf(s.jokerDuration, s.relapseDuration))
        return events.since(horizon)
            .mapNotNull {
                when (it) {
                    is Event.Joker -> ActiveUnlock(ActiveUnlock.Kind.Joker, it.unlockUntil)
                    is Event.Relapse -> ActiveUnlock(ActiveUnlock.Kind.Relapse, it.unlockUntil)
                    else -> null
                }
            }
            .filter { it.until > now }
            .maxByOrNull { it.until }
    }

    fun activeUnlockUntil(now: Instant): Instant? = activeUnlock(now)?.until
```

- [ ] **Step 8: `Event.Block`** — dans `Event.kt` (importer `BlockReason` du même package) :

```kotlin
    /** Un blocage (nuit ou quota) vu par l'utilisateur : écrit une fois, à la première apparition de l'overlay pour ce blocage. */
    data class Block(override val at: Instant, val reason: BlockReason, val until: Instant) : Event
```

Vérifier par `grep -rn "is Event.Error" --include=*.kt` qu'aucun autre `when` exhaustif sur `Event` n'échoue ; ajouter les branches manquantes. Dans `JournalText.line(event)` ajouter provisoirement `is Event.Block -> "Blocage ${if (event.reason == BlockReason.Night) "nuit" else "quota"}"`.

- [ ] **Step 9: Persistance** — test dans `RoomAdaptersTest.kt` :

```kotlin
    @Test fun eventLogRoundTripsBlock() {
        val log = RoomEventLog(db.events())
        val e = Event.Block(t0, BlockReason.Quota, t0.plusSeconds(1080))
        log.append(e)
        log.append(Event.Block(t0.plusSeconds(10), BlockReason.Night, t0.plusSeconds(3600)))
        assertEquals(listOf(e, Event.Block(t0.plusSeconds(10), BlockReason.Night, t0.plusSeconds(3600))), log.all())
    }
```

Implémenter dans `RoomEventLog` (pas de migration : colonnes existantes) :

```kotlin
        is Event.Block -> EventEntity(type = "BLOCK", atUtc = at.toEpochMilli(), unlockUntilUtc = until.toEpochMilli(), message = reason.name)
```

```kotlin
            "BLOCK" -> Event.Block(
                at,
                BlockReason.entries.firstOrNull { it.name == message } ?: return null,
                Instant.ofEpochMilli(unlockUntilUtc ?: return null),
            )
```

- [ ] **Step 10: Lancer** `./gradlew test` — attendu : tout vert.

- [ ] **Step 11: Commit**

```bash
git add domain app/src/main/kotlin/rehab/app/data/RoomEventLog.kt app/src/main/kotlin/rehab/app/ui/JournalText.kt app/src/test
git commit -m "feat(domain): journalise les blocages, synthèse du record, prochaine nuit et déblocage typé"
```

---

## Task 2: Thème, polices, composants et barre de navigation

**Files:**
- Create: `app/src/main/res/font/chivo.ttf`, `app/src/main/res/font/chivo_mono.ttf` — copier depuis `C:\Users\Maxime\AppData\Local\Temp\claude\C--Users-Maxime-Documents-Dev-Rehab\4030b4a6-7d80-475c-98f7-e9250256185f\scratchpad\fonts\` (fichiers variables Google Fonts). S'ils manquent : `curl -sSL -o chivo.ttf "https://github.com/google/fonts/raw/main/ofl/chivo/Chivo%5Bwght%5D.ttf"` et `curl -sSL -o chivo_mono.ttf "https://github.com/google/fonts/raw/main/ofl/chivomono/ChivoMono%5Bwght%5D.ttf"`.
- Create: `docs/design/fonts/OFL.txt` (même dossier, ou `https://github.com/google/fonts/raw/main/ofl/chivo/OFL.txt`)
- Create: `app/src/main/kotlin/rehab/app/ui/theme/Tokens.kt`, `Type.kt`, `RehabTheme.kt`
- Create: `app/src/main/kotlin/rehab/app/ui/components/` : `Header.kt`, `StatusPill.kt`, `RecordRing.kt`, `GaugeCard.kt`, `SettingsGroup.kt`, `KeyValueGroup.kt`, `AlertBanner.kt`, `Buttons.kt`, `NavBar.kt`, `Texts.kt`
- Create: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values/colors.xml`, `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/rehab/app/ui/MainActivity.kt`, `app/src/main/kotlin/rehab/app/overlay/OverlayController.kt` (`MaterialTheme` → `RehabTheme`)
- Test: `app/src/test/kotlin/rehab/app/ui/components/MonoNumbersTest.kt`

**Interfaces:**
- Produces (utilisés par toutes les tâches suivantes) :
  - `object RehabColors { Bg, Panel, Text, Muted, Line, Accent, AccentHover, Danger, DangerDeep, DangerTrack, DangerText, Warn, WarnBg, RingOff }` (`androidx.compose.ui.graphics.Color`)
  - `val Chivo: FontFamily`, `val ChivoMono: FontFamily`
  - `@Composable fun RehabTheme(content: @Composable () -> Unit)`
  - `@Composable fun RehabHeader(right: @Composable () -> Unit = {})`, `@Composable fun HeaderCaption(text: String)`
  - `@Composable fun StatusPill(text: String, color: Color)`
  - `@Composable fun RecordRing(size: Dp, stroke: Dp, ringColor: Color, fraction: Float = 1f, trackColor: Color = Color.Transparent, overlayArc: Float = 0f, overlayArcColor: Color = RehabColors.Danger, ringAlpha: Float = 1f, content: @Composable ColumnScope.() -> Unit)`
  - `@Composable fun GaugeCard(label: String, value: String, fraction: Float, exceeded: Boolean)`
  - `data class RowSpec(val label: String, val value: String, val subtitle: String? = null, val lockedReason: String? = null, val valueColor: Color? = null, val onClick: (() -> Unit)? = null)` ; `@Composable fun SettingsGroup(rows: List<RowSpec>, modifier: Modifier = Modifier)`
  - `data class KeyValue(val key: String, val value: String, val valueColor: Color = RehabColors.Text, val keyMono: Boolean = false, val onClick: (() -> Unit)? = null)` ; `@Composable fun KeyValueGroup(items: List<KeyValue>)`
  - `@Composable fun AlertBanner(text: String, action: String?, onAction: () -> Unit)`
  - `@Composable fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)`, `SecondaryButton(...)` (même signature), `AccentButton(...)` (même signature)
  - `@Composable fun RehabNavBar(labels: List<String>, selected: Int, onSelect: (Int) -> Unit)`
  - `@Composable fun SectionLabel(text: String)`, `@Composable fun Note(text: String)`
  - `fun withMonoNumbers(text: String, numberColor: Color? = null): AnnotatedString`

- [ ] **Step 1: Test de `withMonoNumbers`** (`MonoNumbersTest.kt`, JUnit4, pas de Robolectric) :

```kotlin
package rehab.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import rehab.app.ui.theme.ChivoMono

class MonoNumbersTest {
    @Test fun spansCoverNumberRuns() {
        val s = withMonoNumbers("ancien record 23 · jokers 2/2")
        assertEquals("ancien record 23 · jokers 2/2", s.text)
        val ranges = s.spanStyles.map { s.text.substring(it.start, it.end) }
        assertEquals(listOf("23", "2/2"), ranges)
        assertEquals(ChivoMono, s.spanStyles.first().item.fontFamily)
    }

    @Test fun timesAndArrowsStayTogether() {
        val s = withMonoNumbers("23:00 → 07:30")
        assertEquals(listOf("23:00", "07:30"), s.spanStyles.map { s.text.substring(it.start, it.end) })
    }

    @Test fun noNumberNoSpan() {
        assertEquals(0, withMonoNumbers("Libre").spanStyles.size)
    }
}
```

- [ ] **Step 2: Lancer** `./gradlew :app:testDebugUnitTest --tests '*MonoNumbersTest*'` — attendu : échec de compilation.

- [ ] **Step 3: Polices et tokens.** Copier les deux `.ttf` et `OFL.txt`. `Tokens.kt` :

```kotlin
package rehab.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Tokens de DESIGN.md §2. Aucune autre couleur dans l'UI. */
object RehabColors {
    val Bg = Color(0xFF171513)
    val Panel = Color(0xFF24211D)
    val Text = Color(0xFFEDE7DB)
    val Muted = Color(0xFFA0978A)
    val Line = Color(0xFF33302B)
    val Accent = Color(0xFFB98457)
    val AccentHover = Color(0xFFCD9A6C)
    val Danger = Color(0xFFE4614F)
    val DangerDeep = Color(0xFF6B211B)
    val DangerTrack = Color(0xFF9A3A30)
    val DangerText = Color(0xFFF6E4E0)
    val Warn = Color(0xFFE2B45A)
    val WarnBg = Color(0xFF2A2418)
    val RingOff = Color(0xFF4A443C)
}
```

`Type.kt` (fichiers variables : une entrée `Font` par graisse, via `FontVariation` ; ajouter `@OptIn(ExperimentalTextApi::class)` si le compilateur l'exige) :

```kotlin
package rehab.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import rehab.app.R

private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Chivo = FontFamily(
    variable(R.font.chivo, 300), variable(R.font.chivo, 400), variable(R.font.chivo, 500), variable(R.font.chivo, 700),
)

val ChivoMono = FontFamily(
    variable(R.font.chivo_mono, 300), variable(R.font.chivo_mono, 400), variable(R.font.chivo_mono, 500),
)
```

`RehabTheme.kt` : `MaterialTheme(colorScheme = darkColorScheme(primary = Accent, onPrimary = Bg, secondary = Accent, onSecondary = Bg, background = Bg, onBackground = Text, surface = Panel, onSurface = Text, surfaceVariant = Panel, onSurfaceVariant = Muted, surfaceContainer = Panel, surfaceContainerHigh = Panel, surfaceContainerHighest = Line, outline = Muted, outlineVariant = Line, error = Danger, onError = Bg), typography = Typography()` avec **chaque** style de `Typography()` recopié en `copy(fontFamily = Chivo)`), puis `CompositionLocalProvider(LocalContentColor provides RehabColors.Text, LocalTextStyle provides TextStyle(fontFamily = Chivo, color = RehabColors.Text)) { content() }`.

- [ ] **Step 4: `Texts.kt`** — styles partagés et `withMonoNumbers` :

```kotlin
package rehab.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import rehab.app.ui.theme.Chivo
import rehab.app.ui.theme.ChivoMono
import rehab.app.ui.theme.RehabColors

object RehabText {
    val brand = TextStyle(fontFamily = Chivo, fontSize = 13.sp, fontWeight = FontWeight.W500, letterSpacing = 0.16.em, color = RehabColors.Text)
    val section = TextStyle(fontFamily = Chivo, fontSize = 11.sp, letterSpacing = 0.14.em, color = RehabColors.Muted)
    val caps12 = TextStyle(fontFamily = Chivo, fontSize = 12.sp, letterSpacing = 0.08.em, color = RehabColors.Muted)
    val ringLabel = TextStyle(fontFamily = Chivo, fontSize = 11.sp, fontWeight = FontWeight.W700, letterSpacing = 0.2.em)
    val ringUnit = TextStyle(fontFamily = Chivo, fontSize = 11.sp, letterSpacing = 0.16.em, color = RehabColors.Muted)
    val body13 = TextStyle(fontFamily = Chivo, fontSize = 13.sp, color = RehabColors.Text)
    val body14 = TextStyle(fontFamily = Chivo, fontSize = 14.sp, color = RehabColors.Text)
    val small11 = TextStyle(fontFamily = Chivo, fontSize = 11.sp, color = RehabColors.Muted)
    val note = TextStyle(fontFamily = Chivo, fontSize = 12.sp, lineHeight = 18.sp, color = RehabColors.Muted)
    val mono13 = TextStyle(fontFamily = ChivoMono, fontSize = 13.sp, color = RehabColors.Text)
    val mono14 = TextStyle(fontFamily = ChivoMono, fontSize = 14.sp, color = RehabColors.Text)
}

private val numberRun = Regex("""\d+(?:[/:.,]\d+)*""")

/** Passe en Chivo Mono chaque suite de chiffres (« 23 », « 2/2 », « 07:30 ») : DESIGN §2, valeurs en mono dans le texte courant. */
fun withMonoNumbers(text: String, numberColor: Color? = null): AnnotatedString = buildAnnotatedString {
    append(text)
    numberRun.findAll(text).forEach {
        addStyle(SpanStyle(fontFamily = ChivoMono, color = numberColor ?: Color.Unspecified), it.range.first, it.range.last + 1)
    }
}
```

- [ ] **Step 5: Lancer** le test de l'étape 1 — attendu : PASS.

- [ ] **Step 6: Composants.** Écrire chaque fichier du dossier `components/` d'après DESIGN §3 et les maquettes (valeurs en dp/sp). Code de référence :

```kotlin
// Header.kt
@Composable
fun RehabHeader(right: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("REHAB", style = RehabText.brand)
        right()
    }
}

@Composable
fun HeaderCaption(text: String) = Text(text.uppercase(), style = RehabText.caps12)

// StatusPill.kt
@Composable
fun StatusPill(text: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(RehabColors.Panel).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text.uppercase(), style = RehabText.caps12.copy(color = color))
    }
}

// RecordRing.kt — anneau plein si fraction = 1 ; arc depuis 12 h sinon ; rien dessiné pour un arc de longueur 0 (pas de point).
@Composable
fun RecordRing(
    size: Dp,
    stroke: Dp,
    ringColor: Color,
    fraction: Float = 1f,
    trackColor: Color = Color.Transparent,
    overlayArc: Float = 0f,
    overlayArcColor: Color = RehabColors.Danger,
    ringAlpha: Float = 1f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = stroke.toPx()
            val inset = w / 2 + 4.dp.toPx()
            val topLeft = Offset(inset, inset)
            val arcSize = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
            val style = Stroke(width = w)
            if (trackColor.alpha > 0f) drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = style)
            val f = fraction.coerceIn(0f, 1f)
            if (f > 0f) drawArc(ringColor, -90f, 360f * f, false, topLeft, arcSize, alpha = ringAlpha, style = style)
            val o = overlayArc.coerceIn(0f, 1f)
            if (o > 0f) drawArc(overlayArcColor, -90f, 360f * o, false, topLeft, arcSize, alpha = 0.9f, style = style)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

// GaugeCard.kt
@Composable
fun GaugeCard(label: String, value: String, fraction: Float, exceeded: Boolean) {
    val color = if (exceeded) RehabColors.Danger else RehabColors.Accent
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RehabColors.Panel).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label.uppercase(), style = RehabText.caps12)
            Text(value.uppercase(), style = RehabText.caps12.copy(fontFamily = ChivoMono, color = if (exceeded) RehabColors.Danger else RehabColors.Text))
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(RehabColors.Bg)) {
            val f = fraction.coerceIn(0f, 1f)
            if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}
```

`SettingsGroup.kt` : `Column(modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(16.dp)))`, une ligne par `RowSpec`, séparées par un filet `Box(Modifier.fillMaxWidth().height(1.dp).background(RehabColors.Bg))`. Ligne : fond `Panel`, padding 14 × 16, `Row(SpaceBetween, CenterVertically)`, `clickable` seulement si `onClick != null && lockedReason == null`. Gauche : si verrouillée, `Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp), tint = Muted)` + 8 dp ; libellé `body14` (couleur `Muted` si verrouillée) ; `subtitle` dessous en `small11`. Droite : valeur `mono14` (couleur `valueColor ?: Text`, `Muted` si verrouillée) ; si verrouillée, `lockedReason` dessous en `small11` aligné à droite ; sinon, si `onClick != null`, chevron `Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(16.dp), tint = Muted)` à 10 dp. La colonne de gauche prend `Modifier.weight(1f, fill = false)` et la droite peut passer à la ligne (Review Focus 1).

`KeyValueGroup.kt` : même carte, padding 11 × 16, clé `body13` couleur `Muted` (police `ChivoMono` si `keyMono`), valeur `mono13` couleur `valueColor`, `clickable` si `onClick`.

`AlertBanner.kt` : `Row(fillMaxWidth, clip(RoundedCornerShape(14.dp)), background(WarnBg), padding(horizontal = 14.dp, vertical = 12.dp), spacedBy(12.dp), CenterVertically)` ; texte 13 sp `Warn` en `weight(1f)` ; action (si non nulle) `Text(action.uppercase(), fontSize = 11.sp, fontWeight = W700, letterSpacing = 0.06.em, color = Warn)` dans une `Box(Modifier.defaultMinSize(minHeight = 44.dp).clickable(onClick = onAction), contentAlignment = Alignment.Center)`.

`Buttons.kt` :
- `PrimaryButton` : hauteur 60, rayon 18, fond `Text` (pressé : `Muted` via `interactionSource.collectIsPressedAsState()` ; désactivé : fond `Line`, texte `Muted`), texte `uppercase()` 14 sp W700 0.1 em couleur `Bg`.
- `SecondaryButton` : hauteur 48, rayon 14, bordure 1 dp `Muted`, fond transparent, texte 13 sp W500 0.06 em `Text` (désactivé : texte et bordure `Line`).
- `AccentButton` : hauteur 50, rayon 14, fond `Accent`, texte 13 sp W700 0.06 em `Bg` (désactivé : fond `Line`, texte `Muted`).
Tous : `Box` + `clickable(enabled, role = Role.Button)`, texte centré, `fillMaxWidth` laissé à l'appelant via `modifier`.

`NavBar.kt` :

```kotlin
@Composable
fun RehabNavBar(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 18.dp)
            .clip(RoundedCornerShape(22.dp)).background(RehabColors.Panel).padding(6.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(if (active) RehabColors.Accent else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label.uppercase(), maxLines = 1, softWrap = false,
                    style = TextStyle(fontFamily = Chivo, fontSize = 11.sp, letterSpacing = 0.1.em,
                        fontWeight = if (active) FontWeight.W700 else FontWeight.W400,
                        color = if (active) RehabColors.Bg else RehabColors.Muted),
                )
            }
        }
    }
}
```

`SectionLabel(text)` : `Text(text.uppercase(), style = RehabText.section, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 10.dp))`. `Note(text)` : `Text(text, style = RehabText.note, modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 10.dp))`.

- [ ] **Step 7: Thème Android et squelette de l'app.**
  - `colors.xml` : ajouter `<color name="rehab_bg">#171513</color>` et passer `ic_launcher_background` à `#171513`.
  - `themes.xml` : `<style name="Theme.Rehab" parent="android:Theme.Material.NoActionBar"><item name="android:windowBackground">@color/rehab_bg</item><item name="android:statusBarColor">@android:color/transparent</item><item name="android:navigationBarColor">@android:color/transparent</item></style>` ; manifeste : `android:theme="@style/Theme.Rehab"`.
  - `MainActivity.onCreate` : `enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))` avant `setContent`, `MaterialTheme` → `RehabTheme`.
  - `RehabApp` : remplacer `Scaffold`/`NavigationBar` par :

```kotlin
    Column(Modifier.fillMaxSize().background(RehabColors.Bg).statusBarsPadding().padding(top = 16.dp)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.Accueil -> HomeScreen(home)
                Tab.Reglages -> SettingsScreen(vm)
                Tab.Journal -> JournalScreen(vm)
                Tab.Debug -> DebugScreen(vm)
            }
        }
        Box(Modifier.navigationBarsPadding()) {
            RehabNavBar(Tab.entries.map { it.label }, Tab.entries.indexOf(tab)) { tab = Tab.entries[it] }
        }
    }
```

  L'onboarding est aussi enveloppé dans ce fond (`Box(Modifier.fillMaxSize().background(RehabColors.Bg).statusBarsPadding().navigationBarsPadding())`).
  - `OverlayController` : `MaterialTheme { … }` → `RehabTheme { … }`.

- [ ] **Step 8: Lancer** `./gradlew :app:testDebugUnitTest :app:assembleDebug` — attendu : vert, APK construit.

- [ ] **Step 9: Vérifier sur le téléphone** : `./gradlew :app:installDebug`, ouvrir Rehab. Attendu : fond sombre partout, barre de navigation en pilule avec onglet actif bronze, pas de flash blanc au lancement, les écrans existants restent utilisables (dialogues et champs en sombre). Mettre la taille de police Android à 130 % : les libellés de la barre restent sur une ligne.

- [ ] **Step 10: Commit**

```bash
git add app docs/design/fonts
git commit -m "feat(app): thème bronze mat, polices Chivo et composants partagés"
```

---

## Task 3: Accueil

**Files:**
- Modify: `app/src/main/kotlin/rehab/app/ui/HomeText.kt`, `HomeScreen.kt`, `RehabViewModel.kt` (`HomeUiState`, `compute()`), `MainActivity.kt` (actions des bandeaux)
- Test: `app/src/test/kotlin/rehab/app/ui/HomeTextTest.kt` (réécrit)

**Interfaces:**
- Consumes: `StreakSummary`, `ActiveUnlock`, `Schedule.nextNight`, `Schedule.NightPeriod` (tâche 1) ; composants et `RehabColors` (tâche 2).
- Produces:

```kotlin
enum class PillTone { Accent, Muted, Warn }
data class Pill(val text: String, val tone: PillTone)
enum class AlertAction { OpenAccessibility, OpenDebug }
data class HomeAlert(val text: String, val action: AlertAction?)
data class Gauge(val label: String, val value: String, val fraction: Float, val exceeded: Boolean)
data class NightLine(val label: String, val value: String)
data class HomeUiState(
    val loaded: Boolean = false,
    val pill: Pill = Pill("…", PillTone.Muted),
    val alerts: List<HomeAlert> = emptyList(),
    val streak: StreakSummary = StreakSummary(0, 0, 0),
    val statusLine: String? = null,
    val jokersLeft: Int = 0,
    val jokersPerDay: Int = 0,
    val gauges: List<Gauge> = emptyList(),
    val night: NightLine = NightLine("Prochaine nuit", "—"),
    val serviceConnected: Boolean = false,
)
```

  `HomeScreen(state: HomeUiState, onAlertAction: (AlertAction) -> Unit)`.

- [ ] **Step 1: Réécrire `HomeTextTest.kt`** (garder les tests `durations` et `plural` existants s'ils y sont) :

```kotlin
class HomeTextTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 9, 20, h, m, 0, 0, zone).toInstant()
    private val w30 = QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(5))
    private val w6h = QuotaWindow(Duration.ofHours(6), Duration.ofMinutes(30))

    @Test fun pillStates() {
        assertEquals(Pill("Service inactif", PillTone.Warn), HomeText.pill(false, Decision.Allow, null, zone))
        assertEquals(Pill("Libre", PillTone.Accent), HomeText.pill(true, Decision.Allow, null, zone))
        assertEquals(Pill("Bloqué · quota", PillTone.Muted), HomeText.pill(true, Decision.Block(BlockReason.Quota, at(22, 18)), null, zone))
        assertEquals(Pill("Bloqué · nuit", PillTone.Muted), HomeText.pill(true, Decision.Block(BlockReason.Night, at(7, 30)), null, zone))
        assertEquals(Pill("Joker · jusqu'à 22:05", PillTone.Accent), HomeText.pill(true, Decision.Allow, ActiveUnlock(ActiveUnlock.Kind.Joker, at(22, 5)), zone))
        assertEquals(Pill("Relapse · jusqu'à 22:17", PillTone.Accent), HomeText.pill(true, Decision.Allow, ActiveUnlock(ActiveUnlock.Kind.Relapse, at(22, 17)), zone))
    }

    @Test fun statusLine() {
        val now = at(22, 0)
        assertEquals("Quota atteint · déblocage dans 18 min (22:18)", HomeText.statusLine(Decision.Block(BlockReason.Quota, at(22, 18)), null, now, zone))
        // 17 min 30 s restantes : arrondi au-dessus (18 min), l'heure affichée reste celle de l'échéance.
        assertEquals("Quota atteint · déblocage dans 18 min (22:17)", HomeText.statusLine(Decision.Block(BlockReason.Quota, at(22, 17).plusSeconds(30)), null, now, zone))
        assertEquals("Nuit · déblocage à 07:30", HomeText.statusLine(Decision.Block(BlockReason.Night, at(7, 30)), null, now, zone))
        assertEquals("Joker actif · blocage suspendu jusqu'à 22:05", HomeText.statusLine(Decision.Allow, ActiveUnlock(ActiveUnlock.Kind.Joker, at(22, 5)), now, zone))
        assertEquals("Relapse · blocage suspendu jusqu'à 22:15", HomeText.statusLine(Decision.Allow, ActiveUnlock(ActiveUnlock.Kind.Relapse, at(22, 15)), now, zone))
        assertNull(HomeText.statusLine(Decision.Allow, null, now, zone))
    }

    @Test fun quotaWaitRoundsUpAndSwitchesToHours() {
        val now = at(20, 0)
        assertEquals("Quota atteint · déblocage dans 1 h 05 min (21:05)", HomeText.statusLine(Decision.Block(BlockReason.Quota, at(21, 5)), null, now, zone))
        assertEquals("Quota atteint · déblocage dans 1 min (20:00)", HomeText.statusLine(Decision.Block(BlockReason.Quota, now.plusSeconds(20)), null, now, zone))
    }

    @Test fun recordLines() {
        assertEquals("Record en cours depuis 1 jour", HomeText.recordLine(StreakSummary(24, 24, 23)))
        assertEquals("Record en cours depuis 3 jours", HomeText.recordLine(StreakSummary(26, 26, 23)))
        assertNull(HomeText.recordLine(StreakSummary(12, 23, 23)))
        assertEquals("ancien record 23 · jokers 2/2", HomeText.metaLine(StreakSummary(24, 24, 23), 2, 2))
        assertEquals("record 23 · jokers 1/2", HomeText.metaLine(StreakSummary(12, 23, 23), 1, 2))
        assertEquals("record 0 · jokers 0/0", HomeText.metaLine(StreakSummary(0, 0, 0), 0, 0))
    }

    @Test fun ringFraction() {
        assertEquals(1f, HomeText.ringFraction(StreakSummary(24, 24, 23)))
        assertEquals(12f / 23f, HomeText.ringFraction(StreakSummary(12, 23, 23)))
        assertEquals(0f, HomeText.ringFraction(StreakSummary(0, 0, 0)))
    }

    @Test fun gauges() {
        val g30 = HomeText.gauge(SlidingQuota.WindowUsage(w30, Duration.ofMinutes(3).plusSeconds(20)))
        assertEquals("Fenêtre 30 min", g30.label); assertEquals("3 / 5 min", g30.value); assertEquals(200f / 300f, g30.fraction, 0.001f); assertFalse(g30.exceeded)
        val g = HomeText.gauge(SlidingQuota.WindowUsage(w6h, Duration.ofMinutes(12)))
        assertEquals("Fenêtre 6 h", g.label); assertEquals("12 / 30 min", g.value); assertEquals(0.4f, g.fraction, 0.001f); assertFalse(g.exceeded)
        val full = HomeText.gauge(SlidingQuota.WindowUsage(w30, Duration.ofMinutes(6)))
        assertEquals("6 / 5 min", full.value); assertEquals(1f, full.fraction); assertTrue(full.exceeded)
    }

    @Test fun nightLine() {
        val p = Schedule.NightPeriod(LocalDate.of(2026, 9, 20), at(23, 0), at(23, 0).plus(Duration.ofMinutes(510)))
        assertEquals(NightLine("Prochaine nuit", "23:00 → 07:30"), HomeText.night(p, at(12, 0), zone))
        assertEquals(NightLine("Nuit en cours", "→ 07:30"), HomeText.night(p, at(23, 30), zone))
        assertEquals(NightLine("Prochaine nuit", "aucune"), HomeText.night(null, at(12, 0), zone))
    }

    @Test fun alerts() {
        assertEquals(HomeAlert("Service d'accessibilité désactivé — aucun blocage actif", AlertAction.OpenAccessibility), HomeText.serviceAlert())
        assertEquals(HomeAlert("Instagram 412.0 hors plage testée — tout l'onglet Accueil compte comme cible", AlertAction.OpenDebug),
            HomeText.outOfRangeAlert("com.instagram.android", "412.0.0.35.104"))
        assertEquals(HomeAlert("X 10.60 hors plage testée — la détection peut être incomplète", AlertAction.OpenDebug),
            HomeText.outOfRangeAlert("com.twitter.android", "10.60.0-release.0"))
        assertEquals(HomeAlert("Instagram n'est pas installée", null), HomeText.notInstalledAlert("com.instagram.android"))
        assertEquals(HomeAlert("Instagram : écrans non reconnus — mode dégradé actif", AlertAction.OpenDebug), HomeText.unknownScreensAlert("com.instagram.android"))
    }
}
```

(Formules : `fraction = (used / cap).coerceIn(0, 1)` en secondes, `value = "${used.toMinutes()} / ${cap.toMinutes()} min"`, `exceeded = WindowUsage.exceeded`.)

- [ ] **Step 2: Lancer** `./gradlew :app:testDebugUnitTest --tests '*HomeTextTest*'` — attendu : échec de compilation.

- [ ] **Step 3: Implémenter `HomeText`** (garder `plural` et `duration` ; supprimer `quotaLine`, `status`, `outOfRangeMessage`) :

```kotlin
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    private fun Instant.hm(zone: ZoneId) = atZone(zone).format(hm)

    /** Minutes restantes arrondies au-dessus (au moins 1), formatées par [duration]. */
    fun waitText(from: Instant, to: Instant): String {
        val secs = Duration.between(from, to).seconds.coerceAtLeast(1)
        return duration(Duration.ofMinutes((secs + 59) / 60))
    }

    fun pill(serviceConnected: Boolean, decision: Decision, unlock: ActiveUnlock?, zone: ZoneId): Pill = when {
        !serviceConnected -> Pill("Service inactif", PillTone.Warn)
        unlock != null -> Pill("${kindName(unlock.kind)} · jusqu'à ${unlock.until.hm(zone)}", PillTone.Accent)
        decision is Decision.Block -> Pill(if (decision.reason == BlockReason.Night) "Bloqué · nuit" else "Bloqué · quota", PillTone.Muted)
        else -> Pill("Libre", PillTone.Accent)
    }

    private fun kindName(k: ActiveUnlock.Kind) = if (k == ActiveUnlock.Kind.Joker) "Joker" else "Relapse"

    fun statusLine(decision: Decision, unlock: ActiveUnlock?, now: Instant, zone: ZoneId): String? = when {
        unlock != null -> (if (unlock.kind == ActiveUnlock.Kind.Joker) "Joker actif" else "Relapse") + " · blocage suspendu jusqu'à ${unlock.until.hm(zone)}"
        decision is Decision.Block && decision.reason == BlockReason.Night -> "Nuit · déblocage à ${decision.unlockAt.hm(zone)}"
        decision is Decision.Block -> "Quota atteint · déblocage dans ${waitText(now, decision.unlockAt)} (${decision.unlockAt.hm(zone)})"
        else -> null
    }

    fun recordLine(s: StreakSummary): String? =
        if (s.inRecord) "Record en cours depuis " + plural(s.current - s.previousBest, "jour") else null

    fun metaLine(s: StreakSummary, jokersLeft: Int, jokersPerDay: Int): String =
        (if (s.inRecord) "ancien record ${s.previousBest}" else "record ${s.best}") + " · jokers $jokersLeft/$jokersPerDay"

    fun ringFraction(s: StreakSummary): Float = when {
        s.inRecord -> 1f
        s.best <= 0 -> 0f
        else -> (s.current.toFloat() / s.best).coerceIn(0f, 1f)
    }

    fun gauge(u: SlidingQuota.WindowUsage): Gauge = Gauge(
        label = "Fenêtre ${duration(u.window.duration)}",
        value = "${u.used.toMinutes()} / ${u.window.cap.toMinutes()} min",
        fraction = (u.used.seconds.toFloat() / u.window.cap.seconds).coerceIn(0f, 1f),
        exceeded = u.exceeded,
    )

    fun night(p: Schedule.NightPeriod?, now: Instant, zone: ZoneId): NightLine = when {
        p == null -> NightLine("Prochaine nuit", "aucune")
        p.start <= now -> NightLine("Nuit en cours", "→ ${p.end.hm(zone)}")
        else -> NightLine("Prochaine nuit", "${p.start.hm(zone)} → ${p.end.hm(zone)}")
    }

    fun serviceAlert() = HomeAlert("Service d'accessibilité désactivé — aucun blocage actif", AlertAction.OpenAccessibility)

    /**
     * Texte véridique (revue tâche 28) : pour Instagram, le mode dégradé « version » fait jouer la
     * règle `InstagramSuggested` (`degradedFallback`) sur tout l'onglet Accueil — il compte comme
     * cible (quota, nuit), il n'est pas bloqué en permanence. X n'a pas de repli.
     */
    fun outOfRangeAlert(packageName: String, version: String?): HomeAlert {
        val v = version?.split('.', '-')?.take(2)?.joinToString(".") ?: "?"
        val consequence = if (packageName == InstagramRules.PACKAGE) "tout l'onglet Accueil compte comme cible" else "la détection peut être incomplète"
        return HomeAlert("${AppDisplayNames.of(packageName)} $v hors plage testée — $consequence", AlertAction.OpenDebug)
    }

    fun notInstalledAlert(packageName: String) = HomeAlert("${AppDisplayNames.of(packageName)} n'est pas installée", null)

    fun unknownScreensAlert(packageName: String) = HomeAlert("${AppDisplayNames.of(packageName)} : écrans non reconnus — mode dégradé actif", AlertAction.OpenDebug)
```

Vérifier dans `ScreenDetector`/`InstagramRules` que la phrase « compte comme cible » est exacte (dégradé ⇒ la cible `InstagramSuggested` s'active sur tout l'écran `instagram.home` ; elle n'est bloquée que si la politique dit `Block`). Si le code dit autre chose, ajuster le texte et le test pour qu'ils décrivent le code.

- [ ] **Step 4: Lancer** le test — attendu : PASS.

- [ ] **Step 5: `RehabViewModel.compute()`** — remplir le nouvel état :

```kotlin
    private fun compute(): HomeUiState {
        val now = graph.clock.now()
        val zone = graph.clock.zone()
        val settings = graph.settingsRepo.get()
        val decision = graph.policy.evaluate(now)
        val unlock = graph.unlock.activeUnlock(now)
        val serviceConnected = graph.serviceState.connected.value
        val alerts = buildList {
            if (!serviceConnected) add(HomeText.serviceAlert())
            graph.versionChecker.statuses.value.forEach { s ->
                if (!s.installed) add(HomeText.notInstalledAlert(s.packageName))
                else if (!s.inRange) add(HomeText.outOfRangeAlert(s.packageName, s.version))
            }
            val last = graph.detectionState.last.value
            // "version" est déjà couvert par l'alerte hors plage ci-dessus.
            if (last?.degradedReason == "unknown") add(HomeText.unknownScreensAlert(last.packageName))
        }
        return HomeUiState(
            loaded = true,
            pill = HomeText.pill(serviceConnected, decision, unlock, zone),
            alerts = alerts,
            streak = graph.streak.summary(now),
            statusLine = HomeText.statusLine(decision, unlock, now, zone),
            jokersLeft = (settings.jokersPerDay - graph.unlock.jokersUsed(graph.schedule.dayOf(now))).coerceAtLeast(0),
            jokersPerDay = settings.jokersPerDay,
            gauges = graph.policy.quotaStatus(now).perWindow.map(HomeText::gauge),
            night = HomeText.night(graph.schedule.nextNight(now), now, zone),
            serviceConnected = serviceConnected,
        )
    }
```

- [ ] **Step 6: `HomeScreen`** — d'après `Main.dc.html`, `Accueil-Bloque.dc.html`, `Accueil-Alertes.dc.html` :

```kotlin
@Composable
fun HomeScreen(state: HomeUiState, onAlertAction: (AlertAction) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        RehabHeader { StatusPill(state.pill.text, state.pill.tone.color()) }
        if (state.alerts.isNotEmpty()) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.alerts.forEach { a -> AlertBanner(a.text, a.action?.label()) { a.action?.let(onAlertAction) } }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val s = state.streak
            if (s.inRecord) {
                RecordRing(220.dp, 12.dp, RehabColors.Accent) {
                    Text("RECORD", style = RehabText.ringLabel.copy(color = RehabColors.Accent))
                    Spacer(Modifier.height(4.dp))
                    Text("${s.current}", style = TextStyle(fontFamily = ChivoMono, fontSize = 84.sp, fontWeight = FontWeight.W300, lineHeight = 84.sp, color = RehabColors.Text))
                    Spacer(Modifier.height(4.dp))
                    Text(HomeText.unit(s.current), style = RehabText.ringUnit)
                }
            } else {
                // Décision de cadrage (spec §2.1) : hors record, arc partiel série / record sur piste panel.
                RecordRing(180.dp, 12.dp, RehabColors.Accent, fraction = HomeText.ringFraction(s), trackColor = RehabColors.Panel) {
                    Text("${s.current}", style = TextStyle(fontFamily = ChivoMono, fontSize = 68.sp, fontWeight = FontWeight.W300, lineHeight = 68.sp, color = RehabColors.Text))
                    Spacer(Modifier.height(4.dp))
                    Text(HomeText.unit(s.current), style = RehabText.ringUnit)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (state.statusLine ?: HomeText.recordLine(s))?.let { Text(withMonoNumbers(it), style = RehabText.body13, textAlign = TextAlign.Center) }
                Text(withMonoNumbers(HomeText.metaLine(s, state.jokersLeft, state.jokersPerDay).uppercase(), RehabColors.Text), style = RehabText.caps12, textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.gauges.forEach { GaugeCard(it.label, it.value, it.fraction, it.exceeded) }
            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.night.label.uppercase(), style = RehabText.caps12)
                Text(state.night.value, style = RehabText.caps12.copy(fontFamily = ChivoMono, color = RehabColors.Text))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
```

avec `HomeText.unit(n) = if (n <= 1) "JOUR" else "JOURS"` (ajouter un test : `unit(1) == "JOUR"`, `unit(24) == "JOURS"`), `PillTone.color()` (Accent → `RehabColors.Accent`, Muted → `Muted`, Warn → `Warn`), `AlertAction.label()` (OpenAccessibility → "Activer", OpenDebug → "Debug"). Tant que `!state.loaded`, afficher seulement l'en-tête (pas d'anneau à 0 qui clignote au lancement).

- [ ] **Step 7: Actions des bandeaux** dans `RehabApp` : `Tab.Accueil -> HomeScreen(home) { action -> when (action) { AlertAction.OpenAccessibility -> prerequisites.openAccessibilitySettings(); AlertAction.OpenDebug -> tab = Tab.Debug } }`.

- [ ] **Step 8: Lancer** `./gradlew :app:testDebugUnitTest` — attendu : vert (supprimer les usages de `HomeText.quotaLine`/`status`/`outOfRangeMessage` restants ; `grep -rn "outOfRangeMessage\|quotaLine\|HomeText.status" app/src`).

- [ ] **Step 9: Vérifier sur le téléphone** (`installDebug`) : pastille, anneau, jauges et prochaine nuit conformes à `docs/design/screens/Main.png` ; taille de police 130 % sans chevauchement.

- [ ] **Step 10: Commit** — `git commit -m "feat(app): Accueil redessiné (pastille, anneau record, jauges, prochaine nuit, alertes actionnables)"`

---

## Task 4: Réglages à application immédiate

**Files:**
- Create: `app/src/main/kotlin/rehab/app/ui/SettingsText.kt`, `app/src/main/kotlin/rehab/app/ui/SettingsEdits.kt`, `app/src/main/kotlin/rehab/app/ui/SettingsDialogs.kt`
- Modify: `app/src/main/kotlin/rehab/app/ui/SettingsScreen.kt` (réécrit), `RehabViewModel.kt` (`SettingsScreenState`, `SettingsLocks`)
- Delete: `app/src/main/kotlin/rehab/app/ui/SettingsForm.kt`, `app/src/test/kotlin/rehab/app/ui/SettingsFormTest.kt` (leurs cas de validation migrent dans `SettingsEditsTest`)
- Test: `app/src/test/kotlin/rehab/app/ui/SettingsTextTest.kt`, `SettingsEditsTest.kt`

**Interfaces:**
- Consumes: composants (tâche 2), `HomeText.duration`, `HomeText.waitText` (tâche 3).
- Produces:

```kotlin
// RehabViewModel
data class SettingsLocks(val now: Instant, val lockedNightRow: DayOfWeek?, val lockedNightEnd: Instant?, val quotaUnlockAt: Instant?, val lockedWindows: Set<Duration>)
data class SettingsScreenState(val settings: Settings, val zone: ZoneId, val locks: SettingsLocks)
suspend fun loadSettingsScreen(): SettingsScreenState
suspend fun loadLocks(): SettingsLocks
suspend fun saveSettings(proposed: Settings): GuardResult   // inchangé
```

- [ ] **Step 1: Lire** `SettingsFormTest.kt` pour recenser les messages d'erreur testés (valeur manquante, entier attendu, > 0, négatif, plafond > durée) : ils doivent survivre dans `SettingsEdits`.

- [ ] **Step 2: `SettingsEditsTest.kt`** :

```kotlin
class SettingsEditsTest {
    private val s = Settings.DEFAULT

    @Test fun night() {
        val r = SettingsEdits.withNight(s, DayOfWeek.SUNDAY, LocalTime.of(22, 30), LocalTime.of(7, 0))
        assertEquals(NightWindow(LocalTime.of(22, 30), LocalTime.of(7, 0)), r.nights[DayOfWeek.SUNDAY])
        assertEquals(s.nights[DayOfWeek.MONDAY], r.nights[DayOfWeek.MONDAY])
    }

    @Test fun windowEditKeepsDurationAndCapApart() {
        val r = SettingsEdits.withWindow(s, 0, durationText = "45", capText = "10").getOrThrow()
        assertEquals(QuotaWindow(Duration.ofMinutes(45), Duration.ofMinutes(10)), r.quotaWindows[0])
        assertEquals(s.quotaWindows[1], r.quotaWindows[1])
    }

    @Test fun windowValidation() {
        assertEquals("Plafond : doit être supérieur à 0", SettingsEdits.withWindow(s, 0, "30", "0").exceptionOrNull()?.message)
        assertEquals("Durée : nombre entier attendu", SettingsEdits.withWindow(s, 0, "abc", "5").exceptionOrNull()?.message)
        assertEquals("Le plafond dépasse la durée de la fenêtre", SettingsEdits.withWindow(s, 0, "30", "40").exceptionOrNull()?.message)
        assertEquals("Durée : valeur manquante", SettingsEdits.addWindow(s, " ", "5").exceptionOrNull()?.message)
    }

    @Test fun addAndRemoveWindows() {
        val added = SettingsEdits.addWindow(s, "60", "10").getOrThrow()
        assertEquals(3, added.quotaWindows.size)
        val none = SettingsEdits.removeWindow(SettingsEdits.removeWindow(s, 0), 0)
        assertTrue(none.quotaWindows.isEmpty())
    }

    @Test fun unlockFields() {
        assertEquals(Duration.ofMinutes(7), SettingsEdits.withJokerMinutes(s, "7").getOrThrow().jokerDuration)
        assertEquals(Duration.ofMinutes(20), SettingsEdits.withRelapseMinutes(s, "20").getOrThrow().relapseDuration)
        assertEquals(0, SettingsEdits.withJokersPerDay(s, "0").getOrThrow().jokersPerDay)
        assertEquals("Jokers par jour : ne peut pas être négatif", SettingsEdits.withJokersPerDay(s, "-1").exceptionOrNull()?.message)
        assertEquals(Duration.ofSeconds(15), SettingsEdits.withHoldSeconds(s, "15").getOrThrow().holdDuration)
        assertEquals("Durée d’appui : doit être supérieur à 0", SettingsEdits.withHoldSeconds(s, "0").exceptionOrNull()?.message)
    }
}
```

- [ ] **Step 3: `SettingsTextTest.kt`** :

```kotlin
class SettingsTextTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val now = ZonedDateTime.of(2026, 9, 20, 22, 0, 0, 0, zone).toInstant()

    @Test fun values() {
        assertEquals("23:00 → 07:30", SettingsText.nightValue(NightWindow(LocalTime.of(23, 0), LocalTime.of(7, 30))))
        assertEquals("désactivée", SettingsText.nightValue(NightWindow(LocalTime.of(0, 0), LocalTime.of(0, 0))))
        assertEquals("Sur 30 min", SettingsText.windowLabel(QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(5))))
        assertEquals("Sur 6 h", SettingsText.windowLabel(QuotaWindow(Duration.ofHours(6), Duration.ofMinutes(30))))
        assertEquals("5 min max", SettingsText.windowValue(QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(5))))
        assertEquals("10 s", SettingsText.seconds(Duration.ofSeconds(10)))
    }

    @Test fun lockReasons() {
        assertEquals("Nuit en cours — modifiable à partir de 07:30", SettingsText.nightLockReason(ZonedDateTime.of(2026, 9, 21, 7, 30, 0, 0, zone).toInstant(), zone))
        assertEquals("Quota dépassé — modifiable dans 18 min", SettingsText.quotaLockReason(now.plusSeconds(18 * 60 - 20), now))
    }

    @Test fun rejection() {
        val r = GuardResult.Rejected("Quota en cours", now.plusSeconds(18 * 60))
        assertEquals("Quota en cours : modifiable à partir de 22:18.", SettingsText.rejection(r, zone))
    }
}
```

- [ ] **Step 4: Lancer** les deux tests — attendu : échec de compilation.

- [ ] **Step 5: Implémenter `SettingsEdits`** (objet pur ; reprendre les helpers `parsePositiveLong`/`parseNonNegativeInt` de `SettingsForm`, les rendre `private` ici) :

```kotlin
object SettingsEdits {
    fun withNight(s: Settings, day: DayOfWeek, bedtime: LocalTime, wakeup: LocalTime): Settings =
        s.copy(nights = s.nights + (day to NightWindow(bedtime, wakeup)))

    fun withWindow(s: Settings, index: Int, durationText: String, capText: String): Result<Settings> =
        window(durationText, capText).map { w -> s.copy(quotaWindows = s.quotaWindows.toMutableList().also { it[index] = w }) }

    fun addWindow(s: Settings, durationText: String, capText: String): Result<Settings> =
        window(durationText, capText).map { s.copy(quotaWindows = s.quotaWindows + it) }

    fun removeWindow(s: Settings, index: Int): Settings =
        s.copy(quotaWindows = s.quotaWindows.filterIndexed { i, _ -> i != index })

    fun withJokerMinutes(s: Settings, text: String) = runCatching { s.copy(jokerDuration = Duration.ofMinutes(parsePositiveLong(text, "Durée d’un joker"))) }
    fun withRelapseMinutes(s: Settings, text: String) = runCatching { s.copy(relapseDuration = Duration.ofMinutes(parsePositiveLong(text, "Durée d’un relapse"))) }
    fun withJokersPerDay(s: Settings, text: String) = runCatching { s.copy(jokersPerDay = parseNonNegativeInt(text, "Jokers par jour")) }
    fun withHoldSeconds(s: Settings, text: String) = runCatching { s.copy(holdDuration = Duration.ofSeconds(parsePositiveLong(text, "Durée d’appui"))) }

    private fun window(durationText: String, capText: String): Result<QuotaWindow> = runCatching {
        val d = parsePositiveLong(durationText, "Durée")
        val c = parsePositiveLong(capText, "Plafond")
        if (c > d) error("Le plafond dépasse la durée de la fenêtre")
        QuotaWindow(Duration.ofMinutes(d), Duration.ofMinutes(c))
    }
    // parsePositiveLong / parseNonNegativeInt : copiés de SettingsForm (mêmes messages « X : valeur manquante », « X : nombre entier attendu », « X : doit être supérieur à 0 », « X : ne peut pas être négatif »).
}
```

- [ ] **Step 6: Implémenter `SettingsText`** :

```kotlin
object SettingsText {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")
    fun nightValue(w: NightWindow) = if (w.isEmpty) "désactivée" else "${w.bedtime.format(hm)} → ${w.wakeup.format(hm)}"
    fun windowLabel(w: QuotaWindow) = "Sur ${HomeText.duration(w.duration)}"
    fun windowValue(w: QuotaWindow) = "${HomeText.duration(w.cap)} max"
    fun minutes(d: Duration) = "${d.toMinutes()} min"
    fun seconds(d: Duration) = "${d.seconds} s"
    fun dayName(d: DayOfWeek): String = d.getDisplayName(TextStyle.FULL, Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) }
    fun nightLockReason(end: Instant, zone: ZoneId) = "Nuit en cours — modifiable à partir de ${end.atZone(zone).format(hm)}"
    fun quotaLockReason(unlockAt: Instant, now: Instant) = "Quota dépassé — modifiable dans ${HomeText.waitText(now, unlockAt)}"
    fun rejection(r: GuardResult.Rejected, zone: ZoneId) = "${r.reason} : modifiable à partir de ${r.unlockAt.atZone(zone).format(hm)}."
}
```

- [ ] **Step 7: Lancer** les tests — attendu : PASS. Supprimer `SettingsForm.kt` et `SettingsFormTest.kt` (`grep -rn SettingsForm app/src` doit être vide après l'étape 9).

- [ ] **Step 8: ViewModel** — `SettingsScreenState(settings = graph.settingsRepo.get(), zone, locks = currentLocks())`, et dans `currentLocks()` :

```kotlin
        val status = graph.policy.quotaStatus(now)
        return SettingsLocks(
            now = now,
            lockedNightRow = activeNight?.row?.dayOfWeek,
            lockedNightEnd = activeNight?.end,
            quotaUnlockAt = status.unlockAt,
            // Même règle que SettingsGuard.checkQuota : seules les fenêtres en dépassement sont figées.
            lockedWindows = status.perWindow.filter { it.exceeded }.map { it.window.duration }.toSet(),
        )
```

(`quotaUnlockAt` vient désormais de `quotaStatus` et non plus de `evaluate` : le verrou de `SettingsGuard` tient même quand un joker ou la nuit masque le quota.)

- [ ] **Step 9: Écran et dialogues.** `SettingsScreen(vm)` :
  - État : `settings: Settings?` (chargé par `loadSettingsScreen()`), `zone`, `locks` rafraîchi chaque seconde par la boucle `repeatOnLifecycle(STARTED)` existante (garder son commentaire), `editor: Editor?` (sealed : `Night(day)`, `Window(index)`, `NewWindow`, `Joker`, `Relapse`, `JokersPerDay`, `Hold`).
  - Sauvegarde commune :

```kotlin
    fun save(proposed: Result<Settings>, onError: (String) -> Unit) {
        proposed.onFailure { onError(it.message ?: "Valeur invalide") }.onSuccess { p ->
            scope.launch {
                when (val r = vm.saveSettings(p)) {
                    GuardResult.Accepted -> { settings = vm.loadSettingsScreen().settings; editor = null }
                    is GuardResult.Rejected -> onError(SettingsText.rejection(r, zone))   // le dialogue reste ouvert (Review Focus 5)
                }
            }
        }
    }
```

  - Mise en page (d'après `Reglages.dc.html`) dans `Column(verticalScroll)` : `RehabHeader { HeaderCaption("application immédiate") }` ; `SectionLabel("Plage nocturne")` ; `SettingsGroup` de 7 lignes (lundi → dimanche, `DayOfWeek.entries`) : `RowSpec(dayName, nightValue, lockedReason = if (day == locks.lockedNightRow) nightLockReason(locks.lockedNightEnd!!, zone) else null, onClick = { editor = Editor.Night(day) })` ; `Note("La ligne du jour décrit la nuit qui suit. Pendant une plage en cours, on ne peut que l'allonger.")` ; `SectionLabel("Quota glissant")` ; `SettingsGroup` d'une ligne par fenêtre : `RowSpec(windowLabel, windowValue, subtitle = "Toutes cibles confondues", lockedReason = if (w.duration in locks.lockedWindows) quotaLockReason(locks.quotaUnlockAt!!, locks.now) else null, onClick = { editor = Editor.Window(i) })` (si aucune fenêtre : `Note("Aucune fenêtre : pas de quota.")`) ; `SecondaryButton("Ajouter une fenêtre", { editor = Editor.NewWindow }, Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 10.dp))` ; `SectionLabel("Déblocage")` ; `SettingsGroup` : « Durée d’un joker » (`minutes`), « Durée d’un relapse », « Jokers par jour » (`"${jokersPerDay}"`), « Durée d’appui » (`seconds`) ; `Note("Les verrous sont appliqués par le domaine (SettingsGuard), pas seulement par l'écran.")` ; `Spacer(24.dp)`. Les lignes verrouillées ne sont pas cliquables (spec §2.2, géré par `SettingsGroup`).
  - `SettingsDialogs.kt` : un conteneur `EditDialog(title, error, onDismiss, onConfirm, confirmLabel = "Appliquer", extraAction: Pair<String, () -> Unit>? = null, content)` bâti sur `AlertDialog` (`containerColor = RehabColors.Panel`, `shape = RoundedCornerShape(16.dp)`, titre `body14` W500, erreur en 12 sp `Danger` sous le contenu, boutons `TextButton` couleur `Accent`, action supplémentaire « Supprimer » en `Danger` à gauche) ; puis :
    - `NightDialog(day, current: NightWindow, error, onDismiss, onConfirm: (LocalTime, LocalTime) -> Unit)` : deux « onglets » texte en haut (« Coucher 23:00 » / « Lever 07:30 », l'actif en `Accent`), un `TimePicker(rememberTimePickerState(h, m, is24Hour = true))` par onglet (deux états distincts, `TimePickerDefaults.colors(clockDialColor = Bg, selectorColor = Accent, timeSelectorSelectedContainerColor = Accent, timeSelectorSelectedContentColor = Bg, timeSelectorUnselectedContainerColor = Bg, timeSelectorUnselectedContentColor = Text, clockDialSelectedContentColor = Bg, clockDialUnselectedContentColor = Text, containerColor = Panel)`), `@OptIn(ExperimentalMaterial3Api::class)`.
    - `WindowDialog(initial: QuotaWindow?, error, onDismiss, onConfirm: (durationText, capText) -> Unit, onDelete: (() -> Unit)?)` : deux `OutlinedTextField` numériques (`KeyboardOptions(keyboardType = KeyboardType.Number)`) « Durée de la fenêtre (min) » et « Plafond (min) », valeurs initiales `initial?.duration?.toMinutes() ?: 60` et `initial?.cap?.toMinutes() ?: 10` ; « Supprimer » seulement pour une fenêtre existante.
    - `NumberDialog(title, unit, initial: String, error, onDismiss, onConfirm: (String) -> Unit)` : un champ numérique avec suffixe `unit`.
    - Branchement : `Night` → `save(Result.success(SettingsEdits.withNight(s, day, bed, wake)))`, `Window(i)` → `save(SettingsEdits.withWindow(s, i, d, c))`, suppression → `save(Result.success(SettingsEdits.removeWindow(s, i)))`, `NewWindow` → `addWindow`, les quatre champs → `withJokerMinutes` etc. `error` est un `mutableStateOf<String?>` remis à `null` à chaque ouverture.

- [ ] **Step 10: Lancer** `./gradlew :app:testDebugUnitTest` — attendu : vert.

- [ ] **Step 11: Vérifier sur le téléphone** : modifier une nuit, une fenêtre, la durée d'appui ; la valeur change immédiatement, l'Accueil en tient compte. Pendant un quota dépassé (fenêtre 30 min à 1 min de plafond pour tester), la ligne est grisée avec « Quota dépassé — modifiable dans N min » et ne s'ouvre pas. Remettre les réglages d'origine après le test.

- [ ] **Step 12: Commit** — `git commit -m "feat(app): Réglages en lignes éditables, application immédiate et raisons de verrou"`

---

## Task 5: Journal groupé par journée

**Files:**
- Modify: `app/src/main/kotlin/rehab/app/ui/JournalText.kt` (réécrit), `JournalScreen.kt` (réécrit), `RehabViewModel.kt` (`loadJournal`), `app/src/main/kotlin/rehab/app/AppDisplayNames.kt`
- Test: `app/src/test/kotlin/rehab/app/ui/JournalTextTest.kt` (réécrit)

**Interfaces:**
- Consumes: `Event.Block` (tâche 1), composants (tâche 2).
- Produces:

```kotlin
enum class JournalTone { Danger, Accent, Warn, Neutral }
data class JournalRow(val atMillis: Long, val time: String, val label: String, val type: String, val detail: String, val tone: JournalTone)
data class JournalDay(val header: String, val rows: List<JournalRow>)
object JournalText {
    fun rows(events: List<Event>, usage: List<UsageInterval>, jokersPerDay: Int, dayOf: (Instant) -> LocalDate, now: Instant, zone: ZoneId): List<JournalRow>
    fun days(rows: List<JournalRow>, dayOf: (Instant) -> LocalDate, today: LocalDate): List<JournalDay>
    fun dayHeader(day: LocalDate, today: LocalDate): String
}
object AppDisplayNames { fun of(packageName: String): String; fun target(id: String): String }
suspend fun RehabViewModel.loadJournal(): List<JournalDay>
```

- [ ] **Step 1: `JournalTextTest.kt`** (journée Rehab : lever 07:30, donc `dayOf` = jour civil décalé de 7 h 30) :

```kotlin
class JournalTextTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(d: Int, h: Int, m: Int, s: Int = 0) = ZonedDateTime.of(2026, 9, d, h, m, s, 0, zone).toInstant()
    private val dayOf: (Instant) -> LocalDate = { it.atZone(zone).minusHours(7).minusMinutes(30).toLocalDate() }
    private val now = at(20, 23, 0)
    private fun row(e: Event) = JournalText.rows(listOf(e), emptyList(), 2, dayOf, now, zone).single()

    @Test fun relapseAndJoker() {
        assertEquals(JournalRow(at(20, 21, 47).toEpochMilli(), "21:47", "Relapse · déblocage 15 min", "Événement", "21:47 → 22:02", JournalTone.Danger),
            row(Event.Relapse(at(20, 21, 47), at(20, 22, 2))))
        val jokers = JournalText.rows(listOf(Event.Joker(at(20, 14, 0), at(20, 14, 5)), Event.Joker(at(20, 20, 10), at(20, 20, 15))), emptyList(), 2, dayOf, now, zone)
        assertEquals(listOf("0 restant", "1 restant"), jokers.map { it.detail })   // tri décroissant : le plus récent d'abord
        assertEquals("Joker · déblocage 5 min", jokers[0].label)
        assertEquals(JournalTone.Accent, jokers[0].tone)
    }

    @Test fun blockProducesStartAndEndOnlyWhenPast() {
        val rows = JournalText.rows(listOf(Event.Block(at(20, 22, 0), BlockReason.Quota, at(20, 22, 18))), emptyList(), 2, dayOf, now, zone)
        assertEquals(listOf("Fin du blocage quota", "Blocage quota"), rows.map { it.label })
        assertEquals(listOf("—", "→ 22:18"), rows.map { it.detail })
        assertEquals("Blocage · Quota", rows[0].type)
        val ongoing = JournalText.rows(listOf(Event.Block(at(20, 22, 50), BlockReason.Night, at(21, 7, 30))), emptyList(), 2, dayOf, now, zone)
        assertEquals(listOf("Blocage nuit"), ongoing.map { it.label })
        assertEquals("Blocage · Nuit", ongoing[0].type)
    }

    @Test fun serviceRulesAndErrors() {
        assertEquals(JournalTone.Warn, row(Event.ServiceOff(at(20, 10, 0))).tone)
        assertEquals("Service désactivé", row(Event.ServiceOff(at(20, 10, 0))).label)
        val on = JournalText.rows(listOf(Event.ServiceOff(at(20, 9, 0)), Event.ServiceOn(at(20, 10, 0))), emptyList(), 2, dayOf, now, zone)
        assertEquals("Service réactivé", on[0].label)
        assertEquals("Service activé", row(Event.ServiceOn(at(20, 10, 0))).label)
        assertEquals(JournalTone.Neutral, on[0].tone)
        val rules = row(Event.RulesOutOfRange(at(20, 19, 40), "com.instagram.android", "412.0.0.35.104"))
        assertEquals(listOf("Règles hors plage · Instagram 412.0", "Règles", "notifié"), listOf(rules.label, rules.type, rules.detail))
        assertEquals("Erreur", row(Event.Error(at(20, 9, 0), "boom")).type)
    }

    @Test fun usage() {
        val u = JournalText.rows(emptyList(), listOf(
            UsageInterval(1, TargetId("InstagramReels"), at(20, 21, 32), at(20, 21, 36, 12), open = false),
            UsageInterval(2, TargetId("TwitterHome"), at(20, 18, 2), at(20, 18, 2, 48), open = false),
            UsageInterval(3, TargetId("InstagramSuggested"), at(20, 22, 59), at(20, 23, 0), open = true),
        ), 2, dayOf, now, zone)
        assertEquals(listOf("Instagram · Suggéré", "Instagram · Reels", "X · Accueil"), u.map { it.label })
        assertEquals(listOf("en cours", "4 min 12 s", "48 s"), u.map { it.detail })
        assertEquals("Usage", u[0].type)
    }

    @Test fun groupsByRehabDayNotCivilDay() {
        val rows = JournalText.rows(listOf(Event.ServiceOff(at(20, 1, 0)), Event.ServiceOn(at(20, 12, 0))), emptyList(), 2, dayOf, now, zone)
        val days = JournalText.days(rows, dayOf, today = LocalDate.of(2026, 9, 20))
        assertEquals(listOf("Aujourd'hui · dim. 20 sept.", "Hier · sam. 19 sept."), days.map { it.header })
        assertEquals("01:00", days[1].rows.single().time)
    }

    @Test fun olderDayHeader() {
        assertEquals("ven. 18 sept.", JournalText.dayHeader(LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 20)))
    }
}
```

- [ ] **Step 2: Lancer** — attendu : échec de compilation.

- [ ] **Step 3: `AppDisplayNames.target`** :

```kotlin
    fun target(id: String): String = when (id) {
        InstagramRules.REELS.value -> "Instagram · Reels"
        InstagramRules.SUGGESTED.value -> "Instagram · Suggéré"
        TwitterRules.HOME.value -> "X · Accueil"
        else -> id
    }
```

- [ ] **Step 4: Implémenter `JournalText`** :
  - `time` = `HH:mm` dans `zone` ; tri final `sortedByDescending { it.atMillis }`.
  - Joker : label `"Joker · déblocage ${minutes(at, unlockUntil)} min"`, type « Événement », détail `HomeText.plural(restant, "restant")` où `restant = (jokersPerDay - rang).coerceAtLeast(0)` et `rang` = position 1-based du joker parmi les jokers de la même `dayOf` triés par `at` ; ton `Accent`.
  - Relapse : label `"Relapse · déblocage N min"`, type « Événement », détail `"HH:mm → HH:mm"`, ton `Danger`.
  - Block : ligne de début (`at`) label `"Blocage nuit|quota"`, détail `"→ HH:mm"` ; si `until <= now`, ligne de fin (`until`) label `"Fin du blocage nuit|quota"`, détail `"—"` ; type `"Blocage · Nuit|Quota"` ; ton `Neutral`.
  - ServiceOn : `"Service réactivé"` si un `ServiceOff` antérieur figure dans `events`, sinon `"Service activé"` ; type « Service » ; détail « — » ; ton `Neutral`. ServiceOff : « Service désactivé », « Service », « — », `Warn`.
  - RulesOutOfRange : `"Règles hors plage · ${AppDisplayNames.of(pkg)} ${version.split('.', '-').take(2).joinToString(".")}"`, « Règles », « notifié », `Warn`.
  - Error : `"Erreur · ${message.take(80)}"`, « Erreur », « — », `Warn`.
  - Usage : label `AppDisplayNames.target(target.value)`, type « Usage », détail « en cours » si `open`, sinon `d < 60 s` → `"${s} s"`, sinon `"%d min %02d s"` ; ton `Neutral`.
  - `days` : `rows.groupBy { dayOf(Instant.ofEpochMilli(it.atMillis)) }` trié par jour décroissant, lignes gardant l'ordre décroissant ; `dayHeader` : aujourd'hui → `"Aujourd'hui · " + fmt`, veille → `"Hier · " + fmt`, sinon `fmt`, avec `fmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)` (donne « dim. 20 sept. » sur le JDK 17 ; si le JDK rend autre chose, construire le texte à partir de `DayOfWeek`/`Month.getDisplayName(TextStyle.SHORT, Locale.FRENCH)` pour obtenir exactement la forme du test).

- [ ] **Step 5: Lancer** — attendu : PASS.

- [ ] **Step 6: ViewModel** :

```kotlin
    suspend fun loadJournal(): List<JournalDay> = withContext(Dispatchers.IO) {
        val now = graph.clock.now()
        val from = now.minus(Duration.ofDays(30))
        val rows = JournalText.rows(
            graph.eventLog.since(from), graph.usageLog.intervalsSince(from),
            graph.settingsRepo.get().jokersPerDay, graph.schedule::dayOf, now, graph.clock.zone(),
        )
        JournalText.days(rows, graph.schedule::dayOf, graph.schedule.dayOf(now))
    }
```

- [ ] **Step 7: `JournalScreen`** (d'après `Journal.dc.html`) : `LazyColumn` ; en-tête `RehabHeader { HeaderCaption("30 jours · brut") }` ; par jour, en-tête `day.header.uppercase()` en `caps12` (padding 6/18 × 24 × 8) puis une carte `Panel` rayon 16 (marges 24) avec les lignes séparées par un filet `Bg` 1 dp. Ligne : `Row(padding 14 × 16, CenterVertically)` : heure `mono13 Muted` largeur 52 dp ; colonne `weight(1f)` : `Row(spacedBy 8)` point 8 dp (couleur : Danger / Accent / Warn / Muted) + libellé 14 sp (couleur : Danger / Accent / Warn / Text), type `small11` dessous ; détail `mono13 Muted` à droite (`padding(start = 12.dp)`). Pied : `Note("Intervalles de moins de 2 s supprimés. Le temps passé sous joker ou relapse est compté.")`. Vide : `Note("Aucun événement sur les 30 derniers jours.")`. Rechargement : `repeatOnLifecycle(STARTED) { while (isActive) { days = vm.loadJournal(); delay(10_000) } }`.

- [ ] **Step 8: Lancer** `./gradlew :app:testDebugUnitTest` — vert. **Vérifier sur le téléphone** : groupes, couleurs, libellés lisibles, rien de tronqué à 130 %.

- [ ] **Step 9: Commit** — `git commit -m "feat(app): Journal groupé par journée Rehab avec typologie colorée et blocages"`

---

## Task 6: Debug

**Files:**
- Create: `app/src/main/kotlin/rehab/app/ui/DebugText.kt`, `app/src/main/kotlin/rehab/app/service/CaptureNames.kt`
- Modify: `app/src/main/kotlin/rehab/app/service/CaptureCoordinator.kt`, `RehabAccessibilityService.kt` (appel `maybeSave` après la détection), `app/src/main/kotlin/rehab/app/di/AppGraph.kt` (zone passée au coordinateur), `RehabViewModel.kt`, `DebugScreen.kt` (réécrit), `app/src/main/res/xml/file_paths.xml` (si le dossier `captures/` n'y est pas couvert)
- Test: `app/src/test/kotlin/rehab/app/ui/DebugTextTest.kt`, `app/src/test/kotlin/rehab/app/service/CaptureNamesTest.kt`, `CaptureCoordinatorTest.kt` (mis à jour)

**Interfaces:**
- Consumes: `ActiveUnlock` (tâche 1), `HomeUiState` (tâche 3), composants (tâche 2).
- Produces:
  - `object CaptureNames { fun fileName(packageName: String, screenId: String?, capturedAtMillis: Long, zone: ZoneId): String }`
  - `CaptureCoordinator(dir: File, zone: ZoneId)` ; `maybeSave(snapshot: Snapshot, screenId: String?): File?` ; `list()` trié par `lastModified` décroissant
  - `object DebugText { fun decision(decision: Decision, unlock: ActiveUnlock?, usages: List<SlidingQuota.WindowUsage>, zone: ZoneId): String; fun ago(nowMillis: Long, atMillis: Long): String; fun size(bytes: Long): String }`
  - `HomeUiState` gagne `decisionSummary: String = "—"` et `nowMillis: Long = 0`
  - `data class CaptureFile(val file: File, val name: String, val sizeBytes: Long)` ; `suspend fun RehabViewModel.captures(): List<CaptureFile>` (remplace `captureCount`)

- [ ] **Step 1: Tests**

```kotlin
class CaptureNamesTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val t = ZonedDateTime.of(2026, 9, 20, 21, 31, 5, 0, zone).toInstant().toEpochMilli()
    @Test fun names() {
        assertEquals("ig_reels_2026-09-20T21-31-05.json", CaptureNames.fileName("com.instagram.android", "instagram.reels", t, zone))
        assertEquals("ig_feed_2026-09-20T21-31-05.json", CaptureNames.fileName("com.instagram.android", "instagram.home", t, zone))
        assertEquals("x_home_2026-09-20T21-31-05.json", CaptureNames.fileName("com.twitter.android", "twitter.home", t, zone))
        assertEquals("ig_inconnu_2026-09-20T21-31-05.json", CaptureNames.fileName("com.instagram.android", null, t, zone))
    }
}

class DebugTextTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 9, 20, h, m, 0, 0, zone).toInstant()
    private val u30 = SlidingQuota.WindowUsage(QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(5)), Duration.ofMinutes(3))
    private val u6h = SlidingQuota.WindowUsage(QuotaWindow(Duration.ofHours(6), Duration.ofMinutes(30)), Duration.ofMinutes(12))

    @Test fun decision() {
        assertEquals("Allow · quota 3/5 min", DebugText.decision(Decision.Allow, null, listOf(u30, u6h), zone))
        assertEquals("Allow", DebugText.decision(Decision.Allow, null, emptyList(), zone))
        assertEquals("Allow · joker → 22:05", DebugText.decision(Decision.Allow, ActiveUnlock(ActiveUnlock.Kind.Joker, at(22, 5)), listOf(u30), zone))
        assertEquals("Block · nuit → 07:30", DebugText.decision(Decision.Block(BlockReason.Night, at(7, 30)), null, listOf(u30), zone))
        assertEquals("Block · quota → 22:18", DebugText.decision(Decision.Block(BlockReason.Quota, at(22, 18)), null, listOf(u30), zone))
    }

    @Test fun ago() {
        assertEquals("3 s", DebugText.ago(10_000, 7_000))
        assertEquals("2 min 05 s", DebugText.ago(125_000, 0))
        assertEquals("1 h 02 min", DebugText.ago(3_720_000, 0))
        assertEquals("0 s", DebugText.ago(0, 5_000))
    }

    @Test fun size() {
        assertEquals("38 Ko", DebugText.size(38 * 1024L))
        assertEquals("1 Ko", DebugText.size(10))
        assertEquals("1,5 Mo", DebugText.size(1536 * 1024L))
    }
}
```

« quota a/b » = la fenêtre de plus fort ratio `used / cap`. Mettre à jour `CaptureCoordinatorTest` : nom attendu `ig_…` et nouvelle signature.

- [ ] **Step 2: Lancer** — échec de compilation.

- [ ] **Step 3: Implémenter** `CaptureNames` (préfixe `ig` pour Instagram, `x` pour X, sinon dernier segment du package ; écran = `screenId.substringAfter('.')`, `home` → `feed` pour Instagram uniquement, `null` → `inconnu` ; horodatage `yyyy-MM-dd'T'HH-mm-ss`), `DebugText` (formats des tests ; `size` : `< 1024 * 1024` → `ceil(bytes / 1024)` au moins 1, suivi de « Ko » ; sinon une décimale avec virgule, « Mo »), le coordinateur (`maybeSave(snapshot, screenId)` utilise `CaptureNames.fileName(snapshot.packageName, screenId, snapshot.capturedAt, zone)`), `AppGraph` (`CaptureCoordinator(File(context.filesDir, "captures"), clock.zone())`). Dans `process()`, déplacer `graph.capture.maybeSave(snapshot)` juste après `val detection = graph.detector.detect(...)` → `graph.capture.maybeSave(snapshot, detection.screenId)` (la capture a toujours lieu, troncature comprise).

- [ ] **Step 4: Lancer** — PASS.

- [ ] **Step 5: ViewModel** — dans `compute()`, ajouter `decisionSummary = DebugText.decision(decision, unlock, quota.perWindow, zone)` (réutiliser le `quotaStatus` déjà calculé pour les jauges) et `nowMillis = now.toEpochMilli()`. Remplacer `captureCount()` par `captures()` (`graph.capture.list().map { CaptureFile(it, it.name, it.length()) }` sur `Dispatchers.IO`).

- [ ] **Step 6: `DebugScreen(vm, home: HomeUiState)`** (d'après `Debug.dc.html`) — passer `home` depuis `RehabApp` :
  - `RehabHeader { HeaderCaption("outils") }`.
  - `SectionLabel("Capture de la structure")` ; carte `Panel` rayon 16 padding 12 × 16 : « Délai avant capture » `body14` + `BasicTextField` (52 × 36 dp, fond `Bg`, bordure 1 dp `Line`, rayon 10, `ChivoMono` 15 sp centré, clavier numérique, `rememberSaveable { "5" }`) + « S » `caps12`. `AccentButton` pleine largeur : texte « Capturer le prochain écran cible » ; si capture en attente : « En attente · N s » tant que l'échéance n'est pas passée (`N = ceil((pendingAt - home.nowMillis) / 1000)`), puis « En attente d'un écran cible… » ; désactivé si en attente ou si le délai n'est pas un entier de 1 à 60. Clic : `vm.requestCapture(delay * 1000L)`. `Note("Ouvre Instagram ou X pendant le délai : le prochain Snapshot est sérialisé en JSON dans files/captures/.")`.
  - `SectionLabel("Dernière détection · en direct")` ; `KeyValueGroup` : si `last == null` → une ligne `KeyValue("Aucune détection", "ouvre Instagram ou X")` ; sinon Package, Version, targetId (`valueColor = Accent`, « — » si nul), unknownScreen (`"true"/"false"`), Décision (`home.decisionSummary`), Il y a (`DebugText.ago(home.nowMillis, last.atMillis)`), et si `last.degraded` une ligne « Mode dégradé » = `last.degradedReason` en `Warn`.
  - `SectionLabel("Captures · ${captures.size}")` ; `KeyValueGroup` d'une ligne par fichier (`KeyValue(name, size, onClick = { share(listOf(file)) })`, clé en `Muted`) ; vide → `Note("Aucune capture.")`. Recharger la liste sur `LaunchedEffect(lastFile)`.
  - `Row(padding horizontal 24, top 12, spacedBy 10)` : `SecondaryButton("Exporter", { share(captures.map { it.file }) }, Modifier.weight(1f), enabled = captures.isNotEmpty())` et `SecondaryButton("Overlay de test", { service?.showTestOverlay() }, Modifier.weight(1f), enabled = service != null)`.
  - `share(files)` : un fichier → `ACTION_SEND` (`type = "application/json"`, `EXTRA_STREAM`) ; plusieurs → `ACTION_SEND_MULTIPLE` avec `putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))` ; `FLAG_GRANT_READ_URI_PERMISSION` ; `FileProvider.getUriForFile(context, "rehab.app.files", f)` ; `Intent.createChooser(…, "Partager les captures")`.

- [ ] **Step 7: Lancer** `./gradlew :app:testDebugUnitTest` — vert. **Vérifier sur le téléphone** : délai à 3 s, capturer, ouvrir Instagram, revenir : la capture apparaît nommée `ig_…`, taille en Ko, « Exporter » ouvre le partage avec tous les fichiers.

- [ ] **Step 8: Commit** — `git commit -m "feat(app): écran Debug redessiné (délai, décision en direct, liste et export des captures)"`

---

## Task 7: Overlay de blocage

**Files:**
- Create: `app/src/main/kotlin/rehab/app/overlay/RelapseFx.kt`
- Modify: `app/src/main/kotlin/rehab/app/overlay/OverlayState.kt` (état + `OverlayText` réécrits), `BlockOverlay.kt` (réécrit), `HoldButton.kt` (réécrit), `OverlayController.kt`, `app/src/main/kotlin/rehab/app/service/RehabAccessibilityService.kt`
- Test: `app/src/test/kotlin/rehab/app/overlay/OverlayTextTest.kt` (réécrit), `RelapseFxTest.kt`, `OverlayControllerTest.kt` (mis à jour + gel)

**Interfaces:**
- Consumes: `StreakSummary`, `Streak.summary`, `Event.Block` (tâche 1), `RehabColors`, `RecordRing`, `PrimaryButton`, `withMonoNumbers`, `RehabTheme` (tâche 2), `HomeText.waitText`, `HomeText.plural`, `HomeText.duration` (tâche 3), `SettingsText.dayName` (tâche 4).
- Produces:

```kotlin
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
data class Caption(val prefix: String, val emphasis: String, val suffix: String)
interface OverlayController.Callbacks { fun onQuit(); fun onHoldCompleted() }
const val DONE_MILLIS = 1500L   // dans OverlayController.Companion
```

- [ ] **Step 1: `OverlayTextTest.kt`** :

```kotlin
class OverlayTextTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(d: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 9, d, h, m, 0, 0, zone).toInstant()
    private val record = StreakSummary(24, 24, 23)
    private val offRecord = StreakSummary(12, 23, 23)
    private fun state(reason: BlockReason, unlock: Instant, outcome: PressOutcome = PressOutcome.Joker(1), streak: StreakSummary = record) =
        OverlayState(reason, unlock.toEpochMilli(), "détail", streak, outcome, 10_000, 5, 15, null, zone)

    @Test fun titlesAreStatic() {
        assertEquals("Nuit · déblocage à 07:30", OverlayText.title(state(BlockReason.Night, at(21, 7, 30)), at(20, 23, 30).toEpochMilli()))
        val q = state(BlockReason.Quota, at(20, 22, 18))
        assertEquals("Quota atteint · 18 min", OverlayText.title(q, at(20, 22, 0).toEpochMilli()))
        assertEquals("Quota atteint · 18 min", OverlayText.title(q, at(20, 22, 0).toEpochMilli() + 20_000))
    }

    @Test fun details() {
        assertEquals("Plage nocturne du dimanche : 23:00 → 07:30", OverlayText.nightDetail(DayOfWeek.SUNDAY, NightWindow(LocalTime.of(23, 0), LocalTime.of(7, 30))))
        assertEquals("5 min sur les 30 dernières minutes, toutes cibles", OverlayText.quotaDetail(QuotaWindow(Duration.ofMinutes(30), Duration.ofMinutes(5))))
        assertEquals("30 min sur les 6 dernières heures, toutes cibles", OverlayText.quotaDetail(QuotaWindow(Duration.ofHours(6), Duration.ofMinutes(30))))
        assertEquals("10 min sur la dernière heure, toutes cibles", OverlayText.quotaDetail(QuotaWindow(Duration.ofHours(1), Duration.ofMinutes(10))))
        assertEquals("Jokers du jour épuisés (2/2)", OverlayText.jokersExhausted(2))
        assertEquals("Aucun joker prévu (0/0)", OverlayText.jokersExhausted(0))
    }

    @Test fun streakLines() {
        assertEquals("Record en cours depuis 1 jour", OverlayText.streakLine(record))
        assertEquals("Sans relapse depuis 12 jours · record 23", OverlayText.streakLine(offRecord))
        assertEquals("Série remise à zéro · record 24 conservé", OverlayText.doneStreakLine(record))
    }

    @Test fun restCaptions() {
        assertEquals(Caption("Maintenir 10 s · ", "Joker +5 min", " · 1 restant aujourd’hui"), OverlayText.caption(state(BlockReason.Quota, at(20, 22, 18), PressOutcome.Joker(1))))
        assertEquals(Caption("Maintenir 10 s · ", "Joker +5 min", " · dernier joker du jour"), OverlayText.caption(state(BlockReason.Quota, at(20, 22, 18), PressOutcome.Joker(0))))
        assertEquals(Caption("Maintenir 10 s · ", "RELAPSE", " · ton streak de 24 jours tombe"), OverlayText.caption(state(BlockReason.Night, at(21, 7, 30), PressOutcome.Relapse(24))))
        assertEquals(Caption("Maintenir 10 s · ", "RELAPSE", " · ta série de 12 jours tombe"), OverlayText.caption(state(BlockReason.Night, at(21, 7, 30), PressOutcome.Relapse(12), offRecord)))
        assertEquals(Caption("Maintenir 10 s · ", "RELAPSE", " · ta série de 1 jour tombe"), OverlayText.caption(state(BlockReason.Night, at(21, 7, 30), PressOutcome.Relapse(1), StreakSummary(1, 5, 5))))
    }

    @Test fun holdingAndDoneCaptions() {
        assertEquals("Encore 4 s et ton streak de 24 jours tombe", OverlayText.holdingCaption(state(BlockReason.Night, at(21, 7, 30), PressOutcome.Relapse(24)), secondsLeft = 4))
        assertEquals("Encore 4 s · Joker +5 min", OverlayText.holdingCaption(state(BlockReason.Quota, at(20, 22, 18)), secondsLeft = 4))
        assertEquals("Relapse enregistré · déblocage 15 min", OverlayText.doneCaption(state(BlockReason.Night, at(21, 7, 30), PressOutcome.Relapse(24))))
        assertEquals("Joker activé · déblocage 5 min", OverlayText.doneCaption(state(BlockReason.Quota, at(20, 22, 18))))
    }
}
```

`RelapseFxTest.kt` :

```kotlin
class RelapseFxTest {
    @Test fun background() {
        assertEquals(Color(0xFF171513), RelapseFx.background(0f))
        assertEquals(Color(0xFF5A1A15), RelapseFx.background(1f))
    }
    @Test fun days() {
        assertEquals(24, RelapseFx.daysShown(24, 0f)); assertEquals(12, RelapseFx.daysShown(24, 0.5f)); assertEquals(0, RelapseFx.daysShown(24, 1f))
    }
    @Test fun periods() {
        assertEquals(0.9f - 0.5f * 0.4f, RelapseFx.pulsePeriodSec(0.4f), 1e-4f)
        assertEquals(0.14f - 0.2f * 0.2f, RelapseFx.shakePeriodSec(0.8f), 1e-4f)
    }
    @Test fun scalesAndThresholds() {
        assertEquals(1f, RelapseFx.buttonScale(0f, pulsePhase = 0.25f), 1e-4f)          // pas de pulsation sous 0.3
        assertEquals(1.18f, RelapseFx.buttonScale(1f, pulsePhase = 0f), 1e-4f)
        assertEquals(1.18f * 1.06f, RelapseFx.buttonScale(1f, pulsePhase = 0.5f), 1e-4f)
        assertEquals(0f, RelapseFx.shakeDp(0.5f, shakePhase = 0.25f), 1e-4f)            // pas de tremblement sous 0.6
        assertEquals(2f, RelapseFx.shakeDp(0.8f, shakePhase = 0.25f), 1e-4f)
        assertEquals(1.12f, RelapseFx.captionScale(1f), 1e-4f)
    }
}
```

`OverlayControllerTest.kt` : adapter `state()` à la nouvelle signature (`OverlayState(BlockReason.Night, …, detail = "", streak = StreakSummary(1, 1, 0), outcome = PressOutcome.Joker(1), holdMillis = 10_000, jokerMinutes = 5, relapseMinutes = 15, navBarTop = null, zone = zone)`) et le faux `Callbacks` (`onQuit`). Ajouter, avec le style de pilotage du looper déjà utilisé dans le fichier (`shadowOf(Looper.getMainLooper()).idle()` / `idleFor`) :

```kotlin
    @Test fun hideIsDeferredDuringDoneState() {
        controller.show(state()); idleMain()
        controller.holdCompleted(); idleMain()
        assertEquals(1, callbacks.holdCompleted)
        controller.hide(); idleMain()
        assertTrue(controller.isShowing)                          // gelé 1,5 s
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(OverlayController.DONE_MILLIS))
        assertFalse(controller.isShowing)
    }

    @Test fun lastRequestWinsAfterFreeze() {
        controller.show(state()); idleMain()
        controller.holdCompleted(); idleMain()
        controller.hide(); controller.show(state()); idleMain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(OverlayController.DONE_MILLIS))
        assertTrue(controller.isShowing)
    }

    @Test fun secondCompletionDuringFreezeIsIgnored() {
        controller.show(state()); idleMain()
        controller.holdCompleted(); controller.holdCompleted(); idleMain()
        assertEquals(1, callbacks.holdCompleted)
    }
```

(`callbacks` : faux qui compte `holdCompleted` et `quit`.)

- [ ] **Step 2: Lancer** `./gradlew :app:testDebugUnitTest --tests 'rehab.app.overlay.*'` — échec de compilation.

- [ ] **Step 3: `OverlayText`** (dans `OverlayState.kt`) :

```kotlin
object OverlayText {
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    /** Statique (spec §3) : [shownAtMillis] est l'instant d'apparition de l'overlay, pas « maintenant ». */
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
```

- [ ] **Step 4: `RelapseFx`** (pur, `Color` de Compose) :

```kotlin
/** DESIGN §5, tableau de l'animation relapse. `p` ∈ [0, 1] ; les phases sont intégrées image par image par BlockOverlay. */
object RelapseFx {
    private val from = Color(0xFF171513)
    private val to = Color(0xFF5A1A15)

    fun background(p: Float): Color = lerp(from, to, p.coerceIn(0f, 1f))
    fun daysShown(streak: Int, p: Float): Int = (streak * (1f - p)).roundToInt().coerceAtLeast(0)
    fun pulsePeriodSec(p: Float) = 0.9f - 0.5f * p
    fun shakePeriodSec(p: Float) = 0.14f - 0.2f * (p - 0.6f)
    /** 1 + 0.18 p, multiplié par une pulsation 1 → 1.06 → 1 au-delà de p = 0.3. */
    fun buttonScale(p: Float, pulsePhase: Float): Float {
        val base = 1f + 0.18f * p
        if (p <= 0.3f) return base
        return base * (1f + 0.06f * (0.5f - 0.5f * cos(2f * PI.toFloat() * pulsePhase)))
    }
    /** ±2 dp au-delà de p = 0.6. */
    fun shakeDp(p: Float, shakePhase: Float): Float = if (p <= 0.6f) 0f else 2f * sin(2f * PI.toFloat() * shakePhase)
    fun captionScale(p: Float) = 1f + 0.12f * p
}
```

(`lerp` = `androidx.compose.ui.graphics.lerp`. Vérifier que `background(1f)` donne exactement `0xFF5A1A15` ; sinon interpoler canal par canal avec arrondi comme `Overlay-Proto.dc.html`.)

- [ ] **Step 5: Lancer** `OverlayTextTest` et `RelapseFxTest` — PASS.

- [ ] **Step 6: `OverlayController`** — gel de l'état terminé :

```kotlin
    interface Callbacks {
        fun onQuit()
        fun onHoldCompleted()
    }

    companion object { const val DONE_MILLIS = 1500L }

    /** Horloge du Handler (uptime) : pilotable par Robolectric. Accédés uniquement sur le thread principal. */
    private var frozenUntil = 0L
    private var deferred: (() -> Unit)? = null
    private val flush = Runnable { deferred?.also { deferred = null }?.invoke() }

    /**
     * Pendant [DONE_MILLIS] après un appui complet, l'overlay reste dans son état final (bouton plein,
     * fond rouge figé) : les show()/hide() du moteur sont différés, seul le dernier est appliqué à la fin.
     */
    private fun runOrDefer(action: () -> Unit) {
        val wait = frozenUntil - android.os.SystemClock.uptimeMillis()
        if (wait > 0) {
            deferred = action
            main.removeCallbacks(flush)
            main.postDelayed(flush, wait)
        } else {
            action()
        }
    }

    fun show(newState: OverlayState) = main.post { runOrDefer { showOnMain(newState) } }
    fun hide() = main.post { runOrDefer { hideOnMain() } }

    /** Appelé par BlockOverlay à la fin de l'appui (thread principal). Ignoré pendant un gel déjà en cours. */
    fun holdCompleted() = main.post {
        if (android.os.SystemClock.uptimeMillis() < frozenUntil) return@post
        frozenUntil = android.os.SystemClock.uptimeMillis() + DONE_MILLIS
        callbacks.onHoldCompleted()
    }
```

Dans `setContent`, passer `onQuit = callbacks::onQuit`, `onHoldCompleted = ::holdCompleted`. Dans `hideOnMain`, remettre `frozenUntil = 0` et `deferred = null` (un nouvel overlay repart propre). Le repli en cas d'échec d'`addView` appelle `callbacks.onQuit()`.

- [ ] **Step 7: `HoldButton`** (rendu + geste, sans horloge) :

```kotlin
enum class HoldKind { Joker, Relapse }

@Composable
fun HoldButton(
    kind: HoldKind,
    progress: Float,
    secondsLeft: Int,
    done: Boolean,
    doneValue: String,          // "+5" / "+15"
    scale: Float,
    onPressChange: (Boolean) -> Unit,
) {
    val relapse = kind == HoldKind.Relapse
    val disc = when { done && relapse -> RehabColors.Danger; done -> RehabColors.Accent; relapse -> RehabColors.DangerDeep; else -> RehabColors.Bg }
    val track = if (relapse) RehabColors.DangerTrack else RehabColors.Line
    val gauge = if (relapse) RehabColors.Danger else RehabColors.Accent
    Box(
        Modifier.size(96.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(disc)
            .pointerInput(done) {
                if (done) return@pointerInput
                detectTapGestures(onPress = { onPressChange(true); tryAwaitRelease(); onPressChange(false) })
            }
            .semantics { contentDescription = if (relapse) "Maintenir pour un relapse" else "Maintenir pour un joker" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val w = 8.dp.toPx(); val inset = w / 2 + 4.dp.toPx()
            val tl = Offset(inset, inset); val sz = Size(size.width - 2 * inset, size.height - 2 * inset)
            if (!done) drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(w))
            if (!done && progress > 0f) drawArc(gauge, -90f, 360f * progress, false, tl, sz, style = Stroke(w))   // au repos : rien (DESIGN §3)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when {
                done -> { BigValue(doneValue, RehabColors.Bg); SubLabel("MIN", RehabColors.Bg) }
                progress > 0f -> { BigValue("$secondsLeft", if (relapse) RehabColors.DangerText else RehabColors.Text); SubLabel("SEC", if (relapse) Color(0xFFD9A9A2) else RehabColors.Muted) }
                else -> Text(if (relapse) "RELAPSE" else "JOKER", style = TextStyle(fontFamily = Chivo, fontSize = 12.sp, letterSpacing = 0.08.em, color = if (relapse) RehabColors.DangerText else RehabColors.Text))
            }
        }
    }
}
// BigValue : ChivoMono 22 sp, lineHeight 22 sp ; SubLabel : Chivo 9 sp, 0.12 em.
```

(`#D9A9A2` vient de `Overlay-Proto.dc.html` ; si l'on veut éviter une couleur hors tokens, utiliser `DangerText.copy(alpha = 0.7f)`.)

- [ ] **Step 8: `BlockOverlay`** (d'après `Overlay-Quota`, `Overlay-Nuit`, `Overlay-Relapse`, `Overlay-HorsRecord`, `Overlay-Proto`, `Etats-Bouton`) :

```kotlin
@Composable
fun BlockOverlay(state: OverlayState, nowMillis: () -> Long, onQuit: () -> Unit, onHoldCompleted: () -> Unit) {
    val shownAt = remember(state.reason, state.unlockAtMillis) { nowMillis() }
    val relapse = state.outcome is PressOutcome.Relapse
    var pressing by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var p by remember { mutableFloatStateOf(0f) }
    var pulsePhase by remember { mutableFloatStateOf(0f) }
    var shakePhase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pressing) {
        if (!pressing) { if (!done) p = 0f; return@LaunchedEffect }   // relâché : retour instantané à 0
        val start = withFrameMillis { it }
        var last = start
        while (pressing && !done) {
            val t = withFrameMillis { it }
            val dt = (t - last) / 1000f; last = t
            p = ((t - start).toFloat() / state.holdMillis).coerceIn(0f, 1f)
            if (relapse) {
                pulsePhase = (pulsePhase + dt / RelapseFx.pulsePeriodSec(p)) % 1f
                if (p > 0.6f) shakePhase = (shakePhase + dt / RelapseFx.shakePeriodSec(p)) % 1f
            }
            if (p >= 1f) { done = true; onHoldCompleted() }
        }
    }

    val fx = if (relapse) p else 0f     // joker : ni fond, ni tremblement, ni pulsation
    val holding = pressing && !done
    val bg = if (relapse && (holding || done)) RelapseFx.background(if (done) 1f else p) else RehabColors.Bg
    val secondsLeft = ceil((1f - p) * state.holdMillis / 1000f).toInt()
    val s = state.streak

    Column(
        Modifier.fillMaxSize().background(bg)
            .graphicsLayer { translationX = if (holding) RelapseFx.shakeDp(fx, shakePhase).dp.toPx() else 0f }
            .then(if (state.navBarTop == null) Modifier.navigationBarsPadding() else Modifier)
            .padding(top = 64.dp),
    ) {
        Column(Modifier.padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("REHAB · BLOCAGE", style = RehabText.caps12.copy(letterSpacing = 0.16.em))
            Text(OverlayText.title(state, shownAt), style = TextStyle(fontFamily = Chivo, fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.W500, color = RehabColors.Text))
            Text(state.detail, style = TextStyle(fontFamily = Chivo, fontSize = 14.sp, color = RehabColors.Muted))
        }
        Column(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val days = when { done && relapse -> 0; relapse -> RelapseFx.daysShown(s.current, fx); else -> s.current }
            val daysColor = if (relapse && (fx > 0.5f || done)) RehabColors.Danger else RehabColors.Text
            if (s.inRecord) {
                RecordRing(176.dp, 10.dp, RehabColors.Accent, ringAlpha = 1f - fx, overlayArc = fx) {
                    Text("RECORD", style = RehabText.ringLabel.copy(color = RehabColors.Accent.copy(alpha = 1f - fx)))
                    Spacer(Modifier.height(3.dp))
                    Text("$days", style = TextStyle(fontFamily = ChivoMono, fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.W300, color = daysColor))
                    Spacer(Modifier.height(3.dp))
                    Text(HomeText.unit(days), style = RehabText.ringUnit)
                }
            } else {
                RecordRing(176.dp, 10.dp, RehabColors.RingOff, overlayArc = fx) {
                    Text("SÉRIE", style = RehabText.ringLabel.copy(color = RehabColors.Muted))
                    Spacer(Modifier.height(3.dp))
                    Text("$days", style = TextStyle(fontFamily = ChivoMono, fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.W300, color = daysColor))
                    Spacer(Modifier.height(3.dp))
                    Text(HomeText.unit(days), style = RehabText.ringUnit)
                }
            }
            val line = when {
                done && relapse -> OverlayText.doneStreakLine(s)
                holding && relapse && fx > 0.5f -> "Tu es en train de le perdre."
                else -> OverlayText.streakLine(s)
            }
            Text(withMonoNumbers(line, RehabColors.Text), style = TextStyle(fontFamily = Chivo, fontSize = 13.sp, color = RehabColors.Muted), textAlign = TextAlign.Center, modifier = Modifier.defaultMinSize(minHeight = 20.dp))
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HoldButton(
                kind = if (relapse) HoldKind.Relapse else HoldKind.Joker,
                progress = if (done) 1f else p,
                secondsLeft = secondsLeft,
                done = done,
                doneValue = "+" + (if (relapse) state.relapseMinutes else state.jokerMinutes),
                scale = if (relapse && holding) RelapseFx.buttonScale(fx, pulsePhase) else 1f,
                onPressChange = { if (!done) pressing = it },
            )
            // Boîte de hauteur fixe (40 dp) : rien ne bouge quand le texte change (DESIGN §5.5).
            Box(Modifier.height(40.dp).widthIn(max = 300.dp).graphicsLayer { val k = if (relapse) RelapseFx.captionScale(fx) else 1f; scaleX = k; scaleY = k }, contentAlignment = Alignment.Center) {
                val color = if (relapse) RehabColors.Danger else RehabColors.Text
                val style = TextStyle(fontFamily = Chivo, fontSize = 13.sp, lineHeight = 19.sp, color = color, textAlign = TextAlign.Center,
                    fontWeight = if (relapse && fx > 0.5f) FontWeight.W700 else FontWeight.W400)
                when {
                    done -> Text(OverlayText.doneCaption(state), style = style)
                    holding -> Text(OverlayText.holdingCaption(state, secondsLeft), style = style)
                    else -> {
                        val c = OverlayText.caption(state)
                        Text(buildAnnotatedString {
                            append(c.prefix)
                            withStyle(SpanStyle(fontWeight = FontWeight.W700, color = if (relapse) RehabColors.Danger else RehabColors.Accent)) { append(c.emphasis) }
                            append(c.suffix)
                        }, style = style)
                    }
                }
            }
        }
        PrimaryButton("Quitter", onQuit, Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 16.dp))
    }
}
```

- [ ] **Step 9: Service** (`RehabAccessibilityService`) :
  - Callbacks : `override fun onQuit() { performGlobalAction(GLOBAL_ACTION_HOME) }` (DESIGN §6.2 : `BACK` peut ramener sur une cible).
  - Fabrique commune, utilisée par `apply()` et `showTestOverlay()` :

```kotlin
    private fun overlayState(decision: Decision.Block, now: Instant, navBarTop: Int?): OverlayState {
        val settings = graph.settingsRepo.get()
        val outcome = graph.unlock.preview(now)
        val detail = when {
            decision.reason == BlockReason.Night -> graph.schedule.activeNight(now)
                ?.let { n -> settings.nights[n.row.dayOfWeek]?.let { OverlayText.nightDetail(n.row.dayOfWeek, it) } } ?: ""
            outcome is PressOutcome.Relapse -> OverlayText.jokersExhausted(settings.jokersPerDay)
            else -> graph.policy.quotaStatus(now).perWindow.firstOrNull { it.exceeded }?.window?.let(OverlayText::quotaDetail)
                ?: settings.quotaWindows.firstOrNull()?.let(OverlayText::quotaDetail) ?: ""
        }
        return OverlayState(
            reason = decision.reason,
            unlockAtMillis = decision.unlockAt.toEpochMilli(),
            detail = detail,
            streak = graph.streak.summary(now),
            outcome = outcome,
            holdMillis = settings.holdDuration.toMillis(),
            jokerMinutes = settings.jokerDuration.toMinutes(),
            relapseMinutes = settings.relapseDuration.toMinutes(),
            navBarTop = navBarTop,
            zone = graph.clock.zone(),
        )
    }
```

  - Journalisation du blocage (thread moteur uniquement) :

```kotlin
    /** Dernier blocage journalisé (raison, fin), pour n'écrire qu'un Event.Block par blocage. Accédé depuis "rehab-engine" seulement. */
    private var lastBlockKey: Pair<BlockReason, Instant>? = null

    private fun logBlockOnce(decision: Decision.Block, now: Instant) {
        val key = decision.reason to decision.unlockAt
        if (key == lastBlockKey) return
        lastBlockKey = key
        // Après un redémarrage du service, ne pas réécrire un blocage déjà journalisé (tolérance d'une minute sur la fin).
        val previous = graph.eventLog.since(now.minus(Duration.ofDays(1))).lastOrNull { it is Event.Block } as Event.Block?
        if (previous != null && previous.reason == decision.reason && Duration.between(previous.until, decision.unlockAt).abs() < Duration.ofMinutes(1)) return
        graph.eventLog.append(Event.Block(now, decision.reason, decision.unlockAt))
    }
```

  Dans `apply()`, branche `Decision.Block` : `logBlockOnce(decision, now)` puis `overlay.show(overlayState(decision, now, detection.navBarTop))`. `showTestOverlay()` : `overlay.show(overlayState(Decision.Block(BlockReason.Quota, now.plusSeconds(90)), now, null))` sans journaliser (garder le masquage à 5 s et le commentaire existant).

- [ ] **Step 10: Lancer** `./gradlew test` — vert.

- [ ] **Step 11: Vérifier sur le téléphone** (`installDebug`, Debug → « Overlay de test ») : mise en page conforme à `Overlay-Quota.png` ; appui court = rien ; appui tenu = jauge et décompte ; relâcher = retour à 0 immédiat. Avec la durée d'appui à 3 s dans les Réglages, tester un relapse complet sur un vrai blocage (fond qui rougit, tremblement après 60 %, état final 1,5 s puis disparition) ; vérifier que « Quitter » ramène à l'écran d'accueil du téléphone. Remettre 10 s.

- [ ] **Step 12: Commit** — `git commit -m "feat(overlay): overlay redessiné, appui long animé, état terminé et blocages journalisés"`

---

## Task 8: Onboarding

**Files:**
- Create: `app/src/main/kotlin/rehab/app/ui/OnboardingText.kt`
- Modify: `app/src/main/kotlin/rehab/app/ui/OnboardingScreen.kt` (réécrit)
- Test: `app/src/test/kotlin/rehab/app/ui/OnboardingTextTest.kt`

**Interfaces:**
- Consumes: composants (tâche 2), `AppStatus` (existant), `AppDisplayNames`.
- Produces: `object OnboardingText { fun appValue(s: AppStatus): Pair<String, PillTone> }`

- [ ] **Step 1: Test** :

```kotlin
class OnboardingTextTest {
    @Test fun appValues() {
        assertEquals("412.0 · reconnue" to PillTone.Accent, OnboardingText.appValue(AppStatus("com.instagram.android", installed = true, version = "412.0.0.35.104", inRange = true)))
        assertEquals("413.1 · hors plage" to PillTone.Warn, OnboardingText.appValue(AppStatus("com.instagram.android", installed = true, version = "413.1.0.1", inRange = false)))
        assertEquals("non installée" to PillTone.Muted, OnboardingText.appValue(AppStatus("com.twitter.android", installed = false, version = null, inRange = false)))
    }
}
```

(Adapter le constructeur `AppStatus` à sa définition réelle dans `VersionChecker.kt`.)

- [ ] **Step 2: Lancer** — échec ; **Step 3: implémenter** (version abrégée aux deux premiers composants, comme `HomeText.outOfRangeAlert`) ; **Step 4: Lancer** — PASS.

- [ ] **Step 5: `OnboardingScreen`** : `Column(fillMaxSize, verticalScroll)` : `RehabHeader { HeaderCaption("premier lancement") }` ; `SectionLabel("Avant de commencer")` ; `SettingsGroup` :
  - « Service d'accessibilité » : valeur « Actif » (`valueColor = Accent`, pas de clic) ou « Ouvrir » (`valueColor = Warn`, `onClick = openAccessibilitySettings`) ;
  - « Optimisation batterie désactivée » : « Actif » / « Ouvrir » (`openBatterySettings`) ;
  - « Notifications (alerte règles) » : « Autorisées » / « Autoriser » (lanceur de permission existant) ;
  - une ligne par `apps` : libellé `AppDisplayNames.of(pkg)`, valeur/couleur `OnboardingText.appValue`.
  `Note("Rehab ne bloque rien tant que le service d'accessibilité n'est pas actif. Les lignes Instagram et X apparaissent après la première activation du service.")` ; `Spacer(weight(1f))` ; `PrimaryButton("Continuer", onContinue, Modifier.fillMaxWidth().padding(20.dp), enabled = accessibilityEnabled)`. Les lectures `prerequisites.*` sont faites une fois par composition dans des `val` (pas trois appels `Settings.Secure` par ligne).

- [ ] **Step 6: Lancer** `./gradlew test` — vert. **Vérifier sur le téléphone** : désactiver le service d'accessibilité, rouvrir Rehab → onboarding redessiné ; « Ouvrir » mène aux réglages ; au retour, la ligne passe à « Actif » et « Continuer » s'active.

- [ ] **Step 7: Commit** — `git commit -m "feat(app): onboarding au style des Réglages"`

---

## Clôture

- [ ] Revue finale de toute la branche (`<base>..HEAD`) par le relecteur le plus capable, contre la spec et DESIGN.md.
- [ ] `./gradlew test :app:assembleRelease` vert, `:app:installDebug` sur le Pixel, push.
- [ ] Mettre à jour `docs/manual-test-checklist.md` : ajouter les vérifications visuelles et l'overlay (Quitter → accueil du téléphone, état terminé 1,5 s, blocages dans le Journal).
