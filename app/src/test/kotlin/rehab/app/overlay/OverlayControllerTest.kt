package rehab.app.overlay

import android.accessibilityservice.AccessibilityService
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import rehab.domain.model.BlockReason
import rehab.domain.policy.PressOutcome
import rehab.domain.policy.StreakSummary
import rehab.domain.time.FakeClock
import java.time.Duration
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
        detail = "",
        streak = StreakSummary(1, 1, 0),
        outcome = PressOutcome.Joker(1),
        holdMillis = 10_000,
        jokerMinutes = 5,
        relapseMinutes = 15,
        navBarTop = null,
        zone = zone,
    )

    private fun shadowWm(service: AccessibilityService): ShadowWindowManagerImpl {
        val wm = service.getSystemService(WindowManager::class.java)
        return Shadow.extract(wm)
    }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private class RecordingCallbacks : OverlayController.Callbacks {
        var quit = 0
        var holdCompleted = 0
        override fun onQuit() { quit++ }
        override fun onHoldCompleted() { holdCompleted++ }
    }

    private lateinit var svc: AccessibilityService
    private lateinit var callbacks: RecordingCallbacks
    private lateinit var controller: OverlayController

    @Before fun setUp() {
        svc = Robolectric.buildService(TestAccessibilityService::class.java).create().get()
        callbacks = RecordingCallbacks()
        controller = OverlayController(svc, clock, callbacks)
    }

    @Test fun secondShowUpdatesTheExistingWindowWithoutStackingASecondOne() {
        controller.show(state())
        idleMain()
        assertEquals(1, shadowWm(svc).views.size)
        assertTrue(controller.isShowing)

        controller.show(state().copy(streak = StreakSummary(2, 2, 0)))
        idleMain()

        assertEquals(1, shadowWm(svc).views.size)
        assertTrue(controller.isShowing)
        assertEquals(0, callbacks.quit)
    }

    @Test fun hideRemovesTheViewAndResetsInternalState() {
        controller.show(state())
        idleMain()
        assertTrue(controller.isShowing)
        assertEquals(1, shadowWm(svc).views.size)

        controller.hide()
        idleMain()

        assertFalse(controller.isShowing)
        assertEquals(0, shadowWm(svc).views.size)
    }

    /**
     * v0.5.1 : l'overlay coupe le son du Reel / de la vidéo en cours. À l'apparition, Rehab prend la priorité
     * audio transitoire (les lecteurs d'Instagram/X se mettent en pause) ; au masquage, il la rend.
     */
    @Test fun showTakesTransientAudioFocusAndHideReleasesIt() {
        val audio = shadowOf(svc.getSystemService(android.media.AudioManager::class.java))

        controller.show(state())
        idleMain()
        val request = audio.lastAudioFocusRequest?.audioFocusRequest
        assertNotNull(request)
        assertEquals(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, request!!.focusGain)
        assertNull(audio.lastAbandonedAudioFocusRequest)

        controller.hide()
        idleMain()
        assertEquals(request, audio.lastAbandonedAudioFocusRequest)
    }

    @Test fun updatingAShownOverlayDoesNotRequestFocusAgain() {
        val audio = shadowOf(svc.getSystemService(android.media.AudioManager::class.java))
        controller.show(state())
        idleMain()
        val first = audio.lastAudioFocusRequest

        controller.show(state().copy(streak = StreakSummary(2, 2, 0)))
        idleMain()

        assertSame(first, audio.lastAudioFocusRequest)
    }

    @Test fun hideWithoutAPriorShowIsANoOp() {
        controller.hide()
        idleMain()

        assertFalse(controller.isShowing)
        assertEquals(0, shadowWm(svc).views.size)
    }

    @Test fun hideIsDeferredDuringDoneState() {
        controller.show(state()); idleMain()
        controller.holdCompleted(); idleMain()
        assertEquals(1, callbacks.holdCompleted)
        controller.hide(); idleMain()
        assertTrue(controller.isShowing)                          // gelé 1,5 s
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(OverlayController.DONE_MILLIS))
        assertFalse(controller.isShowing)
    }

    @Test fun lastRequestWinsAfterFreeze() {
        controller.show(state()); idleMain()
        controller.holdCompleted(); idleMain()
        controller.hide(); controller.show(state()); idleMain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(OverlayController.DONE_MILLIS))
        assertTrue(controller.isShowing)
    }

    @Test fun secondCompletionDuringFreezeIsIgnored() {
        controller.show(state()); idleMain()
        controller.holdCompleted(); controller.holdCompleted(); idleMain()
        assertEquals(1, callbacks.holdCompleted)
    }

    @Test fun afterTheFreezeRequestsApplyImmediately() {
        controller.show(state()); idleMain()
        controller.holdCompleted(); idleMain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(OverlayController.DONE_MILLIS))
        assertTrue(controller.isShowing)                          // aucune demande pendant le gel : rien ne change
        controller.hide(); idleMain()
        assertFalse(controller.isShowing)
    }
}
