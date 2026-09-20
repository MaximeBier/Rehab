package rehab.app.service

import kotlinx.coroutines.flow.MutableStateFlow

data class LastDetection(
    val packageName: String,
    val appVersion: String,
    val target: String?,
    val screenId: String?,
    val unknownScreen: Boolean,
    val degraded: Boolean,
    val atMillis: Long,
)

class DetectionState { val last = MutableStateFlow<LastDetection?>(null) }

class ServiceState { val connected = MutableStateFlow(false) }
