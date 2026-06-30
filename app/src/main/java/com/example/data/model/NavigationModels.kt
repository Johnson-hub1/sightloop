package com.example.data.model

import androidx.room.*
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.flow.Flow

// --- ROOM DB CLASSIFICATIONS ---

@Entity(tableName = "navigation_logs")
data class NavigationLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val description: String,
    val hazardsCount: Int,
    val dangerLevel: String, // NONE, LOW, MEDIUM, HIGH
    val directions: String
)

@Dao
interface NavigationLogDao {
    @Query("SELECT * FROM navigation_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<NavigationLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: NavigationLog)

    @Query("DELETE FROM navigation_logs")
    suspend fun clearAllLogs()
}

@Database(entities = [NavigationLog::class], version = 1, exportSchema = false)
abstract class NavigationDatabase : RoomDatabase() {
    abstract fun navigationLogDao(): NavigationLogDao

    companion object {
        @Volatile
        private var INSTANCE: NavigationDatabase? = null

        fun getDatabase(context: android.content.Context): NavigationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NavigationDatabase::class.java,
                    "sight_loop_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- GEMINI API STRUCTURES ---

@JsonClass(generateAdapter = true)
data class Hazard(
    val name: String,
    val distance: String, // Near, Medium, Far
    val severity: String // LOW, MEDIUM, HIGH
)

@JsonClass(generateAdapter = true)
data class GeminiNavigationResponse(
    val description: String,
    val hazards: List<Hazard>?,
    val navigation_guidance: String,
    val vibration_intensity: String // NONE, LOW, MEDIUM, HIGH
)
