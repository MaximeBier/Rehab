package rehab.app.service

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessagesLauncherTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pkg = "com.instagram.android"
    private val uri = "instagram://direct-inbox"

    private fun installHandler(launcher: MessagesLauncher) =
        shadowOf(context.packageManager).addResolveInfoForIntent(
            launcher.intentFor(pkg, uri),
            ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = pkg; name = "UrlHandlerLauncherActivity" } },
        )

    @Test fun `intent vise l app et la liste`() {
        val i = MessagesLauncher(context).intentFor(pkg, uri)
        assertEquals(Intent.ACTION_VIEW, i.action)
        assertEquals(uri, i.dataString)
        assertEquals(pkg, i.`package`)
        assertTrue(i.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun `canOpen faux sans adresse`() = assertFalse(MessagesLauncher(context).canOpen(pkg, null))

    @Test fun `canOpen suit le gestionnaire de paquets`() {
        val launcher = MessagesLauncher(context)
        assertFalse(launcher.canOpen(pkg, uri))
        installHandler(launcher)
        assertTrue(launcher.canOpen(pkg, uri))
    }

    @Test fun `disponibilite memorisee par version de l app`() {
        var lookups = 0
        val counting = object : ContextWrapper(context) {
            override fun getPackageManager() = super.getPackageManager().also { lookups++ }
        }
        val launcher = MessagesLauncher(counting)
        installHandler(launcher)
        assertTrue(launcher.isAvailable(pkg, uri, "456.1"))
        assertTrue(launcher.isAvailable(pkg, uri, "456.1"))
        assertEquals(1, lookups)
        assertTrue(launcher.isAvailable(pkg, uri, "457.0"))
        assertEquals(2, lookups)
    }

    @Test fun `open renvoie false si startActivity leve`() {
        val throwing = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent?) = throw ActivityNotFoundException("refus")
        }
        val launcher = MessagesLauncher(throwing)
        installHandler(launcher)
        assertFalse(launcher.open(pkg, uri))
    }
}
