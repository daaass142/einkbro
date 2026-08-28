package info.plateaukao.einkbro.core.mihomo.runtime

import android.content.Context
import android.os.Build
import info.plateaukao.einkbro.core.mihomo.api.ControllerEndpoint
import info.plateaukao.einkbro.core.mihomo.api.MihomoEngine
import info.plateaukao.einkbro.core.mihomo.api.MihomoException
import info.plateaukao.einkbro.core.mihomo.api.MihomoProfile
import info.plateaukao.einkbro.core.mihomo.api.MihomoSession
import info.plateaukao.einkbro.core.mihomo.api.MihomoSessionState
import info.plateaukao.einkbro.core.mihomo.api.ProxyEndpoint
import info.plateaukao.einkbro.core.mihomo.config.ConfigStore
import info.plateaukao.einkbro.core.mihomo.config.PortAllocator
import info.plateaukao.einkbro.core.mihomo.config.RuntimeConfigBuilder
import info.plateaukao.einkbro.core.mihomo.security.SecretStore
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MihomoSessionManager private constructor(
    private val engine: MihomoEngine,
    private val configStore: ConfigStore,
    private val configBuilder: RuntimeConfigBuilder,
    private val portAllocator: PortAllocator,
    private val secretStore: SecretStore,
    private val healthChecker: LocalEndpointHealthChecker,
) {
    private val mutex = Mutex()
    private val mutableState =
        MutableStateFlow<MihomoSessionState>(MihomoSessionState.Stopped)

    val state: StateFlow<MihomoSessionState> = mutableState.asStateFlow()

    suspend fun start(profile: MihomoProfile): MihomoSession = mutex.withLock {
        val current = (mutableState.value as? MihomoSessionState.Running)?.session
        if (current?.profile?.path == profile.path) return@withLock current

        mutableState.value = MihomoSessionState.Starting(profile)
        val source = File(profile.path)
        if (!source.isFile) {
            return@withLock fail(
                profile,
                MihomoException.InvalidProfile("Profile file does not exist"),
            )
        }
        if (source.length() > MAX_PROFILE_BYTES) {
            return@withLock fail(
                profile,
                MihomoException.InvalidProfile("Profile is larger than 10 MiB"),
            )
        }

        val sourceYaml = try {
            source.readText()
        } catch (error: Throwable) {
            return@withLock fail(
                profile,
                MihomoException.InvalidProfile("Unable to read profile", error),
            )
        }

        val secret = secretStore.controllerSecret()
        var lastError: Throwable? = null

        repeat(START_ATTEMPTS) {
            val ports = portAllocator.allocate()
            val runtimeConfig = configBuilder.build(
                sourceYaml = sourceYaml,
                socksPort = ports.socksPort,
                controllerPort = ports.controllerPort,
                controllerSecret = secret,
            )
            configStore.writeRuntimeConfig(runtimeConfig)

            try {
                runCatching { engine.stop() }
                engine.start(
                    homeDir = configStore.homeDir.absolutePath,
                    platformVersion = Build.VERSION.SDK_INT,
                )
                healthChecker.awaitListening(LOOPBACK, ports.socksPort)
                healthChecker.awaitListening(LOOPBACK, ports.controllerPort)

                val session = MihomoSession(
                    profile = profile,
                    socksEndpoint = ProxyEndpoint(LOOPBACK, ports.socksPort),
                    controllerEndpoint = ControllerEndpoint(
                        LOOPBACK,
                        ports.controllerPort,
                        secret,
                    ),
                )
                configStore.markLastKnownGood()
                mutableState.value = MihomoSessionState.Running(session)
                return@withLock session
            } catch (error: Throwable) {
                lastError = error
                runCatching { engine.stop() }
            }
        }

        val typed = lastError as? MihomoException
            ?: MihomoException.RuntimeFailure(
                "Unable to start local mihomo SOCKS/controller listeners",
                lastError,
            )
        fail(profile, typed)
    }

    suspend fun reload(profile: MihomoProfile): MihomoSession = mutex.withLock {
        runCatching { engine.stop() }
        mutableState.value = MihomoSessionState.Stopped
        startUnlocked(profile)
    }

    suspend fun stop() = mutex.withLock {
        runCatching { engine.stop() }
        mutableState.value = MihomoSessionState.Stopped
    }

    fun currentSession(): MihomoSession? =
        (mutableState.value as? MihomoSessionState.Running)?.session

    private suspend fun startUnlocked(profile: MihomoProfile): MihomoSession {
        // Re-entering start() while holding the mutex would deadlock. Release is
        // avoided by implementing reload through this small sequential reset.
        mutableState.value = MihomoSessionState.Stopped
        return startAfterReset(profile)
    }

    private suspend fun startAfterReset(profile: MihomoProfile): MihomoSession {
        val source = File(profile.path)
        if (!source.isFile) {
            return fail(profile, MihomoException.InvalidProfile("Profile file does not exist"))
        }
        if (source.length() > MAX_PROFILE_BYTES) {
            return fail(profile, MihomoException.InvalidProfile("Profile is larger than 10 MiB"))
        }
        val sourceYaml = source.readText()
        val secret = secretStore.controllerSecret()
        var lastError: Throwable? = null
        mutableState.value = MihomoSessionState.Starting(profile)

        repeat(START_ATTEMPTS) {
            val ports = portAllocator.allocate()
            configStore.writeRuntimeConfig(
                configBuilder.build(
                    sourceYaml,
                    ports.socksPort,
                    ports.controllerPort,
                    secret,
                )
            )
            try {
                engine.start(configStore.homeDir.absolutePath, Build.VERSION.SDK_INT)
                healthChecker.awaitListening(LOOPBACK, ports.socksPort)
                healthChecker.awaitListening(LOOPBACK, ports.controllerPort)
                val session = MihomoSession(
                    profile,
                    ProxyEndpoint(LOOPBACK, ports.socksPort),
                    ControllerEndpoint(LOOPBACK, ports.controllerPort, secret),
                )
                configStore.markLastKnownGood()
                mutableState.value = MihomoSessionState.Running(session)
                return session
            } catch (error: Throwable) {
                lastError = error
                runCatching { engine.stop() }
            }
        }
        return fail(
            profile,
            lastError as? MihomoException
                ?: MihomoException.RuntimeFailure("Unable to reload mihomo", lastError),
        )
    }

    private fun fail(
        profile: MihomoProfile?,
        error: MihomoException,
    ): Nothing {
        mutableState.value = MihomoSessionState.Failed(profile, error)
        throw error
    }

    companion object {
        private const val LOOPBACK = "127.0.0.1"
        private const val START_ATTEMPTS = 3
        private const val MAX_PROFILE_BYTES = 10L * 1024L * 1024L

        fun create(
            context: Context,
            engine: MihomoEngine,
        ): MihomoSessionManager {
            val app = context.applicationContext
            return MihomoSessionManager(
                engine = engine,
                configStore = ConfigStore(app.filesDir),
                configBuilder = RuntimeConfigBuilder(),
                portAllocator = PortAllocator(),
                secretStore = SecretStore(app),
                healthChecker = LocalEndpointHealthChecker(),
            )
        }
    }
}
