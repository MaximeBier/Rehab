package rehab.app.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import rehab.rules.RedirectTab

@RunWith(RobolectricTestRunner::class)
class RedirectPrefsTest {
    private val prefs = ApplicationProvider.getApplicationContext<android.content.Context>()
        .getSharedPreferences("test_redirect", android.content.Context.MODE_PRIVATE)

    @Test fun `messages par defaut`() {
        assertEquals(RedirectTab.Messages, RedirectPrefs(prefs).get("com.instagram.android"))
        assertEquals(RedirectTab.Messages, RedirectPrefs(prefs).get("com.twitter.android"))
    }

    @Test fun `choix persiste par app`() {
        RedirectPrefs(prefs).set("com.instagram.android", RedirectTab.Search)
        RedirectPrefs(prefs).set("com.twitter.android", null)
        val reloaded = RedirectPrefs(prefs)
        assertEquals(RedirectTab.Search, reloaded.get("com.instagram.android"))
        assertNull(reloaded.get("com.twitter.android"))
    }

    @Test fun `valeur inconnue retombe sur messages`() {
        prefs.edit().putString("tab_com.instagram.android", "Reels").commit()
        assertEquals(RedirectTab.Messages, RedirectPrefs(prefs).get("com.instagram.android"))
    }
}
