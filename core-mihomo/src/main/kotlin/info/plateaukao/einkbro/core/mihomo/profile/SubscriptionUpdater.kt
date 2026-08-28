package info.plateaukao.einkbro.core.mihomo.profile

import android.content.Context
import info.plateaukao.einkbro.core.mihomo.security.SensitiveValueRedactor
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SubscriptionUpdater private constructor(
    private val profiles: ProfileRepository,
    private val client: OkHttpClient,
) {
    suspend fun create(
        name: String,
        url: String,
    ): ProfileRecord {
        val normalized = validateUrl(url)
        val yaml = download(normalized)
        return profiles.createSubscription(name, normalized, yaml)
    }

    suspend fun stageRefresh(id: String): StagedProfileUpdate {
        val profile = checkNotNull(profiles.get(id)) { "Profile not found: $id" }
        require(profile.sourceType == ProfileSourceType.SUBSCRIPTION) {
            "Profile is not a subscription"
        }
        val url = checkNotNull(profile.sourceUrl) { "Subscription URL is missing" }

        return try {
            val yaml = download(validateUrl(url))
            profiles.stageUpdate(id, yaml)
        } catch (error: Throwable) {
            profiles.markError(
                id,
                "Subscription refresh failed: " +
                    SensitiveValueRedactor.redactUrl(error.message.orEmpty()),
            )
            throw error
        }
    }

    private fun validateUrl(url: String): String {
        val uri = URI(url.trim())
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Subscription URL must use HTTPS"
        }
        require(!uri.host.isNullOrBlank()) { "Subscription URL has no host" }
        return uri.toASCIIString()
    }

    private suspend fun download(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/yaml, text/plain, application/yaml, */*")
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "Subscription request failed with HTTP ${response.code}"
            }
            val body = checkNotNull(response.body) { "Empty subscription response" }
            val declared = body.contentLength()
            check(declared < 0 || declared <= MAX_BYTES) {
                "Subscription response is larger than 10 MiB"
            }

            body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    check(total <= MAX_BYTES) {
                        "Subscription response is larger than 10 MiB"
                    }
                    output.write(buffer, 0, read)
                }
                output.toString(StandardCharsets.UTF_8.name())
            }
        }
    }

    companion object {
        private const val MAX_BYTES = 10 * 1024 * 1024

        fun create(
            context: Context,
            profiles: ProfileRepository,
        ): SubscriptionUpdater {
            context.applicationContext // Keep construction explicitly app-scoped.
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            return SubscriptionUpdater(profiles, client)
        }
    }
}
