# EinkBro + mihomo Android Browser — Architecture & Implementation Spec

> Status: Proposed  
> Target repository: `daaass142/einkbro`  
> Target branch: `spec/mihomo-browser`  
> Base: EinkBro current `main`  
> Dashboard: Zephyruso/zashboard  
> Scope: Browser traffic automatically proxied through an embedded mihomo core, with proxy-group/config management available inside the app.

---

## 1. Goals

Build an Android browser on top of the existing EinkBro codebase that:

1. Keeps EinkBro's current WebView-based browser architecture and existing reader/e-ink features.
2. Starts an embedded mihomo core automatically with the browser.
3. Sends browser WebView traffic through mihomo by default.
4. Never silently falls back to a direct connection when proxy mode is configured as required.
5. Provides profile import/update and active-profile switching.
6. Lets users manage proxy groups, node selection, mode, connections, traffic and logs through an embedded Zashboard UI.
7. Does not expose the mihomo REST controller or mixed proxy port to LAN.
8. Keeps browser/proxy responsibilities separated so upstream EinkBro updates remain mergeable.
9. Uses reproducible, pinned third-party dependencies/assets.
10. Preserves ABI-split builds so the native core does not force users to install a large universal APK.

## 2. Non-goals for MVP

The first implementation MUST NOT:

- Proxy other Android applications.
- Register a system-wide VPN.
- Require root.
- Bind mihomo ports to `0.0.0.0`.
- Expose the controller over Wi-Fi/LAN.
- Download arbitrary dashboard code at runtime.
- Modify Zashboard source unless an integration bug requires it.
- Replace EinkBro's existing browser UI with a new browser shell.
- Reimplement Clash/Mihomo group management natively when Zashboard already provides it.

A future system-wide/per-app TUN mode may be added as a separate feature using `VpnService`, but it is explicitly outside the MVP.

---

## 3. Architectural decision

### 3.1 Traffic model

Default mode is **application-local WebView proxying**, not Android VPN/TUN.

Traffic path:

```text
EinkBro WebView
    |
    | AndroidX WebKit ProxyController
    v
127.0.0.1:<mixed-port>
    |
    v
embedded mihomo core
    |
    +--> DIRECT
    +--> selected proxy / proxy group
    +--> RULE routing
    |
    v
Internet
```

Rationale:

- No VPN permission prompt.
- No persistent VPN icon.
- Does not affect other apps.
- No routing-table manipulation.
- No VPN routing loop/protect() complexity.
- Fits the product requirement: this browser automatically uses mihomo.
- Uses the already-present `androidx.webkit` dependency.

### 3.2 Fail-closed behavior

When proxy policy is `REQUIRED`:

- Apply the WebView proxy override before any external page is allowed to load.
- The override points at the configured loopback mixed port even while mihomo is still starting.
- If mihomo is unavailable, page loads fail instead of going DIRECT.
- Stopping/crashing the core MUST NOT automatically call `clearProxyOverride()`.
- Only an explicit user action that switches proxy policy to `DIRECT` may clear the proxy override.

This avoids startup races that could leak the first request outside mihomo.

### 3.3 Dashboard model

Zashboard is packaged with the application and served only through the local mihomo controller/UI endpoint.

```text
Proxy Settings
    |
    v
ProxyDashboardActivity / dedicated WebView
    |
    v
http://127.0.0.1:<controller-port>/ui/
    |
    +--> Clash REST API
    +--> Clash WebSocket API
    |
    v
embedded mihomo
```

The dashboard is not opened as a normal browser tab and is not stored in normal browser history.

---

## 4. Proposed Gradle modules

Keep the existing modules and add two focused modules:

```text
:einkbro
├── :app                  existing application/browser UI
├── :ad-filter            existing
├── :adblock-client       existing
├── :proxy-core           NEW
└── :proxy-dashboard      NEW
```

### 4.1 `:proxy-core`

Responsibilities:

- Native/JNI boundary to mihomo.
- mihomo process lifecycle.
- Runtime config generation.
- Config validation.
- Local ports.
- Controller token creation.
- Health checks.
- Status/state model.
- Profile directories.
- IPC/service boundary.

It MUST NOT depend on EinkBro browser UI classes.

### 4.2 `:proxy-dashboard`

Responsibilities:

- Pinned Zashboard distribution assets.
- Zashboard version metadata.
- MIT license/NOTICE.
- Asset installation/extraction into app-private storage.
- Dashboard URL construction.

It MUST NOT own mihomo lifecycle.

### 4.3 `:app`

Responsibilities added to the existing application:

- Proxy settings UI.
- Browser-to-proxy coordination.
- WebView proxy override.
- Profile import/update UI.
- Open embedded dashboard.
- User-facing status/errors.

Do not move unrelated EinkBro features into the new modules.

---

## 5. Proposed source layout

```text
proxy-core/
└── src/main/
    ├── java/info/plateaukao/einkbro/proxy/core/
    │   ├── api/
    │   │   ├── ProxyCore.kt
    │   │   ├── ProxyCoreState.kt
    │   │   ├── ProxyProfile.kt
    │   │   ├── ProxyPolicy.kt
    │   │   └── ProxyPorts.kt
    │   ├── service/
    │   │   ├── MihomoCoreService.kt
    │   │   ├── MihomoRuntime.kt
    │   │   └── MihomoHealthMonitor.kt
    │   ├── config/
    │   │   ├── MihomoConfigManager.kt
    │   │   ├── RuntimeConfigBuilder.kt
    │   │   └── ProfileStore.kt
    │   ├── security/
    │   │   └── ControllerTokenStore.kt
    │   └── native/
    │       └── MihomoNative.kt
    └── jniLibs/
        ├── arm64-v8a/
        ├── armeabi-v7a/
        ├── x86/
        └── x86_64/

proxy-dashboard/
└── src/main/
    ├── assets/zashboard/
    ├── java/info/plateaukao/einkbro/proxy/dashboard/
    │   ├── DashboardInstaller.kt
    │   └── DashboardUrlFactory.kt
    └── resources/META-INF/
        └── THIRD_PARTY_NOTICES

app/src/main/java/info/plateaukao/einkbro/
├── proxy/
│   ├── BrowserProxyCoordinator.kt
│   ├── WebViewProxyController.kt
│   ├── ProxyRepository.kt
│   ├── ProxyViewModel.kt
│   └── ProxyDashboardActivity.kt
└── setting/
    └── ... existing settings integration
```

Names may be adapted to existing EinkBro conventions, but the dependency direction must remain the same.

---

## 6. Core API contract

Define a small application-facing API.

```kotlin
interface ProxyCore {
    val state: StateFlow<ProxyCoreState>

    suspend fun start(profileId: String)
    suspend fun stop()
    suspend fun restart()
    suspend fun validate(profileId: String): ConfigValidationResult
    suspend fun reload(profileId: String)
}
```

Suggested states:

```text
Stopped
Starting
Ready(
    mixedPort,
    controllerPort,
    activeProfileId
)
Reloading
Error(reason)
```

No Activity, Fragment or WebView references are allowed inside `:proxy-core`.

---

## 7. Native mihomo integration

### 7.1 Source of truth

Use upstream `MetaCubeX/mihomo` as the source of truth.

Requirements:

- Pin an exact tag or commit.
- Record the commit in a machine-readable third-party manifest.
- Build native artifacts reproducibly in CI.
- Record SHA-256 checksums for all packaged native libraries.
- Generate/update third-party notices and SBOM in release CI.
- Never depend on a moving `main` branch at application build time.
- Never download an unsigned/unverified core on first launch.

Preferred structure:

```text
third_party/
├── mihomo/
│   ├── VERSION
│   ├── COMMIT
│   ├── SHA256SUMS
│   └── LICENSE
└── zashboard/
    ├── VERSION
    ├── COMMIT
    ├── SHA256SUMS
    └── LICENSE
```

### 7.2 Bridge policy

The app should own a narrow JNI bridge instead of importing an entire third-party Android VPN client.

The bridge only needs:

- start core with home/config path
- stop core
- validate config
- reload config if safely supported
- retrieve version/build info
- report fatal start error

Do not copy unrelated UI, VPN, service or hidden-API code from ClashMetaForAndroid.

### 7.3 Process isolation

Run the native core behind a dedicated bound Android service in process:

```xml
android:process=":mihomo"
android:exported="false"
```

Benefits:

- Native crash does not directly crash the browser UI process.
- Native memory is isolated.
- Lifecycle is explicit.

MVP lifecycle:

- Bind/start core when BrowserActivity enters usable foreground state and proxy policy is not `DIRECT`.
- Keep it alive while browser UI is active.
- Allow Android to reclaim it after the app leaves foreground.
- Restart and re-apply state on resume.
- Do not add a permanent foreground service notification for MVP.

If future background downloads require long-running proxy operation, design that separately under current Android foreground-service policy.

---

## 8. Port and controller policy

Use fixed app-internal defaults with collision detection:

```text
mixed-port:       17890
controller-port:  19090
```

If a port is unexpectedly occupied:

1. Check whether the listener belongs to the current core instance.
2. If not, choose from a bounded app-reserved range.
3. Persist the selected ports for the current run.
4. Notify `BrowserProxyCoordinator` before allowing navigation.

Mandatory runtime overrides:

```yaml
allow-lan: false
mixed-port: <loopback mixed port>
external-controller: 127.0.0.1:<controller port>
secret: <random per-install token>
external-ui: <private zashboard path>
tun:
  enable: false
```

The core MUST NOT bind proxy or controller ports to LAN interfaces.

---

## 9. Profile/config architecture

### 9.1 Storage

```text
filesDir/
└── mihomo/
    ├── profiles/
    │   └── <profile-id>/
    │       ├── source.yaml
    │       ├── runtime.yaml
    │       └── metadata.json
    ├── state/
    └── ui/

noBackupFilesDir/
└── mihomo/
    └── controller.token
```

Sensitive proxy configuration MUST be excluded from Android cloud backup.

### 9.2 Source vs runtime config

Never mutate a user's imported `source.yaml` in place.

Generate `runtime.yaml` from source configuration plus mandatory application overrides.

The runtime builder must force security-critical values:

- loopback-only mixed port
- loopback-only controller
- generated controller secret
- bundled Zashboard directory
- `allow-lan: false`
- `tun.enable: false` for MVP

Do not use regex/string replacement to edit YAML.

Parsing/normalization should be performed by the same Go-side config model used by the bundled mihomo version, or another safe schema-aware implementation. Unsupported config must fail validation with a clear error.

### 9.3 Profile state

Store app metadata in Room or existing app-private preferences:

```text
id
displayName
sourceType
sourceUrl?          // sensitive, never log full URL
lastUpdatedAt
etag?
lastModified?
lastValidation
lastCoreVersion
```

The full profile YAML remains a file, not a Room text blob.

---

## 10. Subscription import/update

Support:

- URL import
- local document import
- raw text/paste
- share/open-with deep link if appropriate

Network rules:

- HTTPS required by default.
- HTTP only after explicit insecure confirmation.
- Maximum config body size: 5 MiB.
- Connect/read/call timeouts.
- Maximum redirect count.
- Reject non-http(s) redirect targets for URL subscriptions.
- Redact query strings and credentials from logs.
- Download into a temporary file.
- Validate before atomic replacement.
- On validation failure, keep the last known-good profile.

Update flow:

```text
download temp
 -> validate
 -> create runtime config
 -> atomic rename
 -> reload/restart core
 -> health check
 -> commit metadata
```

If any step fails, retain the previous working configuration.

---

## 11. WebView proxy integration

Use AndroidX WebKit:

- `WebViewFeature.PROXY_OVERRIDE`
- `ProxyController`

The project already depends on `androidx.webkit`; no new browser networking library is needed for this layer.

### 11.1 Startup sequence

```text
Application/BrowserActivity created
 -> read ProxyPolicy
 -> if DIRECT: no override
 -> else apply loopback proxy override immediately
 -> start/bind MihomoCoreService
 -> wait for Ready
 -> allow/retry external navigation
```

### 11.2 Proxy policy

```kotlin
enum class ProxyPolicy {
    REQUIRED,     // default, fail closed
    PREFER_PROXY, // optional advanced mode
    DIRECT
}
```

Default: `REQUIRED`.

For `REQUIRED`, core failure must show a browser-level proxy error page rather than silently bypassing mihomo.

### 11.3 Unsupported WebView providers

If `WebViewFeature.PROXY_OVERRIDE` is unsupported:

- `REQUIRED`: block external browsing and show an actionable compatibility error.
- `PREFER_PROXY`: user may explicitly allow DIRECT.
- Do not silently downgrade.

A future `VpnService` implementation can be the compatibility fallback, but is not part of MVP.

### 11.4 Downloads and non-WebView traffic

WebView proxy override only guarantees WebView network traffic.

Any browser-owned HTTP client that fetches page-originated resources or files must explicitly use the local proxy when proxy policy requires it.

Do not assume Android `DownloadManager` inherits WebView proxy settings.

For MVP, either:

- route in-app downloads through an app-owned OkHttp downloader configured with the local HTTP proxy, or
- clearly mark system/external downloader hand-off as leaving the app's proxy guarantee.

This requirement must be covered by tests.

---

## 12. Zashboard integration

### 12.1 Versioning

Bundle a pinned `Zephyruso/zashboard` release.

Preferred flavor: `dist-no-fonts` to reduce APK size and avoid runtime font-CDN network requests.

Do not:

- load the public online dashboard
- load unpkg/CDN fonts
- use "latest" at runtime
- permit Zashboard to upgrade the core

### 12.2 Install path

At first use or version change:

```text
APK asset
 -> verify embedded manifest/version
 -> copy/extract atomically
 -> filesDir/mihomo/ui/zashboard/
```

### 12.3 Controller URL

Open the dashboard with the local controller details.

Conceptual setup URL:

```text
http://127.0.0.1:<controller-port>/ui/#/setup
  ?hostname=127.0.0.1
  &port=<controller-port>
  &secret=<controller-token>
  &disableUpgradeCore=1
  &disableTunMode=1
  &type=clash
```

If the served UI auto-detects same-origin controller details, prefer the shortest URL that avoids persisting the secret in WebView history.

### 12.4 Dedicated dashboard WebView

`ProxyDashboardActivity` must:

- be `android:exported="false"`
- use a dedicated WebView instance
- enable JavaScript because Zashboard requires it
- only allow navigation to loopback controller/UI URLs
- block external schemes
- open external documentation links in the normal browser only after explicit user action
- disable file access where not required
- disable content access where not required
- not add arbitrary JavaScript interfaces
- not share normal browsing history
- not expose the controller secret to logs

No `addJavascriptInterface` bridge is required for normal Zashboard operation.

---

## 13. Native proxy settings UI

Add a lightweight native settings surface even though Zashboard is the advanced UI.

Required screen:

```text
Settings
└── Proxy
    ├── Status: Running / Starting / Error / Stopped
    ├── Policy: Required / Prefer proxy / Direct
    ├── Active profile
    ├── Import profile
    ├── Update profile
    ├── Restart core
    ├── Open dashboard
    ├── Core version
    └── Dashboard version
```

The normal path should be:

1. import profile
2. make it active
3. core starts automatically
4. browser automatically uses it
5. open "Proxy Dashboard" for groups/nodes/rules/logs

Do not duplicate every Zashboard feature in Compose.

---

## 14. Proxy group behavior

Proxy group/node selection is controlled through the Clash/Mihomo controller API and Zashboard.

Requirements:

- Selection changes must take effect without restarting the browser.
- Persist selections using mihomo's supported profile state mechanism.
- Switching the active profile must isolate group state by profile.
- Dashboard must show:
  - proxy groups
  - currently selected node
  - latency test where supported
  - rule/global/direct mode
  - active connections
  - traffic
  - logs
  - provider information where supported

The app should not scrape Zashboard DOM to discover the selected node.

If native UI later needs current group state, call the controller API through a typed local client.

---

## 15. Dependency plan

Existing dependencies reused:

| Dependency | Usage |
| --- | --- |
| AndroidX WebKit | WebView proxy override |
| OkHttp | subscription/profile downloads and optional proxied downloader |
| Kotlin Coroutines | lifecycle/state |
| Kotlin Serialization | metadata/controller DTOs |
| Koin | dependency injection |
| Room | optional profile metadata |
| Compose | proxy settings surface |

New dependency policy:

- Do not add a general-purpose VPN framework for MVP.
- Do not add a second HTTP stack.
- Do not add a YAML parser solely for unsafe string rewriting.
- Do not use JitPack/moving GitHub branches for the native core.
- Prefer an in-repo native bridge and pinned upstream source/artifacts.

---

## 16. Security requirements

### Mandatory

1. `external-controller` binds to `127.0.0.1` only.
2. Mixed proxy port binds locally only.
3. `allow-lan: false`.
4. Generate a high-entropy controller token using `SecureRandom`.
5. Never use a hard-coded controller secret.
6. Do not log:
   - proxy passwords
   - UUIDs
   - subscription tokens
   - controller secret
   - complete subscription URLs
7. Profile/config files stay in app-private storage.
8. Exclude proxy credentials/config from Android backup.
9. Validate imported configs before activation.
10. Use atomic config replacement.
11. Keep last known-good config.
12. Dashboard navigation is loopback-only.
13. No dashboard runtime update from arbitrary URL.
14. No core runtime self-update.
15. Release CI verifies SHA-256 of native/dashboard artifacts.
16. Do not allow user-supplied config to override loopback/controller security settings in MVP.
17. Proxy REQUIRED mode is fail-closed.

### WebView dashboard hardening

For dashboard WebView:

- JavaScript: enabled
- DOM storage: enabled only if Zashboard needs it
- mixed content: do not broaden globally
- file access: disabled unless proven required
- universal access from file URLs: disabled
- debugging: debug builds only
- safe browsing: keep enabled where supported
- navigation: strict loopback allowlist

---

## 17. Privacy behavior

The product should be understandable as:

> Browsing traffic is sent to the user's selected proxy according to their imported mihomo configuration. Proxy configuration and controller credentials are stored locally by the app. The application does not operate an external proxy service.

Update EinkBro's privacy documentation before release to describe:

- local mihomo core
- imported subscription URLs
- optional remote subscription fetches
- proxy provider seeing user traffic depending on protocol/config
- no LAN controller exposure
- dashboard is bundled locally

---

## 18. Performance and APK size

EinkBro currently optimizes heavily for APK size; preserve that discipline.

Requirements:

- Keep ABI split builds.
- Release artifacts should primarily be per-ABI.
- Universal APK only when explicitly requested.
- Bundle Zashboard no-font build.
- Do not bundle unnecessary fonts.
- Native symbols/debug info must not ship in release APK unless required.
- Strip native artifacts appropriately while retaining separate symbols for crash analysis.
- Track APK-size delta in CI.
- Track startup time: proxy initialization must not block UI thread.
- Core startup/reload runs off main thread.
- WebView proxy API callbacks are bridged into coroutine/state safely.

No absolute APK-size promise is made until the pinned core is compiled and measured.

---

## 19. Error model

User-visible errors should be explicit:

- No active profile
- Invalid profile
- Subscription download failed
- Subscription authentication failed
- Core start failed
- Controller health check failed
- Proxy port unavailable
- Unsupported WebView proxy override
- Dashboard failed to install/load

For `REQUIRED` mode, show a dedicated local error page:

```text
Proxy unavailable

Browsing is blocked because "Proxy required" is enabled.

[Retry]
[Open proxy settings]
```

Do not provide a one-tap "continue direct" action in REQUIRED mode.

---

## 20. Testing strategy

### 20.1 Unit tests

`:proxy-core`

- runtime override generation
- source config is not mutated
- mandatory security settings win
- profile path traversal rejection
- subscription metadata redaction
- atomic update rollback
- controller token generation
- state transitions

`:app`

- proxy policy state machine
- REQUIRED never clears override on core failure
- DIRECT clears override explicitly
- active profile switching

### 20.2 Integration tests

With a local test HTTP server:

1. Direct endpoint is unreachable unless traffic passes local proxy.
2. Browser request reaches the test endpoint through mihomo.
3. Stopping mihomo makes navigation fail closed.
4. Restarting mihomo restores navigation.
5. Proxy group selection changes subsequent route.
6. Dashboard can query `/version`, `/proxies` and websocket endpoints.
7. Controller is unreachable through the device LAN address.
8. Port listener is loopback-only.
9. Imported invalid YAML never replaces the working config.
10. Dashboard cannot navigate to arbitrary remote pages in its dedicated WebView.

### 20.3 Leakage tests

Required before release:

- cold app start while REQUIRED
- core crash during page load
- core restart
- profile reload
- Wi-Fi to cellular transition
- airplane mode restore
- DNS-sensitive test hostname
- HTTP and HTTPS
- WebSocket page
- file download
- multiple tabs

The acceptance criterion is no silent DIRECT fallback for browser-owned traffic in REQUIRED mode.

---

## 21. CI / release

Add jobs or steps for:

1. Kotlin/unit tests.
2. Lint for changed proxy modules.
3. Native core reproducible build per ABI.
4. Native artifact checksum verification.
5. Zashboard pinned asset verification.
6. Third-party license/NOTICE verification.
7. SBOM generation.
8. Per-ABI APK build.
9. APK-size regression report.
10. Optional instrumented proxy smoke test.

Release metadata must include:

- EinkBro app version
- mihomo version + commit
- Zashboard version + commit
- ABI
- checksums

---

## 22. Implementation phases

### Phase 1 — module skeleton and dependency boundaries

- Add `:proxy-core`.
- Add `:proxy-dashboard`.
- Add typed API/state.
- Add version manifests.
- No browser behavior change yet.

Acceptance: existing EinkBro builds and tests remain green.

### Phase 2 — core runtime

- Package pinned native core.
- Implement service/process.
- Implement profile store/runtime config.
- Implement health check.
- Implement local controller token.

Acceptance: core starts with a test config and controller is reachable only on loopback.

### Phase 3 — browser routing

- Add `BrowserProxyCoordinator`.
- Add WebKit proxy override.
- Add REQUIRED fail-closed startup.
- Add local error page.

Acceptance: test WebView traffic is demonstrably routed through mihomo.

### Phase 4 — profile management

- Import URL/file/text.
- Validate.
- Atomic activate/update.
- Active profile switch.
- Restart/reload.

Acceptance: bad updates preserve last known-good config.

### Phase 5 — Zashboard

- Bundle pinned no-font assets.
- Install to private UI directory.
- Add dedicated dashboard Activity.
- Pass local controller connection.
- Disable core/TUN upgrade controls.

Acceptance: proxy groups and node selection work from in-app dashboard.

### Phase 6 — downloads and edge cases

- Audit all browser-owned network paths.
- Route internal downloads via configured local proxy or explicitly document external handoff.
- Cover WebSocket, HTTP, HTTPS and multi-tab behavior.

### Phase 7 — hardening/release

- backup exclusions
- privacy documentation
- security tests
- checksum/SBOM
- APK-size reporting
- release build verification

---

## 23. Definition of Done

The feature is complete only when all of the following are true:

- [ ] Opening a normal external website automatically uses the embedded mihomo core.
- [ ] REQUIRED mode has no silent DIRECT fallback.
- [ ] Proxy controller is loopback-only.
- [ ] Mixed proxy is loopback-only.
- [ ] No VPN permission is required for MVP.
- [ ] User can import and activate a mihomo profile.
- [ ] User can update a profile without losing last known-good config.
- [ ] User can open bundled Zashboard.
- [ ] User can select proxy groups/nodes in Zashboard.
- [ ] Group selection takes effect without restarting the browser.
- [ ] Zashboard cannot expose controller control to LAN.
- [ ] Zashboard cannot self-upgrade the core.
- [ ] Dashboard assets and core are pinned and verified.
- [ ] Proxy credentials are not written to logs.
- [ ] Proxy config is excluded from cloud backup.
- [ ] Per-ABI release APKs remain supported.
- [ ] Existing EinkBro browsing/reader features pass regression tests.
- [ ] Privacy/third-party notices are updated.

---

## 24. Recommended implementation rules for Codex/agents

When implementing this spec:

1. Work only on a feature branch.
2. Keep commits small and feature-scoped.
3. Do not rewrite unrelated EinkBro architecture.
4. Do not introduce a VPN service in MVP.
5. Do not bind mihomo to LAN.
6. Do not add hard-coded proxy/controller secrets.
7. Do not add arbitrary runtime binary downloads.
8. Do not add "temporary" DIRECT fallback in REQUIRED mode.
9. Reuse existing WebKit, OkHttp, Coroutines, Koin, Compose and Room dependencies.
10. Add tests with every lifecycle/security behavior change.
11. Preserve upstream mergeability.
12. Before merging, produce a short architecture/security review and APK-size comparison.

---

## 25. Key technical rationale

This design intentionally treats mihomo as a local browser network engine, not as a second full Android VPN application embedded inside EinkBro.

The browser owns policy and WebView routing; `:proxy-core` owns the native runtime; Zashboard owns advanced proxy-control UX through the local Clash API.

That separation minimizes coupling and keeps the implementation maintainable:

```text
EinkBro browser
      |
      | depends on interface/state
      v
proxy-core  <---- local REST/WS ----  Zashboard
      |
      v
   mihomo
```

The MVP therefore solves the stated product goal with the smallest Android privilege surface while leaving a clean path to add an optional VpnService/TUN mode later.
