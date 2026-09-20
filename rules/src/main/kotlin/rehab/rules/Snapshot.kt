package rehab.rules

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = bottom - top
}

@Serializable
data class Node(
    val id: String? = null,
    val className: String? = null,
    val text: String? = null,
    val contentDesc: String? = null,
    val bounds: Bounds = Bounds(0, 0, 0, 0),
    val selected: Boolean = false,
    val depth: Int = 0,
)

@Serializable
data class Snapshot(
    val packageName: String,
    val appVersion: String,
    val capturedAt: Long,
    val nodes: List<Node>,
    val truncated: Boolean = false,
)

object SnapshotJson {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(snapshot: Snapshot): String = json.encodeToString(Snapshot.serializer(), snapshot)
    fun decode(text: String): Snapshot = json.decodeFromString(Snapshot.serializer(), text)
}
