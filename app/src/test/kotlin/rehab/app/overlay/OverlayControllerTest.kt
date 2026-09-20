package rehab.app.overlay

import android.accessibilityservice.AccessibilityService
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import rehab.domain.model.BlockReason
import rehab.domain.policy.PressOutcome
import rehab.domain.time.FakeClock
import java.time.Instant
import java.time.ZoneId

/** Service minimal permettant d'obtenir une vraie instance d'AccessibilityService sous Robolectric. */
class TestAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}

@RunWith(RobolectricTestRunner::class)
class OverlayControllerTest {
    private val clock = FakeClock(Instant.parse("2026-09-21T10:00:00Z"))
    private val zone = ZoneId.of("Europe/Paris")

    private fun state() = OverlayState(
        reason = BlockReason.Night,
        unlockAtMillis = clock.now().toEpochMilli(),
        streak = 1,
        best = 1,
        outcome = PressOutcome.Joker(1),
        holdMillis = 10_000,
        navBarTop = null,
        zone = zone,
    )

    private fun service(): AccessibilityService =
        Robolectric.buildService(TestAccessibilityService::class.java).create().get()

    private fun shadowWm(service: AccessibilityService): ShadowWindowManagerImpl {
        val wm = service.getSystemService(WindowManager::class.java)
        return Shadow.extract(wm)
    }

    private class RecordingCallbacks : OverlayController.Callbacks {
        var backCalls = 0
        override fun onBack() { backCalls++ }
        override fun onHoldCompleted() = Unit
    }

    @Test fun secondShowUpdatesTheExistingWindowWithoutStackingASecondOne() {
        val svc = service()
        val callbacks = RecordingCallbacks()
        val controller = OverlayController(svc, clock, callbacks)

        controller.show(state())
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, shadowWm(svc).views.size)
        assertTrue(controller.isShowing)

        controller.show(state().copy(streak = 2))
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(1, shadowWm(svc).views.size)
        assertTrue(controller.isShowing)
        assertEquals(0, callbacks.backCalls)
    }

    @Test fun hideRemovesTheViewAndResetsInternalState() {
        val svc = service()
        val callbacks = RecordingCallbacks()
        val controller = OverlayController(svc, clock, callbacks)

        controller.show(state())
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(controller.isShowing)
        assertEquals(1, shadowWm(svc).views.size)

        controller.hide()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertFalse(controller.isShowing)
        assertEquals(0, shadowWm(svc).views.size)
    }

    @Test fun hideWithoutAPriorShowIsANoOp() {
        val svc = service()
        val controller = OverlayController(svc, clock, RecordingCallbacks())

        controller.hide()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertFalse(controller.isShowing)
        assertEquals(0, shadowWm(svc).views.size)
    }
}
