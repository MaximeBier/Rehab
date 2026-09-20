package rehab.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UsageIntervalEntity::class, EventEntity::class, StreakRecordEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class RehabDatabase : RoomDatabase() {
    abstract fun usageIntervals(): UsageIntervalDao
    abstract fun events(): EventDao
    abstract fun streakRecord(): StreakRecordDao

    companion object {
        fun create(context: Context): RehabDatabase =
            Room.databaseBuilder(context, RehabDatabase::class.java, "rehab.db").build()

        fun inMemory(context: Context): RehabDatabase =
            Room.inMemoryDatabaseBuilder(context, RehabDatabase::class.java).allowMainThreadQueries().build()
    }
}
