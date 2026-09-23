package rehab.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rehab.app.di.AppGraph
import rehab.app.service.AppStatus
import rehab.app.service.LastDetection
import rehab.app.service.RedirectAttempt
import rehab.app.stats.StatsMath
import rehab.app.stats.UsageBucket
import rehab.rules.RedirectTab
import rehab.rules.catalog.InstagramRules
import rehab.rules.catalog.TwitterRules
import rehab.domain.model.Settings
import rehab.domain.model.UsageInterval
import rehab.domain.policy.GuardResult
import rehab.domain.policy.StreakSummary
import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

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
    val decisionSummary: String = "—",
    val nowMillis: Long = 0,
)

/** Une capture de structure sur disque (écran Debug), avec son nom et sa taille déjà prêts pour l'affichage. */
data class CaptureFile(val file: File, val name: String, val sizeBytes: Long)

enum class Tab(val label: String) { Accueil("Accueil"), Reglages("Réglages"), Journal("Journal"), Stats("Stats"), Debug("Debug") }

/**
 * Le pipeline de détection (`RehabAccessibilityService`) confine `graph.usageTracker`,
 * `graph.degraded` et `graph.versionChecker.checkAll()` à son thread "rehab-engine" : ce sont des
 * classes sans synchronisation, correctes uniquement parce qu'un seul thread les touche. Ce
 * ViewModel ne les appelle donc jamais directement. Il ne lit que des vues publiées en
 * `StateFlow` par ce thread — `serviceState.connected`, `detectionState.last` (dont le champ
 * `degradedReason`, calculé sur le thread moteur) et `versionChecker.statuses` (dernier résultat
 * de `checkAll()`, également publié par le thread moteur) — `StateFlow.value` étant sûr à lire
 * depuis n'importe quel thread. Les accès base de données (streak, quota, settings) passent par
 * `Dispatchers.IO` car les adaptateurs Room sont bloquants et la base est ouverte sans
 * `allowMainThreadQueries`.
 *
 * Le rafraîchissement périodique n'est pas déclenché ici : une boucle `while(true)` dans
 * `init` tournerait tant que le `ViewModel` existe, y compris écran éteint ou app en arrière-plan
 * (une lecture Room par seconde en continu). C'est l'UI (voir `RehabApp` dans `MainActivity.kt`)
 * qui pilote l'appel à [refreshNow] sur un `repeatOnLifecycle(STARTED)`, donc uniquement quand
 * l'écran Accueil est visible.
 */
class RehabViewModel(private val graph: AppGraph) : ViewModel() {
    private val _home = MutableStateFlow(HomeUiState())
    val home: StateFlow<HomeUiState> = _home

    fun refreshNow() { viewModelScope.launch { _home.value = withContext(Dispatchers.IO) { compute() } } }

    /** État initial de l'écran Réglages : réglages courants + verrous actifs, calculés hors thread principal. */
    data class SettingsScreenState(
        val settings: Settings,
        val zone: ZoneId,
        val locks: SettingsLocks,
    )

    /**
     * Verrous d'édition (nuit active, quota en cours), séparés des réglages pour pouvoir être
     * rafraîchis périodiquement pendant que l'écran Réglages est ouvert sans jamais toucher à la
     * saisie en cours de l'utilisateur (voir [loadLocks] et son usage dans `SettingsScreen`).
     */
    data class SettingsLocks(
        val now: Instant,
        val lockedNightRow: DayOfWeek?,
        val lockedNightEnd: Instant?,
        val quotaUnlockAt: Instant?,
        val lockedWindows: Set<Duration>,
    )

    suspend fun loadSettingsScreen(): SettingsScreenState = withContext(Dispatchers.IO) {
        SettingsScreenState(
            settings = graph.settingsRepo.get(),
            zone = graph.clock.zone(),
            locks = currentLocks(),
        )
    }

    /**
     * Recalcule uniquement les verrous, avec un `now` frais. À appeler périodiquement pendant que
     * l'écran Réglages est visible : contrairement à [loadSettingsScreen], ne touche jamais aux
     * réglages affichés, donc ne peut pas écraser une saisie en cours.
     */
    suspend fun loadLocks(): SettingsLocks = withContext(Dispatchers.IO) { currentLocks() }

    private fun currentLocks(): SettingsLocks {
        val now = graph.clock.now()
        val activeNight = graph.schedule.activeNight(now)
        val status = graph.policy.quotaStatus(now)
        return SettingsLocks(
            now = now,
            lockedNightRow = activeNight?.row?.dayOfWeek,
            lockedNightEnd = activeNight?.end,
            quotaUnlockAt = status.unlockAt,
            // Même règle que SettingsGuard.checkQuota : seules les fenêtres en dépassement sont figées.
            lockedWindows = status.perWindow.filter { it.exceeded }.map { it.window.duration }.toSet(),
        )
    }

    /** Valide puis, si accepté, persiste les réglages proposés. Le `current` implicite de `SettingsGuard` reste les réglages persistés. */
    suspend fun saveSettings(proposed: Settings): GuardResult = withContext(Dispatchers.IO) {
        val result = graph.settingsGuard.validate(proposed, graph.clock.now())
        if (result is GuardResult.Accepted) graph.settingsRepo.set(proposed)
        result
    }

    /**
     * Onglet de repli par app (package → onglet, `null` = désactivée) pour « Bascule au blocage », dans
     * l'ordre de [SettingsText.redirectApps]. SharedPreferences : lu hors thread principal comme le reste.
     */
    suspend fun loadRedirects(): Map<String, RedirectTab?> = withContext(Dispatchers.IO) {
        SettingsText.redirectApps.associate { (pkg, _) -> pkg to graph.redirectPrefs.get(pkg) }
    }

    /** Aucun verrou : désactiver la bascule ramène l'overlay, ce réglage ne desserre rien (spec v0.3.0). */
    suspend fun saveRedirect(packageName: String, tab: RedirectTab?) = withContext(Dispatchers.IO) {
        graph.redirectPrefs.set(packageName, tab)
    }

    suspend fun loadJournal(): List<JournalDay> = withContext(Dispatchers.IO) {
        val now = graph.clock.now()
        val from = now.minus(Duration.ofDays(30))
        val rows = JournalText.rows(
            graph.eventLog.since(from), graph.usageLog.intervalsSince(from),
            graph.settingsRepo.get().jokersPerDay, graph.schedule::dayOf, now, graph.clock.zone(),
        )
        JournalText.days(rows, graph.schedule::dayOf, graph.schedule.dayOf(now))
    }

    // ---- écran Stats (v0.4.0) ----

    /** Apps couvertes par Stats/« Avant Rehab », dans l'ordre d'affichage : package, libellé, cibles Rehab mesurées. */
    private val statsApps = listOf(
        Triple(InstagramRules.PACKAGE, "Instagram", listOf(InstagramRules.REELS, InstagramRules.SUGGESTED)),
        Triple(TwitterRules.PACKAGE, "X", listOf(TwitterRules.HOME)),
    )

    data class StatsUiState(val hasPermission: Boolean, val apps: List<StatCard>, val total: StatCard)

    /** Valeur « avant Rehab » d'une app (écran Réglages) : saisie manuelle + moyenne Android sur les 28 jours précédant l'installation. */
    data class StatsBeforeRow(val manual: Duration?, val android: Duration?)

    private fun UsageInterval.toBucket(now: Instant): UsageBucket {
        val effectiveEnd = if (open) now else end
        return UsageBucket(start, effectiveEnd, Duration.between(start, effectiveEnd).coerceAtLeast(Duration.ZERO))
    }

    /**
     * Construit l'état de l'écran Stats (spec §Sources) : « avant » = 28 jours précédant
     * l'installation (historique Android ou saisie manuelle), « maintenant » = 7 derniers jours
     * (historique Android, app entière), « dont fil et Reels » = mesure Rehab sur les mêmes 7
     * jours. Toute la lecture (Room, `UsageStatsManager`) est ici, hors thread principal.
     */
    suspend fun loadStats(): StatsUiState = withContext(Dispatchers.IO) {
        val now = graph.clock.now()
        val installedAt = graph.streakRecord.installedAt()
        val hasPermission = graph.usageHistory.hasPermission()
        val beforeFrom = installedAt.minus(Duration.ofDays(28))
        val nowFrom = now.minus(Duration.ofDays(7))
        val rehabIntervals = graph.usageLog.intervalsSince(nowFrom)

        data class Raw(val label: String, val before: Duration?, val beforeSource: String?, val now: Duration?, val rehab: Duration)

        val raws = statsApps.map { (pkg, label, targets) ->
            val manual = graph.statsPrefs.get(pkg)?.let { Duration.ofMinutes(it.toLong()) }
            val androidBefore = if (hasPermission) StatsMath.averagePerDay(graph.usageHistory.query(pkg, beforeFrom, installedAt), 28) else null
            val androidNow = if (hasPermission) StatsMath.averagePerDay(graph.usageHistory.query(pkg, nowFrom, now), 7) else null
            val before = StatsMath.effectiveBefore(manual, androidBefore)
            val beforeSource = if (manual != null) "saisi" else if (androidBefore != null) "Android" else null
            val rehabBuckets = rehabIntervals.filter { it.target in targets }.map { it.toBucket(now) }
            val rehab = StatsMath.averagePerDay(rehabBuckets, 7) ?: Duration.ZERO
            Raw(label, before, beforeSource, androidNow, rehab)
        }

        val cards = raws.map { StatsText.card(it.label, it.before, it.beforeSource, it.now, it.rehab, hasPermission) }
        fun sumOrNull(values: List<Duration?>): Duration? =
            if (values.all { it != null }) values.filterNotNull().fold(Duration.ZERO, Duration::plus) else null
        val totalBefore = sumOrNull(raws.map { it.before })
        val totalNow = sumOrNull(raws.map { it.now })
        val totalRehab = raws.fold(Duration.ZERO) { acc, r -> acc + r.rehab }
        val total = StatsText.card("Total", totalBefore, null, totalNow, totalRehab, hasPermission)

        StatsUiState(hasPermission, cards, total)
    }

    /** État initial de la section « Avant Rehab » des Réglages (v0.4.0) : saisie manuelle + valeur Android par app. */
    suspend fun loadStatsBefore(): Map<String, StatsBeforeRow> = withContext(Dispatchers.IO) {
        val installedAt = graph.streakRecord.installedAt()
        val from = installedAt.minus(Duration.ofDays(28))
        val hasPermission = graph.usageHistory.hasPermission()
        statsApps.associate { (pkg, _, _) ->
            val manual = graph.statsPrefs.get(pkg)?.let { Duration.ofMinutes(it.toLong()) }
            val android = if (hasPermission) StatsMath.averagePerDay(graph.usageHistory.query(pkg, from, installedAt), 28) else null
            pkg to StatsBeforeRow(manual, android)
        }
    }

    /** Aucun verrou : ce réglage ne desserre rien (même raisonnement que `saveRedirect`). `null` = revenir à la valeur Android. */
    suspend fun saveStatsBefore(packageName: String, minutesPerDay: Int?) = withContext(Dispatchers.IO) {
        graph.statsPrefs.set(packageName, minutesPerDay)
    }

    // ---- écran Debug ----
    // `graph` reste privé : l'écran Debug ne reçoit que ce ViewModel et ces expositions dédiées,
    // jamais `graph` lui-même (voir le commentaire de tête sur le confinement au thread
    // "rehab-engine"). `detectionLast`/`capturePendingAt`/`captureLastFile` sont des StateFlow
    // publiés par ce thread ou par `CaptureCoordinator` (simple état en mémoire, pas Room) :
    // sûrs à lire depuis le thread principal via `.collectAsState()`.
    val detectionLast: StateFlow<LastDetection?> get() = graph.detectionState.last
    val redirectLast: StateFlow<RedirectAttempt?> get() = graph.detectionState.redirect
    val capturePendingAt: StateFlow<Long?> get() = graph.capture.pendingAt
    val captureLastFile: StateFlow<File?> get() = graph.capture.lastFile

    fun zone(): ZoneId = graph.clock.zone()

    /** Demande une capture de structure dans [delayMillis] ms, sur l'horloge du graphe (jamais `System.currentTimeMillis()`). */
    fun requestCapture(delayMillis: Long) { graph.capture.request(delayMillis, graph.clock.now().toEpochMilli()) }

    /** Liste les captures enregistrées sur disque : I/O fichier, donc hors thread principal. */
    suspend fun captures(): List<CaptureFile> = withContext(Dispatchers.IO) {
        graph.capture.list().map { CaptureFile(it, it.name, it.length()) }
    }

    /**
     * Dernier résultat de `versionChecker.checkAll()` (écran d'onboarding). `checkAll()` lui-même
     * n'est appelé que par le thread "rehab-engine" (voir `onServiceConnected`) ; ce ViewModel se
     * contente de lire `.value`, publié à chaque connexion du service. Liste vide tant que le
     * service d'accessibilité n'a jamais tourné (avant le tout premier `onServiceConnected`) :
     * l'onboarding n'affiche alors aucune ligne Instagram/X, ce qui est attendu — ces lignes
     * n'apparaissent qu'après une première connexion du service.
     */
    val appStatuses: StateFlow<List<AppStatus>> get() = graph.versionChecker.statuses

    private fun compute(): HomeUiState {
        val now = graph.clock.now()
        val zone = graph.clock.zone()
        val settings = graph.settingsRepo.get()
        val decision = graph.policy.evaluate(now)
        val unlock = graph.unlock.activeUnlock(now)
        val serviceConnected = graph.serviceState.connected.value
        val quota = graph.policy.quotaStatus(now)
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
            gauges = quota.perWindow.map(HomeText::gauge),
            night = HomeText.night(graph.schedule.nextNight(now), now, zone),
            serviceConnected = serviceConnected,
            decisionSummary = DebugText.decision(decision, unlock, quota.perWindow, zone),
            nowMillis = now.toEpochMilli(),
        )
    }
}
