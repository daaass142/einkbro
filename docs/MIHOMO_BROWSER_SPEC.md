# EinkBro + mihomo + Zashboard Browser Specification

Status: implementation-ready architecture specification  
Branch: `feat/mihomo-browser`  
Base application: EinkBro  
Proxy runtime: `daaass142/libmihomo-android`  
Proxy dashboard: `Zephyruso/zashboard`

## 1. Goal

Build an Android browser on top of EinkBro that starts an embedded mihomo core automatically and routes browser traffic through that core by default.

The product is a browser with an embedded network engine, not a general-purpose VPN client with a browser bolted on.

Primary requirements:

- Preserve EinkBro's existing browser, E-Ink, reader, tab, download, ad-filtering and settings features.
- Start mihomo before restored tabs or external URLs are allowed to make network requests.
- Route WebView traffic through mihomo automatically.
- Provide an optional strict mode backed by Android `VpnService` for traffic that cannot be guaranteed to follow a WebView HTTP proxy.
- In strict mode, route this application only by default; do not capture unrelated applications.
- Embed Zashboard locally as the full mihomo management UI.
- Support proxy groups, selectors, URL tests, providers, connections, rules, traffic and other normal mihomo dashboard functions through Zashboard instead of duplicating them in Compose.
- Support local profiles and subscription URLs.
- Default to fail-closed behavior when proxy startup fails.
- Keep controller and proxy listeners loopback-only unless the user explicitly enables a future LAN-server feature.

## 2. Existing codebase constraints

EinkBro currently uses:

- Kotlin
- Jetpack Compose
- Android WebView
- MVVM
- Koin
- Room
- KSP
- Java 17
- minSdk 24
- modules `:app`, `:ad-filter`, `:adblock-client`

The implementation MUST extend these conventions instead of adding a second DI, reactive or UI architecture.

Do not introduce Hilt/Dagger, RxJava, Redux/MVI frameworks, Retrofit, or a second JSON stack without a demonstrated requirement.

`libmihomo-android` is consumed as an Android AAR. It already contains the JNI/CGo bridge and exposes a Kotlin facade. The application MUST NOT fork or copy that JNI bridge into EinkBro.

Important library properties at the time this specification was written:

- facade entry point: `io.github.oviron.libmihomo.Clash`
- lifecycle/API methods include `load`, `quickSetup`, `invokeAction`, `startTUN`, `stopTun`, `setEventListener`, `getTraffic`, `getTotalTraffic`, `suspended`, `updateDNS`
- strict VPN integration is exposed through `TunInterface`, including outbound socket `protect(fd)` callbacks
- supported ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- minSdk of the library is 21, which is compatible with EinkBro's minSdk 24
- host AGP must satisfy the library's current 16 KiB alignment requirement (currently documented as AGP 8.5.1+)
- the native bridge exposes a `bridgeABI()` compatibility check

The AAR version MUST be pinned exactly. Never use `latest`, a wildcard version, or an unverified moving artifact in release builds.

## 3. Architecture decision

Use a small modular-monolith extension to EinkBro:

```text
:app
  |-- browser UI / EBWebView / tabs / navigation
  |-- native proxy overview and Android-only settings
  |
  +--> :core-network
  |       |-- BrowserNetworkGateway
  |       |-- WebView proxy integration
  |       `-- fail-closed readiness gate
  |
  +--> :core-mihomo
          |-- lifecycle
          |-- profile/config pipeline
          |-- controller adapter
          |-- VPN/TUN service
          `--> libmihomo-android AAR
```

Do not create a separate `mihomo-runtime` Gradle module unless a later implementation requires runtime-swappable AARs. The AAR already is the native runtime boundary; `:core-mihomo` is the application-side adapter boundary.

Add only these new Gradle modules for the initial implementation:

```text
:core-network
:core-mihomo
```

Keep proxy UI in `:app` initially. A separate `:feature-proxy` module is optional and should only be introduced if the UI grows enough to justify it.

## 4. Responsibility boundaries

### `:app`

Owns:

- existing EinkBro browser UI
- browser tabs and navigation
- Compose proxy overview
- profile selection UI
- Android VPN permission UX
- Dashboard screen/container
- opening local Zashboard

Must not:

- call `Clash.*` directly
- parse or mutate runtime YAML directly
- own VPN/TUN file descriptors
- implement proxy group business logic already provided by Zashboard

### `:core-network`

Owns the question: "may the browser access the network now?"

Public API:

```kotlin
interface BrowserNetworkGateway {
    val state: StateFlow<BrowserNetworkState>
    suspend fun prepare()
    suspend fun shutdown()
}

sealed interface BrowserNetworkState {
    data object Stopped : BrowserNetworkState
    data object Starting : BrowserNetworkState
    data object Ready : BrowserNetworkState
    data class Error(val cause: Throwable) : BrowserNetworkState
}
```

Also owns a WebView proxy adapter built on AndroidX WebKit `ProxyController` when supported.

The browser MUST not restore network tabs until `BrowserNetworkState.Ready` when proxy-on-start is enabled.

### `:core-mihomo`

Owns all application interaction with libmihomo:

```text
core-mihomo/
  src/main/java/.../
    api/
      MihomoManager.kt
      MihomoState.kt
      ProxyTransportMode.kt
    runtime/
      LibMihomoAdapter.kt
      MihomoManagerImpl.kt
    config/
      ProfileRepository.kt
      RuntimeConfigBuilder.kt
      ConfigValidator.kt
      ConfigStore.kt
    controller/
      MihomoController.kt
      LocalControllerClient.kt
    vpn/
      MihomoVpnService.kt
      VpnPermissionManager.kt
      TunSession.kt
      SocketProtector.kt
```

Only `runtime/LibMihomoAdapter` may import `io.github.oviron.libmihomo.*`.

## 5. Mihomo adapter

Wrap the third-party facade so the rest of EinkBro does not depend on unstable pre-1.0 API details.

```kotlin
interface MihomoRuntime {
    suspend fun load()
    suspend fun start(profile: File)
    suspend fun stop()
    suspend fun invoke(action: String): String
    suspend fun startTun(config: TunConfig)
    suspend fun stopTun()
    fun setSuspended(value: Boolean)
}
```

`LibMihomoAdapter` maps these calls to:

- `Clash.load(...)`
- `Clash.bridgeABI()` / `Clash.EXPECTED_BRIDGE_ABI`
- `Clash.quickSetup(...)`
- `Clash.invokeAction(...)`
- `Clash.startTUN(...)`
- `Clash.stopTun()`
- `Clash.suspended(...)`

All callback APIs must be converted to suspend functions using cancellable coroutine adapters. Do not block libmihomo callback threads.

On startup:

1. Load native libraries exactly once per process.
2. Verify `bridgeABI()` before starting the core.
3. Fail closed on ABI mismatch.
4. Initialize/apply the generated runtime profile.
5. Health-check the local listener/controller.
6. Only then mark browser networking Ready.

## 6. Runtime state machine

Use a single state machine rather than several booleans:

```text
STOPPED -> STARTING -> RUNNING -> STOPPING -> STOPPED
                |          |
                +-> ERROR <-+
```

Suggested model:

```kotlin
sealed interface MihomoState {
    data object Stopped : MihomoState
    data object Starting : MihomoState
    data object Running : MihomoState
    data object Stopping : MihomoState
    data class Error(val cause: Throwable) : MihomoState
}
```

`start()` and `stop()` must be idempotent and serialized with a `Mutex` or equivalent single-owner mechanism. Multiple Activity/ViewModel calls must never create multiple cores.

## 7. Traffic modes

### 7.1 Browser Proxy mode - default

Default route:

```text
EBWebView
  -> AndroidX WebKit ProxyController
  -> 127.0.0.1:<mihomo mixed port>
  -> mihomo
  -> selected proxy / rule / direct
```

This mode is the default because it does not require Android VPN permission and best preserves EinkBro's lightweight behavior.

Use AndroidX WebKit feature detection. Never use reflection-based WebView proxy hacks or global `System.setProperty` proxy settings.

The generated mihomo runtime config must force the browser-facing listener to loopback only:

```yaml
allow-lan: false
bind-address: 127.0.0.1
mixed-port: <app-owned-port>
```

Do not hard-code common Clash ports such as 7890 if avoidable. The port belongs to the app runtime configuration.

Browser Proxy mode is not presented as a cryptographic guarantee that every possible Android/WebView socket path is captured. Users needing complete capture use Strict mode.

### 7.2 Strict mode - application-only VPN

Strict mode route:

```text
EinkBro process
  -> Android VpnService
  -> TUN fd
  -> libmihomo `startTUN`
  -> mihomo outbound
  -> Internet
```

`MihomoVpnService` implements libmihomo `TunInterface`.

Critical rule: `TunInterface.protect(fd)` MUST delegate to `VpnService.protect(fd)` so mihomo outbound sockets do not loop back into its own TUN.

Use `VpnService.Builder.addAllowedApplication(applicationContext.packageName)` so only this browser is captured by default.

Do not implement a system-wide VPN as the initial product behavior.

Strict mode must correctly handle:

- VPN permission grant/revoke
- foreground-service lifecycle
- process death
- `onRevoke()`
- TUN fd ownership/closure
- socket protection
- IPv4/IPv6
- DNS
- TCP/UDP
- WebSocket
- QUIC/HTTP3 validation

Strict mode is not considered complete until the outbound socket-protection path has an integration test.

## 8. Fail-closed startup

If the user has automatic proxying enabled, startup order is mandatory:

```text
Application
 -> load settings
 -> load active profile
 -> build safe runtime config
 -> start mihomo
 -> verify core/controller/listener
 -> configure WebView proxy or VPN
 -> BrowserNetworkGateway = Ready
 -> restore tabs / open external intent URL
```

Never restore tabs first and attach the proxy afterwards; that creates a direct-connect leak window.

Default failure behavior:

```text
mihomo unavailable -> BrowserNetworkGateway.Error -> network blocked
```

UI offers explicit actions:

- Retry
- Temporarily allow direct access

Direct fallback must never happen silently by default.

## 9. Profiles and configuration pipeline

Treat subscription/local YAML as untrusted input.

Do not execute a downloaded profile directly.

Required pipeline:

```text
Profile source
 -> download/read to temporary file
 -> basic size/type validation
 -> parse/validate
 -> RuntimeConfigBuilder
 -> force application safety overrides
 -> libmihomo validation/setup check
 -> atomic replacement
 -> active runtime config
```

Store:

```text
filesDir/mihomo/
  profiles/
    <profile-id>.yaml
  runtime/
    config.yaml
  providers/
  state/
```

The original imported profile and generated runtime profile must remain separate.

Required runtime security overrides regardless of subscription contents:

```yaml
allow-lan: false
bind-address: 127.0.0.1
external-controller: 127.0.0.1:<controller-port>
secret: <application-generated-secret>
```

A remote subscription must never be allowed to change the controller to `0.0.0.0`, disable its secret, or expose the browser's local proxy to the LAN.

Profile update must use temporary-file + validation + atomic rename semantics. A failed subscription update must leave the last known-good configuration running.

## 10. Controller strategy

Zashboard expects Clash/mihomo HTTP/WebSocket APIs, so expose mihomo's controller on loopback only.

Native Compose code should not duplicate the entire controller API. Define a tiny `MihomoController` only for Android-native quick operations such as:

```kotlin
interface MihomoController {
    suspend fun health(): Boolean
    suspend fun version(): String
    suspend fun getMode(): RoutingMode
    suspend fun setMode(mode: RoutingMode)
}
```

Use Zashboard for complete proxy-group/provider/rules/connections management.

Generate a strong random controller secret during application setup and keep it in private app storage. Never log it.

## 11. Zashboard integration

Bundle Zashboard locally. Do not depend on the hosted dashboard.

Prefer its `dist-no-fonts` release because it is small and has no CDN font dependency.

Suggested tree:

```text
app/src/main/assets/zashboard/
  index.html
  assets/
  ...
```

Pin a specific Zashboard release and record its checksum/version under a third-party manifest. Do not download `latest` during an ordinary release build without pinning and verification.

Load it through `WebViewAssetLoader`, for example under:

```text
https://appassets.androidplatform.net/zashboard/
```

Do not use `file:///android_asset/...`.

Create a dedicated Dashboard WebView. Do not reuse an ordinary browser tab/`EBWebView` instance because local trusted application content and arbitrary internet content have different security models.

Dashboard WebView policy:

- JavaScript enabled
- DOM storage enabled if required by Zashboard
- file access disabled
- content access disabled
- mixed content disabled
- release WebView debugging disabled
- navigation limited to the local appassets origin and loopback controller/API needs
- external links leave the Dashboard container
- no `addJavascriptInterface` in the first implementation

Zashboard supports setup parameters including `hostname`, `port`, `secret`, `disableUpgradeCore`, and `disableTunMode`.

The embedded launch must set:

```text
disableUpgradeCore=1
disableTunMode=1
```

Reasons:

- Core updates are application/release responsibilities and must not be performed by a Web UI.
- Android TUN requires `VpnService`, permission and fd lifecycle; a dashboard toggle cannot safely own it.

Initial implementation may pass localhost/controller credentials through Zashboard's supported setup flow. If the URL representation becomes a security/UX concern, add the smallest possible upstream-compatible bootstrap adaptation later; do not maintain a large Zashboard fork.

## 12. Native proxy UI

Compose should provide Android/browser-specific controls only:

```text
Proxy
  Status: Connected / Starting / Error / Off
  Active profile
  Transport: Browser Proxy / Strict VPN
  Routing mode: Rule / Global / Direct
  [Open Proxy Dashboard]
  Profiles
  Auto start
  Failure policy
  Diagnostics
```

Do not build Compose copies of:

- proxy groups
- node list
- providers
- rules
- connection table
- traffic charts
- full logs
- latency dashboard

Those are Zashboard's responsibility.

A small native quick selector may be considered later only as convenience UI, not as a second management implementation.

## 13. Dependencies

Reuse existing EinkBro dependencies wherever possible.

New direct dependencies should be minimal:

- `androidx.webkit:webkit` - WebView `ProxyController`, `WebViewAssetLoader`, feature detection
- `androidx.datastore:datastore-preferences` - proxy startup/mode/failure settings if not already available
- `kotlinx-coroutines-android` - runtime lifecycle adapters, if not already present
- `kotlinx-serialization-json` - local mihomo controller DTOs only if the project has no existing suitable JSON stack
- a single existing HTTP client for subscription/controller calls; add OkHttp only if EinkBro has no reusable client
- pinned `libmihomo-android` AAR

Do not add Retrofit merely for a small loopback controller client.

All versions must live in the existing version catalog/build convention rather than being duplicated in feature files.

## 14. libmihomo distribution and verification

Use a pinned `daaass142/libmihomo-android` release artifact.

Release build requirements:

1. Pin wrapper version.
2. Pin expected bundled mihomo version/bridge ABI.
3. Verify SHA-256 before consuming the AAR.
4. Where practical, verify the release signature described by the library repository.
5. Fail CI on checksum or bridge-ABI mismatch.
6. Record the effective libmihomo and mihomo versions in the app About/Diagnostics information.

Never build a release by downloading an unpinned artifact from a moving `latest` URL.

## 15. ABI packaging

`libmihomo-android` currently supports:

```text
arm64-v8a
armeabi-v7a
x86_64
```

EinkBro currently also declares x86 support. The mihomo-enabled variant must not advertise an ABI for which libmihomo has no native library.

Recommended release artifacts:

```text
browser-arm64-v8a.apk
browser-armeabi-v7a.apk
browser-x86_64.apk
```

A universal APK may be provided for convenience, but ABI-specific APKs should be preferred because the Go core is large per ABI.

## 16. Persistence boundaries

Use Room for structured records such as:

- profile metadata
- subscription metadata
- update timestamps/status

Use DataStore/preferences for small user settings:

- proxy enabled
- active profile ID
- transport mode
- auto start
- fail-closed policy

Use private files for:

- actual YAML profiles
- generated runtime config
- provider files
- mihomo runtime databases

Do not store large YAML blobs in Room merely for convenience.

## 17. Logging and sensitive data

Never log or send to analytics/crash reporting:

- controller secret
- subscription URL tokens
- proxy passwords
- UUIDs/keys
- Authorization headers
- cookies
- browsing history generated by mihomo connections
- page form data

Diagnostics must redact secrets and subscription query parameters.

Debug mihomo logging, if exposed later, must be explicit and temporary rather than always-on.

## 18. Android permissions

Keep permissions minimal.

Expected additions:

- `INTERNET` already required by the browser
- foreground-service declarations required by strict VPN mode on supported Android versions
- `VpnService` declaration with the platform VPN bind permission

Do not request location, contacts, phone state, storage-all-files, package installation, or unrelated permissions for proxy functionality.

The project rule that forbids in-app APK self-update remains unchanged. Zashboard's core-upgrade UI must therefore stay disabled.

## 19. Testing requirements

### Unit tests

Required:

- runtime state-machine serialization/idempotency
- `bridgeABI` mismatch handling
- runtime security overrides
- profile failure preserves last known-good config
- atomic config replacement
- secret/token redaction
- fail-closed policy
- transport-mode switching state

### Integration tests

Required before MVP release:

1. Load libmihomo on a supported ABI.
2. Apply a minimal known profile.
3. Verify mixed listener/controller readiness.
4. Load embedded Zashboard without internet-hosted assets.
5. Verify Zashboard can read proxies through REST/WebSocket.
6. Switch a selector and observe the active proxy change.
7. Kill/stop mihomo and verify WebView cannot silently fall back to direct when fail-closed is enabled.

### Strict-mode release blockers

Validate at least:

- application-only capture
- `VpnService.protect()` callback path
- no outbound routing loop
- IPv4
- IPv6
- TCP
- UDP
- DNS
- WebSocket
- WebRTC/QUIC behavior where supported
- VPN revoke/process-death cleanup

## 20. CI and supply-chain requirements

PR CI should run existing EinkBro checks plus proxy-specific tests.

Release CI should:

```text
verify pinned libmihomo artifact/hash
verify pinned Zashboard artifact/hash
run unit tests
run lint
assemble supported ABI APKs
run release checks/R8
publish checksums
```

Do not add meaningless UI snapshots or broad flaky E2E suites merely to increase test count. Tests should protect an actual boundary or regression risk.

## 21. Implementation phases

### Phase 1 - MVP browser proxy

- add `:core-mihomo`
- add `:core-network`
- pin/integrate libmihomo AAR
- runtime lifecycle adapter
- profile storage/import
- safe runtime config builder
- start mihomo before browser restoration
- AndroidX WebKit proxy integration
- fail-closed network gate
- embedded local Zashboard
- native proxy overview/settings
- Rule/Global/Direct quick switch
- diagnostics/version display

### Phase 2 - strict mode

- `MihomoVpnService`
- `TunInterface`
- socket protect callback
- application-only VPN
- TUN fd lifecycle
- foreground service
- strict-mode DNS/UDP/IPv6 tests

### Phase 3 - polish

- background subscription refresh with WorkManager
- optional native quick selector
- E-Ink-specific Dashboard reduced-motion/theme tweaks
- deeper diagnostics
- battery/runtime suspension tuning with `Clash.suspended(...)`

## 22. Definition of done for MVP

MVP is complete only when all of these are true:

- [ ] EinkBro's existing browsing behavior still works.
- [ ] mihomo automatically starts when configured.
- [ ] libmihomo `bridgeABI` is validated before use.
- [ ] restored tabs cannot race ahead of proxy startup.
- [ ] WebView uses the app-owned local mihomo listener in Browser Proxy mode.
- [ ] proxy failure is fail-closed by default.
- [ ] local YAML can be imported.
- [ ] a subscription profile can be downloaded and safely updated.
- [ ] an invalid update cannot destroy the active configuration.
- [ ] runtime config forcibly keeps LAN/controller exposure disabled.
- [ ] Zashboard is bundled in the APK and opens without its hosted site.
- [ ] Zashboard can manage proxy groups/selectors/providers/rules/connections supported by the core.
- [ ] Zashboard cannot update the core.
- [ ] Zashboard cannot toggle Android TUN directly.
- [ ] controller is loopback-only and authenticated.
- [ ] release builds do not expose WebView debugging.
- [ ] secrets/tokens are absent from normal logs.
- [ ] libmihomo and Zashboard versions are pinned and verifiable.
- [ ] APKs are produced only for ABIs supported by the bundled native core.

## 23. Non-goals

The initial implementation will not:

- become a general device-wide Clash client
- replace Zashboard with a full Compose proxy manager
- add an in-app APK/core updater
- expose a LAN proxy/controller by default
- maintain its own mihomo CGo/JNI fork inside EinkBro
- silently use direct networking when the configured proxy fails

## 24. Final architecture rule

Keep ownership explicit:

```text
EinkBro / Compose = browser and Android UX
core-network       = browser network readiness and WebView proxy binding
core-mihomo        = lifecycle/configuration/security/VPN adapter
libmihomo-android  = native mihomo bridge/runtime
Zashboard          = complete mihomo management UI
mihomo             = routing/proxy engine
```

If a feature belongs to one of these layers, do not reimplement it in another layer without a concrete technical reason.
