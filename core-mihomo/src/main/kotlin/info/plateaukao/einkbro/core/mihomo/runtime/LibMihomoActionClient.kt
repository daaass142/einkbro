package info.plateaukao.einkbro.core.mihomo.runtime

import info.plateaukao.einkbro.core.mihomo.api.MihomoException
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal interface MihomoActionClient {
    suspend fun invoke(method: String, data: JsonElement = JsonNull): JsonElement
    suspend fun quickSetup(
        homeDir: String,
        platformVersion: Int,
        selectedMap: Map<String, String>,
    )
}

internal class LibMihomoActionClient(
    private val bridge: LibMihomoBridge,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : MihomoActionClient {
    private val nextId = AtomicLong(0)

    override suspend fun invoke(
        method: String,
        data: JsonElement,
    ): JsonElement {
        val id = nextId.incrementAndGet().toString()
        val action = buildJsonObject {
            put("id", id)
            put("method", method)
            put("data", data)
        }.toString()

        val raw = suspendCancellableCoroutine<String> { continuation ->
            bridge.invokeAction(action) { result ->
                if (continuation.isActive) {
                    if (result == null) {
                        continuation.resumeWithException(
                            MihomoException.MalformedResponse(
                                method,
                                IllegalStateException("null callback result"),
                            )
                        )
                    } else {
                        continuation.resume(result)
                    }
                }
            }
        }

        return parseEnvelope(method, raw)
    }

    override suspend fun quickSetup(
        homeDir: String,
        platformVersion: Int,
        selectedMap: Map<String, String>,
    ) {
        val initParams = buildJsonObject {
            put("home-dir", homeDir)
            put("version", platformVersion)
            put(
                "allowed-path-roots",
                kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(homeDir))),
            )
        }.toString()

        val setupParams = buildJsonObject {
            put(
                "selected-map",
                JsonObject(selectedMap.mapValues { JsonPrimitive(it.value) }),
            )
        }.toString()

        suspendCancellableCoroutine<Unit> { continuation ->
            bridge.quickSetup(initParams, setupParams) { result ->
                if (continuation.isActive) {
                    if (result.isNullOrEmpty()) {
                        continuation.resume(Unit)
                    } else {
                        continuation.resumeWithException(
                            MihomoException.RuntimeFailure(
                                "mihomo quickSetup failed: " + result
                            )
                        )
                    }
                }
            }
        }
    }

    fun parseNestedJson(method: String, element: JsonElement): JsonElement {
        val primitive = element as? JsonPrimitive ?: return element
        if (!primitive.isString) return primitive
        val content = primitive.contentOrNull ?: return JsonNull
        if (content.isBlank()) return JsonNull

        return try {
            json.parseToJsonElement(content)
        } catch (error: Throwable) {
            throw MihomoException.MalformedResponse(method, error)
        }
    }

    private fun parseEnvelope(
        method: String,
        raw: String,
    ): JsonElement {
        try {
            val root = json.parseToJsonElement(raw).jsonObject
            val code = root["code"]?.jsonPrimitive?.intOrNull
                ?: throw IllegalStateException("missing action result code")
            val data = root["data"] ?: JsonNull

            if (code != 0) {
                val detail = (data as? JsonPrimitive)?.contentOrNull ?: data.toString()
                throw MihomoException.ActionFailure(method, detail)
            }

            return data
        } catch (error: MihomoException) {
            throw error
        } catch (error: Throwable) {
            throw MihomoException.MalformedResponse(method, error)
        }
    }
}
