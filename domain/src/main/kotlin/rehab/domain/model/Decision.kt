package rehab.domain.model

import java.time.Instant

enum class BlockReason { Night, Quota }

sealed interface Decision {
    data object Allow : Decision
    data class Block(val reason: BlockReason, val unlockAt: Instant) : Decision
}
