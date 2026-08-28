# EinkBro Mihomo Browser — Technical Specification

Status: implementation-ready  
Repository: daaass142/einkbro  
Branch: feat/mihomo-browser  
Base browser: plateaukao/einkbro  
Embedded proxy runtime: daaass142/libmihomo-android  
Advanced proxy UI: Zephyruso/zashboard

## 1. Product goal

Build an Android browser based on EinkBro that embeds mihomo in the same APK and automatically routes browser traffic through mihomo.

The product is a browser with an embedded network engine. It is not a general-purpose VPN client with a browser attached.

Primary requirements:

- Preserve EinkBro browsing, E-Ink, reader, tab, download, ad-filtering and settings behavior.
- Start mihomo before restored tabs or external-intent URLs can make network requests.
- Route WebView traffic through a local mihomo SOCKS5 listener by default.
- Default to browser-scoped proxying and do not capture unrelated Android applications.
- Provide native quick controls for status, routing mode, proxy groups, selector switching, delay tests and traffic.
- Bundle Zashboard locally for complete proxy/provider/rule/connection management.
- Support local YAML profiles and remote subscription URLs.
- Default to fail-closed behavior when the proxy is expected to be active.
- Keep all local proxy and controller listeners bound to loopback.
- Pin and verify libmihomo and Zashboard artifacts in release builds.
- Keep the architecture small enough to remain maintainable as EinkBro and mihomo evolve.

## 2. Upstream and dependency baseline

### 2.1 EinkBro

The current EinkBro codebase already uses:

- Kotlin
- Jetpack Compose
- Android WebView
- AndroidX WebKit
- MVVM
- Koin
- Room
- KSP
- OkHttp
- kotlinx.serialization
- Java 17
- compileSdk 36
- targetSdk 36
- minSdk 24

The implementation MUST extend these conventions rather than introducing a second application architecture.

Do not add Hilt/Dagger, RxJava, Retrofit, another JSON stack, or a second reactive state framework without a concrete technical requirement.

### 2.2 libmihomo-android

Use daaass142/libmihomo-android as the only native mihomo integration.

The application MUST NOT copy the Go/cgo/JNI bridge into the EinkBro repository.

At the time this specification was revised, the fork exposes:

- Android AAR distribution
- Kotlin entry point io.github.oviron.libmihomo.Clash
- libclash.so plus libmihomo-jni.so
- arm64-v8a, armeabi-v7a and x86_64
- bridgeABI compatibility checks
- quickSetup and invokeAction
- getTraffic and getTotalTraffic
- event listener support
- TUN support for a future strict mode
- direct listener recreation for HTTP, SOCKS and mixed listeners inside the embedded core

Current development baseline observed in the fork:

- bridgeABI: 3
- latest published prerelease inspected: v0.3.2-alpha.20260827
- AAR SHA-256: 6acc2446392ecea0307609147387c89ba38f4697d5d3d4c80e10fd51ea4265e7
- the main branch currently tracks a mihomo v1.19.31 development pseudo-version from 2026-08-27

Release builds MUST consume an exact release artifact and MUST NOT depend on main, latest, wildcard versions, or a moving URL.

Before the first production release, either:

1. pin a tested stable libmihomo release, or
2. cut a tested stable release in daaass142/libmihomo-android and pin that exact version.

## 3. Architecture decisions

### ADR-001 — libmihomo is the native boundary

All native mihomo work is delegated to libmihomo-android.

EinkBro contains only an application-side adapter around the library.

### ADR-002 — SOCKS5 is the default browser transport

The default route is:

~~~text
EBWebView
  -> AndroidX WebKit ProxyController
  -> socks://127.0.0.1:<runtime-port>
  -> embedded mihomo
  -> rules / proxy groups
  -> selected outbound
  -> Internet
~~~

Do not use mixed-port by default when the browser only needs one explicit proxy protocol.

The runtime configuration SHOULD disable unused HTTP and mixed listeners unless a future feature requires them.

### ADR-003 — browser-scoped proxy first

The default product mode does not use Android VpnService.

This keeps proxying scoped to WebView/browser traffic, avoids a VPN permission prompt, avoids capturing other applications, and keeps the normal browser lifecycle lightweight.

### ADR-004 — strict VPN is optional and separate

A future Strict mode may use Android VpnService and libmihomo startTUN for application-only capture.

Strict mode is not required for the initial browser proxy MVP.

### ADR-005 — native quick UI uses JNI actions

Native Compose quick controls use MihomoEngine -> LibMihomoActionClient -> Clash.invokeAction.

Do not route native quick controls through localhost HTTP when libmihomo already exposes the same operation in-process.

### ADR-006 — Zashboard owns the full management UI

Zashboard remains the complete advanced management interface for proxy groups, providers, rules, connections, logs and other normal Clash/mihomo dashboard functions.

Native Compose implements only browser-centric quick controls and settings.

### ADR-007 — controller is for Zashboard, not the application domain API

Mihomo external-controller is enabled on loopback only so bundled Zashboard can use its REST/WebSocket API.

Application business code SHOULD use the typed libmihomo adapter for operations already exposed by invokeAction.

### ADR-008 — fail closed by default

When proxy-on-start is enabled, a missing or failed mihomo runtime MUST NOT silently fall back to direct browsing.

Direct access requires an explicit user action or an explicitly configured non-strict failure policy.

## 4. Gradle module layout

Initial implementation adds only two modules:

~~~text
:app
:core-network
:core-mihomo
:ad-filter
:adblock-client
~~~

Do not prematurely create many feature modules.

### 4.1 :app

Owns:

- existing EinkBro UI and browser behavior
- proxy status UI
- native quick proxy panel
- profile/settings screens
- dashboard screen
- Android permission UX
- navigation

Must not:

- import io.github.oviron.libmihomo directly
- own YAML runtime mutation logic
- manipulate native library paths
- manage TUN file descriptors
- duplicate the complete Zashboard UI

### 4.2 :core-network

Owns:

- browser network readiness
- WebView ProxyController integration
- SOCKS proxy binding
- startup gate
- fail-closed behavior
- direct/proxy mode transitions
- feature detection for AndroidX WebKit

Suggested API:

~~~kotlin
interface BrowserNetworkGateway {
    val state: StateFlow<BrowserNetworkState>

    suspend fun prepare()
    suspend fun enableProxy(endpoint: ProxyEndpoint)
    suspend fun enableDirect()
    suspend fun block()
    suspend fun shutdown()
}

sealed interface BrowserNetworkState {
    data object Stopped : BrowserNetworkState
    data object Starting : BrowserNetworkState
    data object Blocked : BrowserNetworkState
    data class Ready(val mode: BrowserNetworkMode) : BrowserNetworkState
    data class Error(val cause: Throwable) : BrowserNetworkState
}
~~~

### 4.3 :core-mihomo

Owns all application interaction with libmihomo:

~~~text
core-mihomo/
  src/main/kotlin/.../
    api/
      MihomoEngine.kt
      MihomoState.kt
      MihomoModels.kt
      MihomoException.kt
    runtime/
      LibMihomoEngine.kt
      LibMihomoLoader.kt
      LibMihomoActionClient.kt
      LibMihomoMapper.kt
      MihomoRuntimeManager.kt
    config/
      RuntimeConfigBuilder.kt
      ConfigValidator.kt
      ConfigStore.kt
      PortAllocator.kt
    profile/
      ProfileRepository.kt
      SubscriptionRepository.kt
      SubscriptionUpdater.kt
    security/
      SecretStore.kt
      SensitiveValueRedactor.kt
    controller/
      ControllerEndpoint.kt
    vpn/
      MihomoVpnService.kt
      TunSession.kt
~~~

The vpn package may exist as a placeholder but is implemented only in the Strict-mode phase.

Only runtime/libmihomo adapter classes may import io.github.oviron.libmihomo.

## 5. Domain-facing Mihomo API

The rest of EinkBro depends on a stable application interface rather than the pre-1.0 library API.

Suggested interface:

~~~kotlin
interface MihomoEngine {
    val state: StateFlow<MihomoState>

    suspend fun load()
    suspend fun start(profile: MihomoProfile)
    suspend fun reload(profile: MihomoProfile)
    suspend fun stop()

    suspend fun proxies(): List<ProxyGroup>
    suspend fun selectProxy(group: String, proxy: String)
    suspend fun testDelay(proxy: String, url: String, timeoutMs: Int): Int
    suspend fun traffic(): TrafficSnapshot
    suspend fun connections(): List<ProxyConnection>
    suspend fun closeConnection(id: String)
    suspend fun closeAllConnections()

    suspend fun updateProvider(type: String, name: String)
    suspend fun setRoutingMode(mode: RoutingMode)
}
~~~

LibMihomoEngine maps these operations to:

- Clash.load
- Clash.bridgeABI
- Clash.quickSetup
- Clash.invokeAction
- Clash.getTraffic
- Clash.getTotalTraffic
- Clash.setEventListener
- Clash.suspended

All callback APIs MUST be adapted to coroutines without blocking libmihomo callback threads.

## 6. Native action client

LibMihomoActionClient is the single place that understands the JSON action protocol.

It should expose typed methods for the libmihomo actions actually used by the app.

Initial action coverage:

- getProxies
- changeProxy
- testDelay
- probeCurrentProxyIp
- queryProxyGroupOrder
- queryExternalProviders
- getExternalProvider
- updateExternalProvider
- getConnections
- closeConnection
- closeAllConnections
- getTraffic
- getTotalTraffic
- updateConfig
- setupConfig
- validateConfig
- startListener
- stopListener

Do not let ViewModels construct action JSON strings.

Every action must:

1. have a unique request ID,
2. parse the returned action envelope,
3. validate code == 0,
4. map JSON payloads to typed application models,
5. convert native/library errors into MihomoException subclasses.

## 7. Runtime state machine

Use one serialized owner of the core.

~~~text
STOPPED
   -> LOADING
   -> STARTING
   -> RUNNING
   -> RELOADING
   -> RUNNING
   -> STOPPING
   -> STOPPED

Any state
   -> ERROR
~~~

Suggested state:

~~~kotlin
sealed interface MihomoState {
    data object Stopped : MihomoState
    data object Loading : MihomoState
    data object Starting : MihomoState

    data class Running(
        val socksEndpoint: ProxyEndpoint,
        val controllerEndpoint: ControllerEndpoint,
        val profileId: String
    ) : MihomoState

    data object Reloading : MihomoState
    data object Stopping : MihomoState
    data class Error(val cause: Throwable) : MihomoState
}
~~~

Requirements:

- load is process-once and idempotent.
- start/stop/reload are serialized by Mutex or an equivalent single-owner mechanism.
- multiple Activity/ViewModel calls cannot create multiple runtimes.
- bridgeABI is checked before the first setup.
- an ABI mismatch is fatal and fail-closed.
- process lifecycle suspension may use Clash.suspended where safe.

## 8. Default SOCKS5 runtime

### 8.1 Listener configuration

Generated runtime configuration MUST force:

~~~yaml
allow-lan: false
bind-address: 127.0.0.1

port: 0
mixed-port: 0
socks-port: <app-owned-runtime-port>

external-controller: 127.0.0.1:<app-owned-controller-port>
secret: <application-generated-secret>
~~~

Any imported profile values that conflict with these application security invariants are overridden in the generated runtime profile.

### 8.2 Runtime ports

Do not assume 7890/9090 are always free.

PortAllocator should:

1. choose from a private high-port range,
2. verify loopback availability,
3. generate the runtime config,
4. start mihomo,
5. health-check the actual listener,
6. retry with a new port on bind failure.

Persisting the last successful ports is optional; correctness is more important than port stability.

### 8.3 WebView proxy binding

Use AndroidX WebKit ProxyController with feature detection.

The proxy rule is SOCKS to the running MihomoState endpoint.

Never use:

- Java system proxy properties
- reflection-based WebView proxy hacks
- a device-wide Android proxy setting

ProxyController operations are application-process concerns and belong in :core-network.

## 9. Browser startup and fail-closed gate

Mandatory startup order when automatic proxying is enabled:

~~~text
Application
  -> load proxy preferences
  -> resolve active profile
  -> BrowserNetworkGateway = Blocked/Starting
  -> load libmihomo
  -> verify bridgeABI
  -> build safe runtime config
  -> validate config
  -> start mihomo
  -> verify SOCKS listener
  -> verify controller
  -> apply WebView SOCKS proxy
  -> BrowserNetworkGateway = Ready
  -> restore tabs
  -> process external-intent URL
~~~

Never restore network tabs before proxy readiness.

On startup failure:

~~~text
mihomo failure
  -> proxy rule remains pointed at a non-direct path or network gate remains blocked
  -> BrowserNetworkGateway.Error
  -> browser shows recovery UI
~~~

Recovery UI:

- Retry
- Choose another profile
- Open diagnostics
- Explicitly use Direct temporarily

Silent direct fallback is forbidden by default.

## 10. Profile and subscription pipeline

Treat imported and downloaded YAML as untrusted input.

Store original and generated configurations separately.

Suggested storage:

~~~text
filesDir/mihomo/
  profiles/
    <profile-id>/
      source.yaml
      metadata.json
  runtime/
    config.yaml
    last-known-good.yaml
  providers/
  state/
~~~

Required pipeline:

~~~text
source
  -> temporary file
  -> maximum-size check
  -> YAML/basic syntax validation
  -> libmihomo validateConfig
  -> RuntimeConfigBuilder
  -> enforce security overrides
  -> write runtime temp file
  -> fsync
  -> atomic rename
  -> setup/reload
  -> health check
  -> mark last-known-good
~~~

A failed profile/subscription update MUST NOT destroy the currently working runtime config.

Subscription URLs and tokens are secrets and MUST be redacted from logs.

## 11. Native quick proxy UI

Unlike the previous draft, the MVP SHOULD provide a small native quick proxy UI because libmihomo already exposes the required in-process actions.

The quick panel may contain:

~~~text
Proxy
  Status
  Active profile
  Routing mode
  Proxy group
  Selected proxy
  Delay
  Current traffic

  [Change proxy]
  [Test delay]
  [Open Zashboard]
~~~

Proxy group/selector data comes from getProxies and queryProxyGroupOrder.

Changing a selector uses changeProxy.

Delay uses testDelay.

Do not implement a second full provider/rules/connection dashboard in Compose.

## 12. Zashboard integration

Bundle a pinned Zashboard build inside the APK.

Prefer the no-fonts distribution where compatible with the selected release.

Suggested path:

~~~text
app/src/main/assets/zashboard/
  index.html
  assets/
~~~

Load local assets through WebViewAssetLoader under an HTTPS appassets origin.

Do not load the hosted Zashboard in normal product operation.

### 12.1 Dedicated WebView

Zashboard uses its own dedicated WebView.

Do not reuse an arbitrary browser tab or EBWebView instance.

Dashboard policy:

- JavaScript enabled
- DOM storage enabled only as required
- file access disabled
- content access disabled
- mixed content disabled
- release WebView debugging disabled
- no arbitrary addJavascriptInterface
- navigation restricted to appassets and expected loopback controller interactions
- external links leave the dashboard container

### 12.2 Controller

Zashboard connects to:

~~~text
127.0.0.1:<controller-port>
~~~

with an application-generated secret.

The controller MUST:

- bind only to loopback
- require a strong random secret
- never expose the secret in normal logs
- never be enabled on 0.0.0.0 by imported profiles

Launch Zashboard with core/TUN management disabled where supported:

~~~text
disableUpgradeCore=1
disableTunMode=1
~~~

Core lifecycle and Android VPN lifecycle remain application responsibilities.

## 13. Controller secret storage

Generate at least 256 bits of cryptographically secure random data.

Store the secret only in application-private storage.

Android Keystore may protect an encryption key used for sensitive preferences if needed, but the implementation should not add an unnecessary crypto framework merely to hide data already protected by the application sandbox.

Never place the secret in:

- browser history
- crash reports
- analytics
- normal logcat
- exported files
- subscription metadata shown to other apps

## 14. Strict application-only VPN mode

Strict mode is a later phase.

Route:

~~~text
EinkBro process
  -> Android VpnService
  -> TUN fd
  -> Clash.startTUN
  -> mihomo
  -> Internet
~~~

MihomoVpnService implements TunInterface.

Critical requirements:

- protect(fd) delegates to VpnService.protect(fd)
- addAllowedApplication captures only this browser by default
- TUN fd ownership is explicit
- onRevoke stops the session
- process death is handled
- foreground-service requirements for supported Android versions are satisfied
- DNS, IPv4, IPv6, TCP and UDP are tested

Strict mode must not be presented as complete until the socket-protection integration test passes.

## 15. Persistence boundaries

Use Room for structured metadata:

- profiles
- subscriptions
- update timestamps
- update status

Use DataStore/preferences for small settings:

- proxy enabled
- active profile
- auto-start
- browser-proxy vs strict mode
- fail-closed policy
- optional direct fallback preference

Use private files for:

- YAML
- runtime config
- provider files
- mihomo databases/cache

Do not store large YAML blobs in Room.

## 16. Logging and privacy

No new analytics or telemetry is required for this feature.

Never log:

- controller secrets
- subscription URL tokens
- proxy passwords
- UUIDs/private keys
- authorization headers
- cookies
- page form data

SensitiveValueRedactor must sanitize diagnostics before sharing/export.

Normal diagnostics may include:

- app version
- libmihomo wrapper version
- bundled mihomo version
- bridgeABI
- active profile ID/name
- listener readiness
- controller readiness
- WebView proxy feature support
- routing mode
- redacted error messages

## 17. ABI and APK-size policy

libmihomo currently supports:

- arm64-v8a
- armeabi-v7a
- x86_64

The mihomo-enabled application MUST NOT advertise x86 because no matching native library is shipped.

Release preference:

- arm64-v8a APK
- armeabi-v7a APK
- x86_64 APK
- AAB for Play distribution

A universal APK may be published for convenience but should not be the primary download because the Go core is large per ABI.

The inspected v0.3.2-alpha.20260827 AAR is approximately 45.8 MB before final APK packaging. The project must accept that embedding mihomo materially increases binary size.

Do not compromise architecture or security merely to preserve the original small EinkBro APK size.

## 18. Dependency verification

Release CI MUST verify:

### libmihomo

- exact wrapper version
- expected SHA-256
- expected bridgeABI
- expected supported ABIs
- bundled mihomo version from release metadata when available

### Zashboard

- exact tag/release or commit
- exact SHA-256 of the bundled distribution
- no floating latest download

The pinned dependency information should live in one build/third-party manifest rather than being duplicated across scripts.

## 19. Security boundaries

~~~text
Untrusted Internet
      |
      v
Browser WebView
      |
      X  no privileged JS bridge
      |
Dedicated Dashboard WebView
      |
      v
Loopback Controller + secret
      |
      v
Embedded mihomo
~~~

Browser pages must never receive privileged Android bridge methods for proxy control.

Zashboard must never receive arbitrary Android APIs.

Controller authentication remains required even though it binds to loopback.

## 20. Testing requirements

### 20.1 Unit tests

Required:

- LibMihomoActionClient envelope/error parsing
- state-machine serialization/idempotency
- bridgeABI mismatch handling
- SOCKS/controller port allocation retry
- runtime security overrides
- fail-closed startup policy
- last-known-good config rollback
- subscription token redaction
- proxy model mapping
- routing-mode mapping

### 20.2 Android integration tests

Required for browser-proxy MVP:

1. Load libmihomo on a supported ABI.
2. Start a minimal known profile.
3. Verify SOCKS listener readiness.
4. Verify controller readiness.
5. Apply AndroidX WebKit SOCKS proxy.
6. Load a test HTTPS page through the proxy.
7. Switch a selector using invokeAction.
8. Verify the selected proxy changed.
9. Stop the local listener/core.
10. Verify WebView does not silently use direct networking under fail-closed policy.
11. Open bundled Zashboard without remote dashboard assets.
12. Verify Zashboard can access the controller with the generated secret.

### 20.3 Strict-mode release blockers

Before Strict mode is considered production-ready:

- VpnService.protect callback works
- no routing loop
- application-only capture works
- IPv4 works
- IPv6 works
- TCP works
- UDP works
- DNS works
- WebSocket works
- QUIC/HTTP3 behavior is documented/tested
- revoke/process-death cleanup works

## 21. CI requirements

Pull-request CI should include:

- existing EinkBro unit/lint/build checks
- core-mihomo unit tests
- core-network unit tests
- dependency pin verification
- supported-ABI validation

Release CI should include:

~~~text
verify libmihomo hash/metadata
verify Zashboard hash
run tests
run lint
assemble supported ABI APKs
assemble AAB where applicable
run R8/release build
emit checksums
emit third-party version manifest
~~~

Do not add broad flaky tests merely to increase test count. Every test must protect a real boundary or regression risk.

## 22. Non-goals for the initial MVP

The initial MVP will not:

- become a general system-wide Clash client
- proxy unrelated Android apps
- maintain a custom mihomo JNI bridge inside EinkBro
- download/update executable cores at runtime
- allow Zashboard to upgrade the core
- expose LAN proxy/controller access
- build a full duplicate Compose dashboard
- silently bypass mihomo on failure

## 23. MVP definition of done

MVP is complete only when:

- [ ] EinkBro baseline browser functions still work.
- [ ] Exact libmihomo AAR is pinned and hash-verified.
- [ ] Unsupported x86 ABI is removed from mihomo-enabled release output.
- [ ] bridgeABI is validated before runtime setup.
- [ ] mihomo starts automatically for an active profile.
- [ ] runtime config forces loopback-only SOCKS and controller endpoints.
- [ ] WebView uses SOCKS5 through AndroidX WebKit ProxyController.
- [ ] restored tabs cannot race ahead of proxy startup.
- [ ] fail-closed behavior is the default.
- [ ] local YAML profiles work.
- [ ] subscription updates are validated and atomic.
- [ ] last-known-good rollback works.
- [ ] native quick proxy group selection works through invokeAction.
- [ ] native delay testing works.
- [ ] traffic/status display works.
- [ ] Zashboard is bundled locally.
- [ ] Zashboard connects to the authenticated loopback controller.
- [ ] Zashboard cannot upgrade core or own Android TUN lifecycle.
- [ ] secrets/tokens are redacted from normal diagnostics.
- [ ] release artifacts are produced only for supported ABIs.
- [ ] proxy-stop/failure leak test passes.

## 24. Final ownership rule

~~~text
EinkBro / Compose
  = browser and Android UX

core-network
  = browser readiness, ProxyController, fail-closed gate

core-mihomo
  = lifecycle, profiles, security, typed libmihomo adapter

libmihomo-android
  = native mihomo bridge/runtime

Zashboard
  = complete advanced mihomo management UI

mihomo
  = proxy protocols, routing, DNS, providers and connections
~~~

Keep these ownership boundaries explicit. Do not reimplement a responsibility in another layer unless a documented technical limitation requires it.
