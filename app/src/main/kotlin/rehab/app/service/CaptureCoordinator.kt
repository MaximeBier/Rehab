package rehab.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rehab.rules.Snapshot
import rehab.rules.SnapshotJson
import java.io.File

class CaptureCoordinator(private val dir: File) {
    private val _pendingAt = MutableStateFlow<Long?>(null)
    val pendingAt: StateFlow<Long?> = _pendingAt
    private val _lastFile = MutableStateFlow<File?>(null)
    val lastFile: StateFlow<File?> = _lastFile

    fun request(delayMillis: Long, nowMillis: Long) { _pendingAt.value = nowMillis + delayMillis }

    /** Appelé par le service après chaque snapshot. Sauvegarde le premier snapshot postérieur à l'échéance. */
    fun maybeSave(snapshot: Snapshot): File? {
        val at = _pendingAt.value ?: return null
        if (snapshot.capturedAt < at) return null
        _pendingAt.value = null
        dir.mkdirs()
        val file = File(dir, "${snapshot.capturedAt}_${snapshot.packageName}.json")
        file.writeText(SnapshotJson.encode(snapshot))
        _lastFile.value = file
        return file
    }

    fun list(): List<File> = dir.listFiles { f -> f.extension == "json" }?.sortedByDescending { it.name } ?: emptyList()
}
