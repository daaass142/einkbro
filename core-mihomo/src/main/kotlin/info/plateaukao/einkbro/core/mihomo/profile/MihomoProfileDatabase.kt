package info.plateaukao.einkbro.core.mihomo.profile

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MihomoProfileEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MihomoProfileDatabase : RoomDatabase() {
    abstract fun profiles(): MihomoProfileDao
}
