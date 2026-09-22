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
import rehab.app.overlay.OverlayText
import rehab.domain.model.BlockReason
import rehab.domain.model.Decision
import rehab.domain.model.Event
import rehab.domain.policy.PressOutcome
import rehab.rules.Detection
import java.time.Duration
import java.time.Instant

/**
 * Décide si le compteur d'écran inconnu ([rehab.domain.degraded.DegradedModeTracker.onDetection])
 * doit s'armer pour ce tick.
 *
 * Les `knownScreens` d'Instagram (voir `InstagramRules`) ne couvrent que les écrans avec barre
 * d'onglets (`search`, `profile`, `dm`) : toute vue plein écran sans barre d'onglets (stories,
 * détail de publication, commentaires, réglages, caméra) est donc "inconnue" au sens de
 * [Detection.unknownScreen] alors qu'il s'agit d'un usage parfaitement ordinaire. Regarder des
 * stories 30 s d'affilée ne doit pas faire basculer l'app en `degradedByUnknown` — un état qui ne
 * se vide jamais et déclencherait ensuite la règle `SUGGESTED` (`degradedFallback = true`) sur
 * tout l'onglet Accueil, fil d'abonnements compris.
 *
 * On n'arme donc le compteur sur un écran inconnu que lorsque la barre de navigation est présente
 * ([Detection.navBarBounds] non nul) : c'est le signal que la structure de l'app a changé sous nos
 * règles (spec §2.8), pas que l'utilisateur est simplement sur une vue plein écran connue pour ne
 * pas en avoir.
 *
 * La troncature ([rehab.rules.Snapshot.truncated]) arme le compteur indépendamment de cette garde :
 * une troncature sévère peut justement faire disparaître la barre de navigation de l'arbre capturé
 * (nœuds coupés avant `maxDepth`/`maxNodes`), et doit rester fail-closed (voir [process]).
 */
internal fun shouldArmUnknownScreen(detection: Detection, truncated: Boolean): Boolean =
    truncated || (detection.unknownScreen && detection.navBarBounds != null)

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

    /** Dernier blocage journalisé (raison, fin), pour n'écrire qu'un Event.Block par blocage. Accédé depuis "rehab-engine" seulement. */
    private var lastBlockKey: Pair<BlockReason, Instant>? = null

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
                override fun onHoldCompleted() { engine.post { graph.unlock.commit(graph.clock.now()); process() } }
            },
        )
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
        engine.removeCallbacks(processRunnable)
        engine.postDelayed(processRunnable, 300)
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
     * une vraie nuit/un vrai quota (écran Debug). L'appui long dessus déclenche un vrai
     * `commit()` (joker ou relapse) : c'est le même chemin que l'overlay réel, volontairement.
     */
    fun showTestOverlay() = engine.post {
        val now = graph.clock.now()
        // Pas de journalisation : ce blocage factice n'a pas eu lieu (pas d'Event.Block).
        overlay.show(overlayState(Decision.Block(BlockReason.Quota, now.plusSeconds(90)), now, null))
        engine.postDelayed({ overlay.hide() }, 5000)
    }

    /** État de l'overlay pour [decision], commun au blocage réel ([apply]) et à l'overlay de test. Thread "rehab-engine". */
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

    /** Écrit un seul Event.Block par blocage, même si l'overlay est réaffiché à chaque tick. Thread "rehab-engine". */
    private fun logBlockOnce(decision: Decision.Block, now: Instant) {
        val key = decision.reason to decision.unlockAt
        if (key == lastBlockKey) return
        lastBlockKey = key
        // Après un redémarrage du service, ne pas réécrire un blocage déjà journalisé (tolérance d'une minute sur la fin).
        val previous = graph.eventLog.since(now.minus(Duration.ofDays(1))).filterIsInstance<Event.Block>().lastOrNull()
        if (previous != null && previous.reason == decision.reason &&
            Duration.between(previous.until, decision.unlockAt).abs() < Duration.ofMinutes(1)
        ) return
        graph.eventLog.append(Event.Block(now, decision.reason, decision.unlockAt))
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
        maybeRecheckVersion(pkg, snapshot.appVersion)

        if (snapshot.truncated) {
            // Échec fermé (spec §2.8) : un arbre amputé (SnapshotBuilder.truncated) est indiscernable de
            // « rien à bloquer » pour ScreenDetector. On le fait remonter au lieu de le laisser silencieux :
            // un Event.Error (une fois par transition, pas à chaque tick) et l'armement du compteur d'écran
            // inconnu, qui basculera en mode dégradé au bout de 30 s comme un vrai écran non reconnu.
            if (truncatedLogged.add(pkg)) {
                graph.eventLog.append(Event.Error(now, "Snapshot tronqué pour $pkg : arbre amputé par SnapshotBuilder"))
            }
        } else {
            truncatedLogged.remove(pkg)
        }

        val degradedReasonBefore = graph.degraded.reason(pkg)
        val degraded = degradedReasonBefore != null
        val detection = graph.detector.detect(snapshot, degraded)
        // La capture a toujours lieu, troncature comprise (écran Debug) : on lui passe le
        // `screenId` détecté, désormais connu à ce point de `process()`, pour un nom de fichier
        // lisible (voir CaptureNames).
        graph.capture.maybeSave(snapshot, detection.screenId)
        graph.degraded.onDetection(pkg, shouldArmUnknownScreen(detection, snapshot.truncated), now)
        // Relu après onDetection() : le seuil de 30s peut faire basculer en dégradé pendant cet
        // appel ; on publie l'état à jour, toujours calculé ici sur le thread "rehab-engine".
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

        apply(detection)
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
                logBlockOnce(decision, now)
                overlay.show(overlayState(decision, now, detection.navBarBounds?.top))
            }
        }
        startTicker()
    }

    private fun leaveTargets() {
        // overlay.hide() d'abord : c'est un simple post{} vers le thread principal (voir OverlayController),
        // alors que usageTracker.closeOpen() attaque Room. Si l'I/O lève, l'overlay doit déjà avoir été
        // retiré (IMPORTANT 5, revue finale) — l'ordre inverse laissait l'overlay affiché en cas d'échec Room.
        overlay.hide()
        graph.usageTracker.closeOpen(graph.clock.now())
        stopTicker()
        // On quitte une app catalogue (ou l'écran s'éteint) : le dernier `LastDetection` publié
        // ne décrit plus l'état courant. Sans ce reset, une alerte "mode dégradé" resterait
        // affichée indéfiniment côté UI (RehabViewModel) après que l'utilisateur a quitté
        // Instagram/X, alors qu'elle ne concerne plus rien de courant.
        graph.detectionState.last.value = null
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
