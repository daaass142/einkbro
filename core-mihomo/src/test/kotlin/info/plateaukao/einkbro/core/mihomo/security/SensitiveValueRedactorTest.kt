package info.plateaukao.einkbro.core.mihomo.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveValueRedactorTest {
    @Test
    fun redactsSubscriptionSecretsButKeepsNonSecretParameters() {
        val result = SensitiveValueRedactor.redactUrl(
            "https://example.com/sub?token=abcdef&client=android"
        )

        assertFalse(result.contains("abcdef"))
        assertTrue(result.contains("token=REDACTED"))
        assertTrue(result.contains("client=android"))
    }
}
