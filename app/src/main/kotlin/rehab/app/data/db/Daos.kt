package rehab.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface UsageIntervalDao {
    @Query("SELECT * FROM usage_intervals WHERE open = 1 OR end_utc >= :fromUtc ORDER BY start_utc")
    fun since(fromUtc: Long): List<UsageIntervalEntity>

    @Query("SELECT * FROM usage_intervals WHERE open = 1 LIMIT 1")
    fun open(): UsageIntervalEntity?

    @Query("SELECT * FROM usage_intervals ORDER BY start_utc DESC LIMIT :limit")
    fun latest(limit: Int): List<UsageIntervalEntity>

    @Insert fun insert(e: UsageIntervalEntity): Long
    @Update fun update(e: UsageIntervalEntity)

    @Query("DELETE FROM usage_intervals WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM usage_intervals WHERE open = 0 AND end_utc < :beforeUtc")
    fun purgeBefore(beforeUtc: Long)
}

@Dao
interface EventDao {
    @Insert fun insert(e: EventEntity)

    @Query("SELECT * FROM events ORDER BY at_utc, id")
    fun all(): List<EventEntity>

    @Query("SELECT * FROM events WHERE at_utc >= :fromUtc ORDER BY at_utc, id")
    fun since(fromUtc: Long): List<EventEntity>

    @Query("SELECT COUNT(*) FROM events WHERE type = 'ERROR'")
    fun countErrors(): Int

    @Query("DELETE FROM events WHERE id IN (SELECT id FROM events WHERE type = 'ERROR' ORDER BY at_utc, id LIMIT :n)")
    fun deleteOldestErrors(n: Int)
}

@Dao
interface StreakRecordDao {
    @Query("SELECT * FROM streak_record WHERE id = 1")
    fun get(): StreakRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(e: StreakRecordEntity)
}
