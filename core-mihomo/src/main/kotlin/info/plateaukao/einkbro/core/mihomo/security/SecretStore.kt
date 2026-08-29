package info.plateaukao.einkbro.core.mihomo.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

class SecretStore(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun controllerSecret(): String {
        preferences.getString(KEY_CONTROLLER_SECRET, null)?.let { existing ->
            if (existing.length >= 32) return existing
        }

        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val generated = Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        check(preferences.edit().putString(KEY_CONTROLLER_SECRET, generated).commit())
        return generated
    }

    private companion object {
        const val PREFERENCES = "mihomo_private"
        const val KEY_CONTROLLER_SECRET = "controller_secret"
    }
}
