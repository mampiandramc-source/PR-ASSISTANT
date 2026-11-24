package com.prassistant.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val roleLabel: String = "Principal",
    val isActive: Boolean = true,
    val totalDone: Int = 0,
    val restrictionsCount: Int = 0
)

@Entity(tableName = "tasks")
data class TaskItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long = 1L,
    val targetLabel: String,
    val status: String = "PENDING", // PENDING, DONE, SKIPPED, RESTRICTED
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long = 1L,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val tasksCompleted: Int = 0,
    val workSeconds: Long = 0L,
    val restSeconds: Long = 0L
)

@Entity(tableName = "restrictions")
data class RestrictionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long = 1L,
    val occurredAt: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0L,
    val notes: String = "Restriction signalée pendant la session"
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val minDelaySec: Int = 20,
    val maxDelaySec: Int = 60,
    val randomDelayEnabled: Boolean = true,
    val autoBreakEveryTasks: Int = 15,
    val breakDurationSec: Int = 300,
    val autoBreakEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val isCompactOverlay: Boolean = false
)

@Dao
interface PrDao {
    @Query("SELECT * FROM accounts ORDER BY id ASC")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("SELECT * FROM tasks ORDER BY id DESC")
    fun observeTasks(): Flow<List<TaskItemEntity>>

    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'PENDING'")
    fun observePendingTasksCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'DONE' AND completedAt >= :startOfDay")
    fun observeDoneTodayCount(startOfDay: Long): Flow<Int>

    @Insert
    suspend fun insertTask(task: TaskItemEntity): Long

    @Query("UPDATE tasks SET status = :status, completedAt = :completedAt WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: Long, status: String, completedAt: Long?)

    @Query("SELECT * FROM sessions ORDER BY id DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM restrictions ORDER BY occurredAt DESC")
    fun observeRestrictions(): Flow<List<RestrictionLogEntity>>

    @Insert
    suspend fun insertRestriction(log: RestrictionLogEntity): Long

    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observeSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettingsOnce(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettingsEntity)
}

@Database(
    entities = [
        AccountEntity::class,
        TaskItemEntity::class,
        SessionEntity::class,
        RestrictionLogEntity::class,
        AppSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PrDatabase : RoomDatabase() {
    abstract fun dao(): PrDao

    companion object {
        @Volatile
        private var INSTANCE: PrDatabase? = null

        fun getInstance(context: Context): PrDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PrDatabase::class.java,
                    "pr_assistant.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
