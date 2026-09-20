package rehab.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "usage_intervals", indices = [Index("start_utc")])
data class UsageIntervalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "target_id") val targetId: String,
    @ColumnInfo(name = "start_utc") val startUtc: Long,
    @ColumnInfo(name = "end_utc") val endUtc: Long,
    val open: Boolean,
)

@Entity(tableName = "events", indices = [Index("at_utc")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    @ColumnInfo(name = "at_utc") val atUtc: Long,
    @ColumnInfo(name = "unlock_until_utc") val unlockUntilUtc: Long? = null,
    @ColumnInfo(name = "package_name") val packageName: String? = null,
    val version: String? = null,
    val message: String? = null,
)

@Entity(tableName = "streak_record")
data class StreakRecordEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "best_days") val bestDays: Int,
    @ColumnInfo(name = "installed_at_utc") val installedAtUtc: Long,
)
