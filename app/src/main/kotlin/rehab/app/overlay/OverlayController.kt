package rehab.app.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import rehab.app.ui.theme.RehabTheme
import rehab.domain.time.Clock

class OverlayController(
    private val service: AccessibilityService,
    private val clock: Clock,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        /** « Quitter » : retour à l'écran d'accueil du téléphone (DESIGN §6.2). */
        fun onQuit()
        fun onHoldCompleted()
    }

    companion object {
        /** Durée pendant laquelle l'état terminé (bouton plein, fond figé) reste affiché après un appui complet. */
        const val DONE_MILLIS = 1500L
    }

    private val main = Handler(Looper.getMainLooper())
    private val wm get() = service.getSystemService(WindowManager::class.java)
    // v0.5.1 : coupe le son du Reel/de la vidéo sous l'overlay (voir AudioSilencer).
    private val silencer = AudioSilencer(service)

    // Lus depuis isShowing, potentiellement depuis un autre thread que celui du Handler principal
    // (p. ex. le thread moteur du service d'accessibilité de la tâche 22) : volatile pour éviter
    // qu'un appelant hors du thread principal voie une valeur périmée juste après un show()/hide() posté.
    @Volatile private var view: ComposeView? = null
    @Volatile private var owner: OverlayLifecycleOwner? = null
    @Volatile private var currentHeight: Int = WindowManager.LayoutParams.MATCH_PARENT
    private var state by mutableStateOf<OverlayState?>(null)

    /**
     * Incrémenté à la fin d'un gel si l'overlay est toujours affiché : `key(session)` recrée alors
     * BlockOverlay, qui repart de son état de repos au lieu de rester figé sur « terminé » (bouton inactif)
     * si le blocage persiste malgré l'appui (p. ex. échec du commit). Thread principal uniquement.
     */
    private var session by mutableIntStateOf(0)

    /** Horloge du Handler (uptime) : pilotable par Robolectric. Accédés uniquement sur le thread principal. */
    private var frozenUntil = 0L
    private var deferred: (() -> Unit)? = null
    private val flush = Runnable {
        frozenUntil = 0L
        val action = deferred
        deferred = null
        action?.invoke()
        if (view != null) session++
    }

    val isShowing: Boolean get() = view != null

    /**
     * Pendant [DONE_MILLIS] après un appui complet, l'overlay reste dans son état final (bouton plein,
     * fond rouge figé) : les show()/hide() du moteur sont différés, seul le dernier est appliqué à la fin
     * du gel, par [flush] que [holdCompleted] a planifié.
     */
    private fun runOrDefer(action: () -> Unit) {
        // MINEUR 4 (revue finale) : tester frozenUntil != 0L (que seuls flush/hideOnMain remettent à 0) plutôt
        // que de comparer à l'horloge. Comparer à SystemClock.uptimeMillis() ouvrait une course : une requête
        // postée pendant le gel, exécutée juste après l'échéance mais avant que le Runnable `flush` (posté au
        // même instant) n'ait tourné, se serait appliquée immédiatement — rejouée une seconde fois ensuite par
        // flush avec un `deferred` plus ancien. frozenUntil ne redevient 0 qu'une fois flush/hideOnMain déjà
        // passés : aucune ambiguïté possible sur l'ordre.
        if (frozenUntil != 0L) deferred = action else action()
    }

    fun show(newState: OverlayState) = main.post { runOrDefer { showOnMain(newState) } }
    fun hide() = main.post { runOrDefer { hideOnMain() } }

    /** Appelé par BlockOverlay à la fin de l'appui (thread principal). Ignoré pendant un gel déjà en cours. */
    fun holdCompleted() = main.post {
        val now = SystemClock.uptimeMillis()
        if (now < frozenUntil) return@post
        frozenUntil = now + DONE_MILLIS
        main.removeCallbacks(flush)
        main.postDelayed(flush, DONE_MILLIS)
        callbacks.onHoldCompleted()
    }

    private fun params(height: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun showOnMain(newState: OverlayState) {
        state = newState
        val height = newState.navBarTop ?: WindowManager.LayoutParams.MATCH_PARENT
        val existing = view
        if (existing == null) {
            val lifecycle = OverlayLifecycleOwner().also { it.start() }
            val v = ComposeView(service).apply {
                setViewTreeLifecycleOwner(lifecycle)
                setViewTreeSavedStateRegistryOwner(lifecycle)
                setViewTreeViewModelStoreOwner(lifecycle)
                setContent {
                    RehabTheme {
                        state?.let {
                            key(session) {
                                BlockOverlay(
                                    it,
                                    nowMillis = { clock.now().toEpochMilli() },
                                    onQuit = callbacks::onQuit,
                                    onHoldCompleted = ::holdCompleted,
                                )
                            }
                        }
                    }
                }
            }
            try {
                wm.addView(v, params(height))
                view = v
                owner = lifecycle
                currentHeight = height
                silencer.silence()
            } catch (e: Exception) {
                Log.e("Rehab", "Overlay impossible", e)
                lifecycle.stop()
                state = null
                callbacks.onQuit()
            }
        } else if (height != currentHeight) {
            // Comme addView/removeViewImmediate ci-dessus : un appel WindowManager peut lever (fenêtre déjà
            // détachée, service en cours d'arrêt...). Ne pas protéger celui-ci laisserait une exception non
            // capturée remonter sur le thread principal (IMPORTANT 5, revue finale).
            runCatching { wm.updateViewLayout(existing, params(height)) }
                .onFailure { Log.e("Rehab", "Overlay updateViewLayout impossible", it) }
            currentHeight = height
        }
    }

    private fun hideOnMain() {
        // Un nouvel overlay repart propre : ni gel ni demande différée hérités du précédent.
        frozenUntil = 0L
        deferred = null
        main.removeCallbacks(flush)
        val v = view ?: return
        runCatching { wm.removeViewImmediate(v) }
        silencer.release()
        owner?.stop()
        view = null
        owner = null
        state = null
    }
}
