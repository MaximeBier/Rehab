package rehab.app.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import rehab.domain.time.Clock

class OverlayController(
    private val service: AccessibilityService,
    private val clock: Clock,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onBack()
        fun onHoldCompleted()
    }

    private val main = Handler(Looper.getMainLooper())
    private val wm get() = service.getSystemService(WindowManager::class.java)

    // Lus depuis isShowing, potentiellement depuis un autre thread que celui du Handler principal
    // (p. ex. le thread moteur du service d'accessibilité de la tâche 22) : volatile pour éviter
    // qu'un appelant hors du thread principal voie une valeur périmée juste après un show()/hide() posté.
    @Volatile private var view: ComposeView? = null
    @Volatile private var owner: OverlayLifecycleOwner? = null
    @Volatile private var currentHeight: Int = WindowManager.LayoutParams.MATCH_PARENT
    private var state by mutableStateOf<OverlayState?>(null)

    val isShowing: Boolean get() = view != null

    fun show(newState: OverlayState) = main.post { showOnMain(newState) }
    fun hide() = main.post { hideOnMain() }

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
                    MaterialTheme {
                        state?.let {
                            BlockOverlay(
                                it,
                                nowMillis = { clock.now().toEpochMilli() },
                                onBack = callbacks::onBack,
                                onHoldCompleted = callbacks::onHoldCompleted,
                            )
                        }
                    }
                }
            }
            try {
                wm.addView(v, params(height))
                view = v
                owner = lifecycle
                currentHeight = height
            } catch (e: Exception) {
                Log.e("Rehab", "Overlay impossible", e)
                lifecycle.stop()
                state = null
                callbacks.onBack()
            }
        } else if (height != currentHeight) {
            wm.updateViewLayout(existing, params(height))
            currentHeight = height
        }
    }

    private fun hideOnMain() {
        val v = view ?: return
        runCatching { wm.removeViewImmediate(v) }
        owner?.stop()
        view = null
        owner = null
        state = null
    }
}
