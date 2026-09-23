package rehab.app.di

import android.content.Context
import rehab.app.data.PrefsSettingsRepo
import rehab.app.data.RedirectPrefs
import rehab.app.data.RoomEventLog
import rehab.app.data.RoomStreakRecordRepo
import rehab.app.data.RoomUsageLog
import rehab.app.data.StatsPrefs
import rehab.app.data.db.RehabDatabase
import rehab.app.stats.AndroidUsageHistory
import rehab.app.stats.UsageHistory
import rehab.app.service.AndroidRulesNotifier
import rehab.app.service.CaptureCoordinator
import rehab.app.service.DetectionState
import rehab.app.service.PackageManagerVersions
import rehab.app.service.ServiceState
import rehab.app.service.VersionChecker
import rehab.app.time.SystemClock
import rehab.app.ui.Prerequisites
import rehab.domain.degraded.DegradedModeTracker
import rehab.domain.policy.PolicyEngine
import rehab.domain.policy.Schedule
import rehab.domain.policy.SettingsGuard
import rehab.domain.policy.SlidingQuota
import rehab.domain.policy.Streak
import rehab.domain.policy.UnlockPolicy
import rehab.domain.time.Clock
import rehab.domain.usage.UsageTracker
import rehab.rules.DefaultCatalog
import rehab.rules.ScreenDetector
import rehab.rules.SnapshotBuilder
import java.io.File

class AppGraph(context: Context) {
    val clock: Clock = SystemClock()
    val db = RehabDatabase.create(context)
    val usageLog = RoomUsageLog(db.usageIntervals())
    val eventLog = RoomEventLog(db.events())
    val streakRecord = RoomStreakRecordRepo(db.streakRecord(), clock)
    val settingsRepo = PrefsSettingsRepo(context.getSharedPreferences("rehab_settings", Context.MODE_PRIVATE))
    // Bascule au blocage quota (v0.3.0) : réglage app, hors `Settings` du domaine (voir RedirectPrefs).
    val redirectPrefs = RedirectPrefs(context.getSharedPreferences("rehab_redirect", Context.MODE_PRIVATE))
    // Onglet Stats (v0.4.0) : saisie manuelle « avant Rehab » et lecture de l'historique Android.
    val statsPrefs = StatsPrefs(context.getSharedPreferences("rehab_stats", Context.MODE_PRIVATE))
    val usageHistory: UsageHistory = AndroidUsageHistory(context)

    val schedule = Schedule({ settingsRepo.get().nights }, clock.zone())
    val streak = Streak(schedule, eventLog, streakRecord)
    val unlock = UnlockPolicy(settingsRepo, schedule, eventLog, streak)
    val policy = PolicyEngine(settingsRepo, schedule, SlidingQuota(), unlock, usageLog)
    val settingsGuard = SettingsGuard(schedule, policy)
    val usageTracker = UsageTracker(usageLog)
    val degraded = DegradedModeTracker()

    val catalog = DefaultCatalog.create()
    val detector = ScreenDetector(catalog)
    val snapshotBuilder = SnapshotBuilder()
    val capture = CaptureCoordinator(File(context.filesDir, "captures"), clock.zone())
    val detectionState = DetectionState()
    // Voir la doc de ServiceState : évite le bandeau "Rehab est inactif" affiché à tort au tout
    // début du processus, avant la première connexion (ou reconnexion) du service.
    val serviceState = ServiceState(Prerequisites(context).accessibilityEnabled())

    val notifier = AndroidRulesNotifier(context)
    val versionChecker = VersionChecker(PackageManagerVersions(context.packageManager), catalog, degraded, eventLog, notifier, clock)
}
