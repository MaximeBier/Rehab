package rehab.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import rehab.app.R

fun interface RulesNotifier {
    fun notifyOutOfRange(packageName: String, version: String)
}

class AndroidRulesNotifier(private val context: Context) : RulesNotifier {
    override fun notifyOutOfRange(packageName: String, version: String) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val appName = when (packageName) {
            "com.instagram.android" -> "Instagram"
            "com.twitter.android" -> "X"
            else -> packageName
        }
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Règles à mettre à jour pour $appName $version")
            .setSilent(true)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
    }

    companion object {
        const val CHANNEL_ID = "rules"
        const val NOTIFICATION_ID = 1001

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Règles de détection", NotificationManager.IMPORTANCE_LOW)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
