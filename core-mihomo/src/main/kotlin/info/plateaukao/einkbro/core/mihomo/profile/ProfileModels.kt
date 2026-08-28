package info.plateaukao.einkbro.core.mihomo.profile

import info.plateaukao.einkbro.core.mihomo.api.MihomoProfile

enum class ProfileSourceType {
    LOCAL,
    SUBSCRIPTION,
}

data class ProfileRecord(
    val id: String,
    val name: String,
    val sourceType: ProfileSourceType,
    val sourceUrl: String?,
    val filePath: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastError: String?,
) {
    fun asMihomoProfile(): MihomoProfile =
        MihomoProfile(id = id, name = name, path = filePath)
}

data class StagedProfileUpdate(
    val profile: ProfileRecord,
    val candidatePath: String,
)
