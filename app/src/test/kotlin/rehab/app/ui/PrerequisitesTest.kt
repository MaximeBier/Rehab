package rehab.app.ui

import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import rehab.app.service.RehabAccessibilityService

@RunWith(RobolectricTestRunner::class)
class PrerequisitesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prerequisites = Prerequisites(context)

    @Test fun accessibilityDisabledByDefault() {
        assertFalse(prerequisites.accessibilityEnabled())
    }

    @Test fun accessibilityEnabledWhenServiceListedAmongOthers() {
        val me = ComponentName(context, RehabAccessibilityService::class.java).flattenToString()
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "com.other/.OtherService:$me")

        assertTrue(prerequisites.accessibilityEnabled())
    }

    @Test fun accessibilityDisabledWhenAnotherServiceOnlyIsListed() {
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "com.other/.OtherService")

        assertFalse(prerequisites.accessibilityEnabled())
    }

    @Test fun batteryOptimizationReflectsPowerManagerState() {
        val shadowPm = shadowOf(context.getSystemService(PowerManager::class.java))

        assertFalse(prerequisites.batteryOptimizationIgnored())

        shadowPm.setIgnoringBatteryOptimizations(context.packageName, true)

        assertTrue(prerequisites.batteryOptimizationIgnored())
    }

    @Test fun notificationsAllowedReflectsGrantedPermission() {
        assertFalse(prerequisites.notificationsAllowed())

        shadowOf(context as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        assertTrue(prerequisites.notificationsAllowed())
    }
}
