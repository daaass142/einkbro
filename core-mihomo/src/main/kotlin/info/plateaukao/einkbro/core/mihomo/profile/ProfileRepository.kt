package info.plateaukao.einkbro.core.mihomo.profile

import android.content.Context
import android.system.Os
import androidx.room.Room
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ProfileRepository private constructor(
    private val root: File,
    private val dao: MihomoProfileDao,
) {
    val profiles: Flow<List<ProfileRecord>> =
        dao.observeAll().map { rows -> rows.map(::map) }

    suspend fun get(id: String): ProfileRecord? =
        dao.get(id)?.let(::map)

    suspend fun importLocal(
        displayName: String,
        yaml: String,
    ): ProfileRecord = withContext(Dispatchers.IO) {
        validatePayload(yaml)
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val dir = File(root, id).apply { mkdirs() }
        val target = File(dir, SOURCE_FILE)
        atomicWrite(target, yaml.toByteArray(StandardCharsets.UTF_8))

        val entity = MihomoProfileEntity(
            id = id,
            name = displayName.ifBlank { "Local profile" },
            sourceType = ProfileSourceType.LOCAL.name,
            sourceUrl = null,
            filePath = target.absolutePath,
            createdAt = now,
            updatedAt = now,
            lastError = null,
        )
        dao.upsert(entity)
        map(entity)
    }

    suspend fun createSubscription(
        displayName: String,
        sourceUrl: String,
        yaml: String,
    ): ProfileRecord = withContext(Dispatchers.IO) {
        validatePayload(yaml)
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val dir = File(root, id).apply { mkdirs() }
        val target = File(dir, SOURCE_FILE)
        atomicWrite(target, yaml.toByteArray(StandardCharsets.UTF_8))

        val entity = MihomoProfileEntity(
            id = id,
            name = displayName.ifBlank { "Subscription" },
            sourceType = ProfileSourceType.SUBSCRIPTION.name,
            sourceUrl = sourceUrl,
            filePath = target.absolutePath,
            createdAt = now,
            updatedAt = now,
            lastError = null,
        )
        dao.upsert(entity)
        map(entity)
    }

    suspend fun stageUpdate(
        id: String,
        yaml: String,
    ): StagedProfileUpdate = withContext(Dispatchers.IO) {
        validatePayload(yaml)
        val profile = checkNotNull(get(id)) { "Profile not found: $id" }
        val candidate = File(File(profile.filePath).parentFile, CANDIDATE_FILE)
        atomicWrite(candidate, yaml.toByteArray(StandardCharsets.UTF_8))
        StagedProfileUpdate(profile, candidate.absolutePath)
    }

    suspend fun commit(staged: StagedProfileUpdate): ProfileRecord =
        withContext(Dispatchers.IO) {
            val current = checkNotNull(get(staged.profile.id)) {
                "Profile not found: ${staged.profile.id}"
            }
            val candidate = File(staged.candidatePath)
            check(candidate.isFile) { "Staged profile update is missing" }

            val target = File(current.filePath)
            // Candidate and target live in the same private directory; rename(2)
            // atomically replaces the source profile only after validation succeeds.
            Os.rename(candidate.absolutePath, target.absolutePath)
            val now = System.currentTimeMillis()
            dao.updateStatus(current.id, now, null)
            checkNotNull(get(current.id))
        }

    suspend fun discard(staged: StagedProfileUpdate) = withContext(Dispatchers.IO) {
        File(staged.candidatePath).delete()
    }

    suspend fun markError(id: String, error: String?) {
        dao.updateStatus(id, System.currentTimeMillis(), error?.take(500))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val profile = get(id) ?: return@withContext
        dao.delete(id)
        File(profile.filePath).parentFile?.deleteRecursively()
    }

    private fun validatePayload(yaml: String) {
        require(yaml.isNotBlank()) { "Empty mihomo profile" }
        require(yaml.toByteArray(StandardCharsets.UTF_8).size <= MAX_PROFILE_BYTES) {
            "Mihomo profile is larger than 10 MiB"
        }
        require(!yaml.contains('\u0000')) { "Mihomo profile contains NUL bytes" }
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            FileOutputStream(temp).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            Os.rename(temp.absolutePath, target.absolutePath)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun map(entity: MihomoProfileEntity): ProfileRecord =
        ProfileRecord(
            id = entity.id,
            name = entity.name,
            sourceType = runCatching { ProfileSourceType.valueOf(entity.sourceType) }
                .getOrDefault(ProfileSourceType.LOCAL),
            sourceUrl = entity.sourceUrl,
            filePath = entity.filePath,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            lastError = entity.lastError,
        )

    companion object {
        private const val SOURCE_FILE = "source.yaml"
        private const val CANDIDATE_FILE = "source.candidate.yaml"
        private const val MAX_PROFILE_BYTES = 10 * 1024 * 1024

        fun create(context: Context): ProfileRepository {
            val app = context.applicationContext
            val database = Room.databaseBuilder(
                app,
                MihomoProfileDatabase::class.java,
                "mihomo-profiles.db",
            ).build()
            val root = File(app.filesDir, "mihomo/profiles").apply { mkdirs() }
            return ProfileRepository(root, database.profiles())
        }
    }
}
