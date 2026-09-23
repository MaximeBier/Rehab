package rehab.app.overlay

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log

/**
 * Coupe le son du Reel ou de la vidéo en cours quand l'overlay de blocage apparaît (v0.5.1).
 *
 * Rehab prend la priorité audio **transitoire** (`AUDIOFOCUS_GAIN_TRANSIENT`) : c'est le signal
 * standard « un autre son passe devant, mets-toi en pause », que les lecteurs d'Instagram et de X
 * (ExoPlayer/Media3) respectent en mettant la lecture en pause. Rehab ne joue rien lui-même ; il
 * rend la priorité au masquage de l'overlay, ce qui permet au lecteur de reprendre une fois le fil
 * débloqué. Idempotent. Thread principal uniquement (appelé par [OverlayController]).
 *
 * Échec silencieux : une demande refusée (appel téléphonique en cours…) ne doit jamais empêcher
 * l'affichage de l'overlay.
 */
class AudioSilencer(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setOnAudioFocusChangeListener { }
        .build()
    private var held = false

    fun silence() {
        if (held) return
        runCatching { audio?.requestAudioFocus(request) }
            .onSuccess { held = true }
            .onFailure { Log.w("Rehab", "Priorité audio refusée", it) }
    }

    fun release() {
        if (!held) return
        held = false
        runCatching { audio?.abandonAudioFocusRequest(request) }
            .onFailure { Log.w("Rehab", "Libération de la priorité audio impossible", it) }
    }
}
