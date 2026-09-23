package rehab.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import rehab.app.service.RehabAccessibilityService

/**
 * Lectures de configuration système pures (aucun accès Room, aucun appel `policy`/`eventLog`) :
 * peut donc être appelée directement depuis un Composable.
 */
class Prerequisites(private val context: Context) {

    fun accessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, RehabAccessibilityService::class.java)
        return enabled.split(':').any { it.equals(me.flattenToString(), true) || it.equals(me.flattenToShortString(), true) }
    }

    fun batteryOptimizationIgnored(): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun notificationsAllowed(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openAccessibilitySettings() =
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun openBatterySettings() =
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

    /** Ouvre le réglage système d'accès aux données d'utilisation (v0.4.0, écran Stats). L'état de la permission
     * lui-même vient de `RehabViewModel.loadStats` (`graph.usageHistory.hasPermission()`), jamais lu ici. */
    fun openUsageAccessSettings() =
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
