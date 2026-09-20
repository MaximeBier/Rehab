package rehab.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import rehab.app.RehabApp
import rehab.app.di.AppGraph
import rehab.app.overlay.OverlayController
import rehab.app.overlay.OverlayState
import rehab.domain.model.Decision
import rehab.domain.model.Event
import rehab.rules.Detection
import java.time.Duration

/**
 * Pièce d'assemblage du pipeline : reçoit les événements d'accessibilité d'Instagram et de X,
 * construit un snapshot borné de l'arbre, détecte l'écran/la cible, applique la politique
 * (déblocage actif > nuit > quota) et pilote l'overlay de blocage.
 *
 * Tout le travail de décision tourne sur le HandlerThread "rehab-engine", jamais sur le thread
 * principal ; [OverlayController] repasse lui-même sur le thread principal pour les opérations
 * de fenêtre.
 *
 * `graph.usageTracker`, `graph.degraded` et `graph.versionChecker` ne sont accédés que depuis ce
 * thread "rehab-engine" (via [process], [onServiceConnected] et [onUnbind], qui postent
 * systématiquement sur `engine`) : c'est ce confinement à un seul thread qui rend correcte
 * l'absence de synchronisation dans ces classes. Un futur appelant côté UI/thread principal ne
 * doit pas les toucher directement — passer par un `engine.post { … }` ou par un état exposé en
 * `StateFlow` (`detectionState`, `serviceState`) comme le reste du service.
 */
class RehabAccessibilityService : AccessibilityService() {

    private lateinit var graph: AppGraph
    private lateinit var overlay: OverlayController
    private val engineThread = HandlerThread("rehab-engine").apply { start() }
    private val engine = Handler(engineThread.looper)
    private val processRunnable = Runnable { process() }
    private val tickRunnable = object : Runnable {
        override fun run() { process(); if (tickerRunning) engine.postDelayed(this, 1000) }
    }
    @Volatile private var tickerRunning = false

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { engine.post { leaveTargets() } }
    }

    override fun onServiceConnected() {
        // Le système peut rappeler onServiceConnected() sur la même instance sans onUnbind()
        // intermédiaire (observé sur certains OEM après un crash du service d'accessibilité
        // système) : on ne suppose pas un appel unique, on rend l'initialisation idempotente.
        graph = (application as RehabApp).graph
        if (::overlay.isInitialized) overlay.hide()
        overlay = OverlayController(
            this,
            graph.clock,
            object : OverlayController.Callbacks {
                override fun onBack() { performGlobalAction(GLOBAL_ACTION_BACK) }
                override fun onHoldCompleted() { engine.post { graph.unlock.commit(graph.clock.now()); process() } }
            },
        )
        runCatching { unregisterReceiver(screenOff) }
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        graph.serviceState.connected.value = true
        engine.post {
            safely {
                graph.eventLog.append(Event.ServiceOn(graph.clock.now()))
                graph.usageTracker.recover()
                graph.usageLog.purgeBefore(graph.clock.now().minus(Duration.ofDays(30)))
                graph.versionChecker.checkAll()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in graph.catalog.packageNames) return
        engine.removeCallbacks(processRunnable)
        engine.postDelayed(processRunnable, 300)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        graph.serviceState.connected.value = false
        runCatching { unregisterReceiver(screenOff) }
        engine.post {
            safely {
                leaveTargets()
                graph.eventLog.append(Event.ServiceOff(graph.clock.now()))
            }
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        engineThread.quitSafely()
        super.onDestroy()
    }

    // ---- moteur (thread rehab-engine) ----

    private fun process() = safely {
        val now = graph.clock.now()
        val root = rootInActiveWindow ?: return@safely leaveTargets()
        val pkg = root.packageName?.toString() ?: return@safely leaveTargets()
        if (pkg !in graph.catalog.packageNames) return@safely leaveTargets()

        val snapshot = graph.snapshotBuilder.build(
            AccessibilityTreeNode(root),
            pkg,
            graph.versionChecker.versionOf(pkg),
            now.toEpochMilli(),
        )
        graph.capture.maybeSave(snapshot)

        val degraded = graph.degraded.isDegraded(pkg)
        val detection = graph.detector.detect(snapshot, degraded)
        graph.degraded.onDetection(pkg, detection.unknownScreen, detection.homeTabSelected, now)
        graph.detectionState.last.value = LastDetection(
            pkg,
            snapshot.appVersion,
            detection.target?.value,
            detection.screenId,
            detection.unknownScreen,
            degraded,
            now.toEpochMilli(),
        )

        apply(detection)
    }

    private fun apply(detection: Detection) {
        val now = graph.clock.now()
        val target = detection.target
        if (target == null) {
            graph.usageTracker.onDetected(null, now)
            overlay.hide()
            stopTicker()
            return
        }
        when (val decision = graph.policy.evaluate(now)) {
            Decision.Allow -> {
                graph.usageTracker.onDetected(target, now)
                overlay.hide()
            }
            is Decision.Block -> {
                graph.usageTracker.closeOpen(now)
                val settings = graph.settingsRepo.get()
                overlay.show(
                    OverlayState(
                        reason = decision.reason,
                        unlockAtMillis = decision.unlockAt.toEpochMilli(),
                        streak = graph.streak.current(now),
                        best = graph.streak.best(now),
                        outcome = graph.unlock.preview(now),
                        holdMillis = settings.holdDuration.toMillis(),
                        navBarTop = detection.navBarBounds?.top,
                        zone = graph.clock.zone(),
                        jokerMinutes = settings.jokerDuration.toMinutes(),
                    ),
                )
            }
        }
        startTicker()
    }

    private fun leaveTargets() {
        graph.usageTracker.closeOpen(graph.clock.now())
        overlay.hide()
        stopTicker()
    }

    private fun startTicker() {
        if (tickerRunning) return
        tickerRunning = true
        engine.postDelayed(tickRunnable, 1000)
    }

    private fun stopTicker() {
        tickerRunning = false
        engine.removeCallbacks(tickRunnable)
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e("Rehab", "Erreur moteur", e)
            runCatching { graph.eventLog.append(Event.Error(graph.clock.now(), e.toString().take(500))) }
        }
    }
}
