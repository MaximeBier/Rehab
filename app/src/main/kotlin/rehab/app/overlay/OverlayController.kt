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

class OverlayController(private val service: AccessibilityService, private val callbacks: Callbacks) {
    interface Callbacks {
        fun onBack()
        fun onHoldCompleted()
    }

    private val main = Handler(Looper.getMainLooper())
    private val wm get() = service.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null
    private var state by mutableStateOf<OverlayState?>(null)
    private var currentHeight: Int = WindowManager.LayoutParams.MATCH_PARENT

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
                        state?.let { BlockOverlay(it, onBack = callbacks::onBack, onHoldCompleted = callbacks::onHoldCompleted) }
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
