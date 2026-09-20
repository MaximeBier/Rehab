package rehab.app.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import rehab.domain.model.Settings

@RunWith(RobolectricTestRunner::class)
class PrefsSettingsRepoTest {
    private val prefs = ApplicationProvider.getApplicationContext<android.content.Context>()
        .getSharedPreferences("test_settings", android.content.Context.MODE_PRIVATE)

    @Test fun readImmediatelyAfterSetReturnsWrittenValue() {
        val repo = PrefsSettingsRepo(prefs)
        val custom = Settings.DEFAULT.copy(jokersPerDay = 3)

        repo.set(custom)

        // apply() est asynchrone pour le disque, mais le cache mémoire garantit une lecture
        // immédiate cohérente : c'est ce que la tâche 25 (écran de réglages sur le thread UI) exige.
        assertEquals(custom, repo.get())
    }

    @Test fun aNewRepoOnSamePrefsSeesThePersistedValue() {
        val custom = Settings.DEFAULT.copy(jokersPerDay = 3)
        PrefsSettingsRepo(prefs).set(custom)

        val reloaded = PrefsSettingsRepo(prefs)

        assertEquals(custom, reloaded.get())
    }
}
