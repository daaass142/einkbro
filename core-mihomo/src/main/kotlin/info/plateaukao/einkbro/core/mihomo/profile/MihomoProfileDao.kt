package info.plateaukao.einkbro.core.mihomo.profile

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MihomoProfileDao {
    @Query("SELECT * FROM mihomo_profiles ORDER BY updatedAt DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<MihomoProfileEntity>>

    @Query("SELECT * FROM mihomo_profiles WHERE id = :id LIMIT 1")
    suspend fun get(id: String): MihomoProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MihomoProfileEntity)

    @Query("DELETE FROM mihomo_profiles WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE mihomo_profiles SET updatedAt = :updatedAt, lastError = :error WHERE id = :id")
    suspend fun updateStatus(id: String, updatedAt: Long, error: String?)
}
