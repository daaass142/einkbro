package info.plateaukao.einkbro.core.mihomo.api

sealed class MihomoException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    class NativeLoadFailure(cause: Throwable) :
        MihomoException("Failed to load libmihomo native libraries", cause)

    class BridgeAbiMismatch(
        val expected: Int,
        val actual: Int,
    ) : MihomoException("libmihomo bridge ABI mismatch: expected $expected, got $actual")

    class ActionFailure(
        val method: String,
        detail: String,
    ) : MihomoException("mihomo action $method failed: $detail")

    class MalformedResponse(
        val method: String,
        cause: Throwable,
    ) : MihomoException("Malformed response for mihomo action $method", cause)

    class InvalidProfile(detail: String) :
        MihomoException("Invalid mihomo profile: $detail")

    class RuntimeFailure(detail: String, cause: Throwable? = null) :
        MihomoException(detail, cause)
}
