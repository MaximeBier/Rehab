package rehab.app.data

import android.content.SharedPreferences
import rehab.domain.model.Settings
import rehab.domain.ports.SettingsRepo

class PrefsSettingsRepo(private val prefs: SharedPreferences) : SettingsRepo {
    @Volatile private var cached: Settings = SettingsCodec.decodeOrDefault(prefs.getString(KEY, null))

    override fun get(): Settings = cached

    override fun set(settings: Settings) {
        cached = settings
        prefs.edit().putString(KEY, SettingsCodec.encode(settings)).apply()
    }

    private companion object { const val KEY = "settings_json" }
}
