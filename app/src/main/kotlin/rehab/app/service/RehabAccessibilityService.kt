package rehab.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
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
import rehab.rules.Bounds
import rehab.rules.Detection
import rehab.rules.Snapshot
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
        /** Durée du tap de bascule (spec v0.3.0). */
        private const val TAP_MILLIS = 50L
        /** Réévaluation après une bascule : juste après le délai de 1,5 s de [RedirectPolicy]. */
        private const val REDIRECT_CHECK_MILLIS = 1600L
    }

    private lateinit var graph: AppGraph
    private lateinit var overlay: OverlayController
    private lateinit var blockJournal: BlockJournal
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

    /**
     * Bascule au blocage quota (v0.3.0) : état des tentatives par package. Accédée uniquement depuis
     * "rehab-engine" (voir [redirectInstead]) ; l'écran Debug lit `graph.detectionState.redirect`.
     * Conservée entre deux onServiceConnected() sur la même instance, comme [blockJournal].
     */
    private val redirectPolicy = RedirectPolicy()
    /** Réévalue la cible juste après le délai de [RedirectPolicy] sans attendre le tick suivant. */
    private val redirectCheckRunnable = Runnable { process() }
    /**
     * Bas de l'overlay demandé en dernier via [showOverlay] (`navBarTop`, ou `Int.MAX_VALUE` pour un overlay
     * plein écran), `null` après [hideOverlay]. Passé à [RedirectPolicy] pour ne jamais envoyer le toucher de
     * bascule sur l'overlay lui-même (revue v0.3.0, fix 2). Thread "rehab-engine" uniquement.
     */
    private var overlayBottomPx: Int? = null

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
                override fun onHoldCompleted() { engine.post { safely { graph.unlock.commit(graph.clock.now()) }; process() } }
            },
        )
        // Même logique que `lastBlockKey` avant l'extraction (MINEUR/IMPORTANT 2, revue finale) : conservé
        // entre deux appels d'onServiceConnected() sur la même instance, pas recréé à chaque reconnexion.
        if (!::blockJournal.isInitialized) blockJournal = BlockJournal(graph.eventLog)
        // Nouveau contrôleur, donc aucun overlay affiché : [overlayBottomPx] est confiné au thread moteur.
        engine.post { overlayBottomPx = null }
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
        showOverlay(overlayState(Decision.Block(BlockReason.Quota, now.plusSeconds(90)), now, null))
        engine.postDelayed({ hideOverlay() }, 5000)
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

        apply(detection, snapshot)
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

    private fun apply(detection: Detection, snapshot: Snapshot) {
        val now = graph.clock.now()
        val target = detection.target
        val pkg = snapshot.packageName
        if (target == null) {
            redirectPolicy.reset(pkg)
            graph.detectionState.redirect.value = redirectPolicy.lastAttempt
            graph.usageTracker.onDetected(null, now)
            hideOverlay()
            stopTicker()
            return
        }
        when (val decision = graph.policy.evaluate(now)) {
            Decision.Allow -> {
                redirectPolicy.reset(pkg)
                graph.detectionState.redirect.value = redirectPolicy.lastAttempt
                graph.usageTracker.onDetected(target, now)
                hideOverlay()
            }
            is Decision.Block -> {
                // IMPORTANT 2 (revue finale) : afficher d'abord, puis clore l'usage et journaliser — une erreur Room dans
                // blockJournal.logOnce ne doit jamais sauter l'affichage du tick (ni startTicker() au premier
                // tick), sous peine de fenêtre sans blocage. lastKey n'est posé qu'après un append réussi
                // (voir BlockJournal) : un échec est donc retenté au tick suivant.
                // v0.3.0 : au blocage quota, on tente d'abord la bascule vers l'onglet de repli ; l'overlay n'est
                // sauté que si un toucher vient d'être envoyé ou qu'une tentative est en cours (< 1,5 s).
                if (!redirectInstead(decision, snapshot, now)) {
                    showOverlay(overlayState(decision, now, detection.navBarBounds?.top))
                }
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

    /**
     * Bascule au blocage (v0.3.0, spec `2026-09-23-v0.3-bascule-dm-design.md`). Renvoie `true` quand
     * l'overlay ne doit pas être affiché à ce tick : toucher envoyé sur l'onglet de repli, ou tentative
     * en cours. Échec fermé : toute exception (prefs, parcours du snapshot, geste) renvoie `false`, donc
     * l'overlay s'affiche, et marque la tentative en échec pour ne pas retoucher en boucle.
     * Thread "rehab-engine".
     */
    private fun redirectInstead(decision: Decision.Block, snapshot: Snapshot, now: Instant): Boolean {
        val pkg = snapshot.packageName
        val skipOverlay = try {
            val action = redirectPolicy.decide(
                pkg, decision, targetPresent = true,
                tab = graph.redirectPrefs.get(pkg),
                boundsOf = { graph.detector.redirectBounds(snapshot, it) },
                nowMillis = now.toEpochMilli(),
                overlayBottomPx = overlayBottomPx,
            )
            when (action) {
                is RedirectPolicy.Action.Redirect -> {
                    val sent = tap(pkg, action.bounds)
                    if (sent) {
                        engine.removeCallbacks(redirectCheckRunnable)
                        engine.postDelayed(redirectCheckRunnable, REDIRECT_CHECK_MILLIS)
                    } else {
                        redirectPolicy.markFailed(pkg)
                    }
                    sent
                }
                RedirectPolicy.Action.Wait -> true
                RedirectPolicy.Action.ShowOverlay, RedirectPolicy.Action.None -> false
            }
        } catch (e: Exception) {
            Log.e("Rehab", "Bascule au blocage impossible : overlay", e)
            runCatching { redirectPolicy.markFailed(pkg) }
            false
        }
        graph.detectionState.redirect.value = redirectPolicy.lastAttempt
        return skipOverlay
    }

    /**
     * Tap de 50 ms au centre de [b] via `dispatchGesture` (les onglets Compose de X ne sont pas `clickable`,
     * un seul mécanisme pour les deux apps). Les rappels arrivent sur [engine] : une annulation marque la
     * tentative en échec et réévalue aussitôt, ce qui affiche l'overlay.
     */
    private fun tap(pkg: String, b: Bounds): Boolean {
        val path = Path().apply { moveTo((b.left + b.right) / 2f, (b.top + b.bottom) / 2f) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_MILLIS))
            .build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCancelled(gestureDescription: GestureDescription?) {
                safely {
                    redirectPolicy.markFailed(pkg)
                    graph.detectionState.redirect.value = redirectPolicy.lastAttempt
                }
                process()
            }
        }, engine)
    }

    /** Seuls points d'appel moteur de `overlay.show`/`hide` : tiennent [overlayBottomPx] à jour. Thread "rehab-engine". */
    private fun showOverlay(state: OverlayState) {
        overlayBottomPx = state.navBarTop ?: Int.MAX_VALUE
        overlay.show(state)
    }

    private fun hideOverlay() {
        overlayBottomPx = null
        overlay.hide()
    }

    private fun leaveTargets() {
        // overlay.hide() d'abord : c'est un simple post{} vers le thread principal (voir OverlayController),
        // alors que usageTracker.closeOpen() attaque Room. Si l'I/O lève, l'overlay doit déjà avoir été
        // retiré (IMPORTANT 5, revue finale) — l'ordre inverse laissait l'overlay affiché en cas d'échec Room.
        hideOverlay()
        redirectPolicy.resetAll()
        graph.detectionState.redirect.value = redirectPolicy.lastAttempt
        engine.removeCallbacks(redirectCheckRunnable)
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
