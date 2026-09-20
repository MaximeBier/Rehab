package rehab.app.di

import android.content.Context
import rehab.app.data.PrefsSettingsRepo
import rehab.app.data.RoomEventLog
import rehab.app.data.RoomStreakRecordRepo
import rehab.app.data.RoomUsageLog
import rehab.app.data.db.RehabDatabase
import rehab.app.service.AndroidRulesNotifier
import rehab.app.service.CaptureCoordinator
import rehab.app.service.DetectionState
import rehab.app.service.PackageManagerVersions
import rehab.app.service.ServiceState
import rehab.app.service.VersionChecker
import rehab.app.time.SystemClock
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
    val capture = CaptureCoordinator(File(context.filesDir, "captures"))
    val detectionState = DetectionState()
    val serviceState = ServiceState()

    val notifier = AndroidRulesNotifier(context)
    val versionChecker = VersionChecker(PackageManagerVersions(context.packageManager), catalog, degraded, eventLog, notifier, clock)
}
