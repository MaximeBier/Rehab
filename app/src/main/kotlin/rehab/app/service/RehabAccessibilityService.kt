package rehab.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import rehab.app.RehabApp
import rehab.app.di.AppGraph
import rehab.app.overlay.OverlayController
import rehab.app.overlay.OverlayState
import rehab.app.overlay.OverlayText
import rehab.domain.model.BlockReason
import rehab.domain.model.Decision
import rehab.domain.model.Event
import rehab.domain.policy.PressOutcome
import rehab.rules.Detection
import rehab.rules.catalog.InstagramRules
import rehab.rules.SnapshotBuilder
import java.time.Duration
import java.time.Instant

/**
 * Pièce d'assemblage du pipeline : reçoit les événements d'accessibilité d'Instagram et de X,
 * choisit la fenêtre de l'app parmi toutes celles à l'écran (WindowPicker, pas seulement la fenêtre active ;
 * arbre illisible = échec fermé), construit un snapshot borné de l'arbre, détecte l'écran/la cible, applique la politique
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

    companion object {
        /**
         * Instance courante, utilisée uniquement par l'écran Debug pour proposer un overlay de
         * test quand le service est actif. `@Volatile` car écrite depuis le thread principal
         * (cycle de vie du service) et lue depuis un Composable, potentiellement sur un autre
         * thread de recomposition. Remise à `null` dans `onUnbind` : sans ça, cette référence
         * statique retiendrait le service (et tout `graph`) indéfiniment après sa fin de vie.
         */
        @Volatile var instance: RehabAccessibilityService? = null
            private set

        private val USAGE_RETENTION: Duration = Duration.ofDays(30)
        private val PURGE_INTERVAL: Duration = Duration.ofDays(1)
    }

    private lateinit var graph: AppGraph
    private lateinit var overlay: OverlayController
    private lateinit var blockJournal: BlockJournal
    private lateinit var messages: MessagesLauncher
    private val engineThread = HandlerThread("rehab-engine").apply { start() }
    private val engine = Handler(engineThread.looper)
    private val processRunnable = Runnable { process() }
    private val tickRunnable = object : Runnable {
        override fun run() { process(); if (tickerRunning) engine.postDelayed(this, 1000) }
    }
    @Volatile private var tickerRunning = false
    /**
     * Purge quotidienne de `usageLog` (spec §2.4). Auparavant appelée une seule fois, à
     * `onServiceConnected` : un service resté connecté des mois (le cas nominal — pas de raison de se
     * déconnecter) ne purgeait alors plus jamais après le premier appel. Se replanifie elle-même tant que
     * le service tourne ; `onServiceConnected` l'annule et la relance pour rester idempotent en cas de
     * reconnexion sans `onUnbind` intermédiaire.
     */
    private val purgeRunnable = object : Runnable {
        override fun run() {
            safely { graph.usageLog.purgeBefore(graph.clock.now().minus(USAGE_RETENTION)) }
            engine.postDelayed(this, PURGE_INTERVAL.toMillis())
        }
    }
    /** Packages pour lesquels un `Event.Error` de troncature a déjà été journalisé, pour ne pas le répéter
     * à chaque `process()` (jusqu'à toutes les secondes tant que le ticker tourne). Accédé uniquement depuis
     * "rehab-engine". */
    private val truncatedLogged = mutableSetOf<String>()
    /** Vrai tant que l'overlay affiché est celui de [showTestOverlay]. Accédé uniquement depuis "rehab-engine". */
    private var testOverlayShown = false
    /** Paquet vu par `windowId`, pour une fenêtre dont la racine devient illisible (WindowPicker). Thread "rehab-engine". */
    private val windowPackages = mutableMapOf<Int, String>()

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { engine.post { safely { leaveTargets() } } }
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
                // Accueil du téléphone, pas BACK : BACK peut ramener sur une cible et refaire apparaître
                // l'overlay aussitôt (DESIGN §6.2).
                override fun onQuit() { performGlobalAction(GLOBAL_ACTION_HOME) }
                // IMPORTANT 3 (revue finale) : le commit (Room) est protégé par `safely` — une exception
                // ici ne doit pas tuer le thread moteur ; `process()` a déjà son propre `safely`.
                // v0.7.1 : ouvre la liste des conversations ; ni hide(), ni journal, ni usage — l'overlay se retire
                // quand l'écran des messages est détecté, et reste si l'ouverture échoue.
                override fun onOpenMessages(packageName: String) {
                    engine.post { safely { messages.open(packageName, graph.catalog.forPackage(packageName)?.messagesUri) } }
                }
                override fun onHoldCompleted() {
                    engine.post {
                        if (testOverlayShown) return@post hideTestOverlay()
                        safely { graph.unlock.commit(graph.clock.now()) }
                        process()
                    }
                }
            },
        )
        // Même logique que `lastBlockKey` avant l'extraction (MINEUR/IMPORTANT 2, revue finale) : conservé
        // entre deux appels d'onServiceConnected() sur la même instance, pas recréé à chaque reconnexion.
        if (!::blockJournal.isInitialized) blockJournal = BlockJournal(graph.eventLog)
        messages = MessagesLauncher(this)
        // Publiée seulement une fois `graph`/`overlay` prêts : l'écran Debug lit cette instance
        // pour activer son bouton d'overlay de test, qui appelle showTestOverlay() (utilise les
        // deux). La publier plus tôt exposerait une fenêtre, même infime, où l'UI obtiendrait une
        // instance dont ces `lateinit var` ne sont pas encore initialisées.
        instance = this
        runCatching { unregisterReceiver(screenOff) }
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        graph.serviceState.connected.value = true
        engine.post {
            safely {
                graph.eventLog.append(Event.ServiceOn(graph.clock.now()))
                graph.usageTracker.recover()
                graph.versionChecker.checkAll()
            }
        }
        engine.removeCallbacks(purgeRunnable)
        engine.post(purgeRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in graph.catalog.packageNames) return
        // Regroupe les événements sur 300 ms au plus, sans repousser l'échéance à chaque nouvel événement : un Reel
        // en lecture émet des événements en continu (barre de progression) ; l'ancien « anti-rebond » repoussait
        // alors process() indéfiniment — ni détection, ni ticker, rien de compté (constaté sur le Pixel, 2026-11-21).
        if (!engine.hasCallbacks(processRunnable)) engine.postDelayed(processRunnable, 300)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        // `graph` peut ne jamais avoir été initialisé si onServiceConnected() n'a jamais tourné
        // (ex. lien/déliaison très rapprochés du service d'accessibilité système) : lire
        // `graph.serviceState` planterait alors avec UninitializedPropertyAccessException.
        if (!::graph.isInitialized) {
            instance = null
            return super.onUnbind(intent)
        }
        graph.serviceState.connected.value = false
        runCatching { unregisterReceiver(screenOff) }
        engine.removeCallbacks(purgeRunnable)
        engine.post {
            safely {
                leaveTargets()
                graph.eventLog.append(Event.ServiceOff(graph.clock.now()))
            }
        }
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        engineThread.quitSafely()
        super.onDestroy()
    }

    /**
     * Affiche un overlay de blocage factice pendant 5 s, pour vérifier son rendu sans attendre
     * une vraie nuit/un vrai quota (écran Debug). L'appui long le ferme sans rien enregistrer :
     * un joker ou un relapse de test consommerait les vrais jokers du jour, voire la série.
     */
    fun showTestOverlay() = engine.post {
        safely {
            val now = graph.clock.now()
            // Pas de journalisation : ce blocage factice n'a pas eu lieu (pas d'Event.Block).
            overlay.show(overlayState(Decision.Block(BlockReason.Quota, now.plusSeconds(90)), now, null, InstagramRules.PACKAGE))
            testOverlayShown = true
            engine.postDelayed(::hideTestOverlay, 5000)
        }
    }

    /** Ne retire que l'overlay de test : un vrai blocage apparu entre-temps reste affiché. Thread "rehab-engine". */
    private fun hideTestOverlay() {
        if (!testOverlayShown) return
        testOverlayShown = false
        overlay.hide()
    }

    /** État de l'overlay pour [decision], commun au blocage réel ([apply]) et à l'overlay de test. Thread "rehab-engine". */
    private fun overlayState(decision: Decision.Block, now: Instant, navBarTop: Int?, packageName: String): OverlayState {
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
            messagesPackage = packageName.takeIf { messages.canOpen(it, graph.catalog.forPackage(it)?.messagesUri) },
        )
    }

    // ---- moteur (thread rehab-engine) ----

    private fun process() = safely {
        val now = graph.clock.now()
        val infos = runCatching { windows }.getOrDefault(emptyList())
        val roots = HashMap<Int, AccessibilityNodeInfo?>()
        val candidates = if (infos.isEmpty()) {
            // Repli : liste des fenêtres indisponible (service tout juste connecté).
            val r = rootInActiveWindow
            roots[-1] = r
            listOf(WindowCandidate(-1, true, r?.packageName?.toString(), active = true, focused = true, area = 0))
        } else infos.map { w ->
            val isApp = w.type == AccessibilityWindowInfo.TYPE_APPLICATION
            val r = if (isApp) w.root else null
            roots[w.id] = r
            val rect = Rect().also(w::getBoundsInScreen)
            WindowCandidate(w.id, isApp, r?.packageName?.toString(), w.isActive, w.isFocused, rect.width().toLong() * rect.height())
        }
        windowPackages.keys.retainAll(candidates.map { it.windowId }.toSet())
        val picked = WindowPicker.pick(candidates, graph.catalog.packageNames, windowPackages) ?: return@safely leaveTargets()
        windowPackages[picked.windowId] = picked.packageName
        val pkg = picked.packageName
        val root = roots[picked.windowId]
        val version = graph.versionChecker.versionOf(pkg)
        // Échec fermé (v0.7.0) : fenêtre de l'app présente mais arbre illisible → racine vide, que les règles
        // « tout sauf DM » classent comme la cible de l'app. Jamais une sortie.
        val snapshot = if (root == null || root.childCount == 0) {
            SnapshotBuilder.unreadable(pkg, version, now.toEpochMilli())
        } else {
            graph.snapshotBuilder.build(AccessibilityTreeNode(root), pkg, version, now.toEpochMilli())
        }
        maybeRecheckVersion(pkg, snapshot.appVersion)

        if (snapshot.truncated) {
            // Échec fermé (spec §2.8) : un arbre amputé (SnapshotBuilder.truncated) est indiscernable de
            // « rien à bloquer » pour ScreenDetector. On le fait remonter au lieu de le laisser silencieux :
            // un Event.Error, une fois par transition (pas à chaque tick). Ne change plus la décision :
            // un DM tronqué est compté.
            if (truncatedLogged.add(pkg)) {
                graph.eventLog.append(Event.Error(now, "Snapshot tronqué pour $pkg : arbre amputé par SnapshotBuilder"))
            }
        } else {
            truncatedLogged.remove(pkg)
        }

        val detection = graph.detector.detect(snapshot)
        // La capture a toujours lieu, troncature comprise (écran Debug) : on lui passe le
        // `screenId` détecté, désormais connu à ce point de `process()`, pour un nom de fichier
        // lisible (voir CaptureNames).
        graph.capture.maybeSave(snapshot, detection.screenId)
        // Raison lue ici, sur le thread "rehab-engine", puis publiée pour l'écran Debug.
        val degradedReasonAfter = graph.degraded.reason(pkg)
        graph.detectionState.last.value = LastDetection(
            packageName = pkg,
            appVersion = snapshot.appVersion,
            target = detection.target?.value,
            screenId = detection.screenId,
            unknownScreen = detection.unknownScreen,
            degraded = degradedReasonAfter != null,
            atMillis = now.toEpochMilli(),
            degradedReason = degradedReasonAfter,
        )

        apply(detection, pkg)
    }

    /**
     * IMPORTANT 4 (revue finale) : `checkAll()` n'était appelé qu'à `onServiceConnected`. Une mise à jour
     * d'Instagram/X en arrière-plan (justement l'événement qui casse les règles) ne redémarre pas le service
     * et restait donc invisible. On compare la version vue dans le snapshot courant (toujours fraîche : lue
     * depuis `PackageManager` à chaque `process()`, voir `graph.versionChecker.versionOf`) à la dernière
     * version connue de [VersionChecker.statuses] ; un écart relance `checkAll()`, peu coûteux sur ce thread.
     */
    private fun maybeRecheckVersion(pkg: String, appVersion: String) {
        val known = graph.versionChecker.statuses.value.firstOrNull { it.packageName == pkg }?.version
        if (known != null && known != appVersion) {
            graph.versionChecker.checkAll()
        }
    }

    private fun apply(detection: Detection, packageName: String) {
        val now = graph.clock.now()
        val target = detection.target
        if (target == null) {
            graph.usageTracker.onDetected(null, now)
            overlay.hide()
            // v0.7.0 : le ticker tourne tant qu'une fenêtre de l'app est à l'écran, DM compris, pour réévaluer sans
            // dépendre des événements (sortie de DM, vidéo regardée sans toucher). Seul `leaveTargets()` l'arrête.
            startTicker()
            return
        }
        when (val decision = graph.policy.evaluate(now)) {
            Decision.Allow -> {
                graph.usageTracker.onDetected(target, now)
                overlay.hide()
            }
            is Decision.Block -> {
                // IMPORTANT 2 (revue finale) : afficher d'abord, puis clore l'usage et journaliser — une erreur Room dans
                // blockJournal.logOnce ne doit jamais sauter l'affichage du tick (ni startTicker() au premier
                // tick), sous peine de fenêtre sans blocage. lastKey n'est posé qu'après un append réussi
                // (voir BlockJournal) : un échec est donc retenté au tick suivant.
                overlay.show(overlayState(decision, now, detection.navBarBounds?.top, packageName))
                testOverlayShown = false
                // Même règle pour la clôture de l'intervalle d'usage (Room) : placée avant show(), une erreur sautait
                // l'affichage du tick (résiduel de la revue finale de la refonte).
                runCatching { graph.usageTracker.closeOpen(now) }
                    .onFailure { Log.e("Rehab", "Clôture de l'usage impossible", it) }
                runCatching { blockJournal.logOnce(decision, now) }
                    .onFailure { Log.e("Rehab", "Journalisation du blocage impossible", it) }
            }
        }
        startTicker()
    }

    private fun leaveTargets() {
        // overlay.hide() d'abord : c'est un simple post{} vers le thread principal (voir OverlayController),
        // alors que usageTracker.closeOpen() attaque Room. Si l'I/O lève, l'overlay doit déjà avoir été
        // retiré (IMPORTANT 5, revue finale) — l'ordre inverse laissait l'overlay affiché en cas d'échec Room.
        overlay.hide()
        testOverlayShown = false
        stopTicker()
        // On quitte une app catalogue (ou l'écran s'éteint) : le dernier `LastDetection` publié ne décrit
        // plus l'écran courant (écran Debug). L'alerte Accueil de hors-plage vient de `versionChecker`.
        graph.detectionState.last.value = null
        graph.usageTracker.closeOpen(graph.clock.now())
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
