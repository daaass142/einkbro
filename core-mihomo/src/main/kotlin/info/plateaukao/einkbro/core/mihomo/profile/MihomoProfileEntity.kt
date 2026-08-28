package info.plateaukao.einkbro.core.mihomo.profile

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mihomo_profiles")
data class MihomoProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceType: String,
    val sourceUrl: String?,
    val filePath: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastError: String?,
)
