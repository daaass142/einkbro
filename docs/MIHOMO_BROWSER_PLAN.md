# EinkBro Mihomo Browser — Implementation Plan

Status: execution plan  
Repository: daaass142/einkbro  
Branch: feat/mihomo-browser  
Companion specification: docs/MIHOMO_BROWSER_SPEC.md

## 1. Execution rules

All implementation work for this feature stays on feat/mihomo-browser until it is ready for review.

The work is organized into independently testable phases.

Rules:

- Do not mix unrelated EinkBro refactors into this branch.
- Keep the existing browser behavior working after every phase.
- Prefer small adapters around existing dependencies over new frameworks.
- Do not import io.github.oviron.libmihomo outside :core-mihomo runtime adapter code.
- Do not expose proxy/controller listeners to LAN.
- Do not restore browser tabs before the network gate is ready when proxy-on-start is enabled.
- Default to fail-closed.
- Do not implement Strict VPN until Browser Proxy mode is working and tested.
- Each phase ends with build/tests before moving to the next phase.
- Commit by coherent feature boundary, not by every tiny edit.

## 2. Target implementation sequence

~~~text
Phase 0  Dependency and baseline lock
Phase 1  Gradle modules + libmihomo adapter
Phase 2  Runtime config + SOCKS listener + browser network gate
Phase 3  Profiles/subscriptions + native quick proxy controls
Phase 4  Embedded Zashboard
Phase 5  Strict application-only VPN
Phase 6  Release hardening, diagnostics and polish
~~~

Browser Proxy MVP is complete after Phase 4 plus the MVP blockers in Phase 6.

Strict VPN is a separate deliverable.

---

## Phase 0 — Baseline and dependency lock

### Goal

Create a reproducible starting point before changing runtime behavior.

### Tasks

- [ ] Confirm feat/mihomo-browser is based on the intended EinkBro main commit.
- [ ] Run the existing unit tests/lint/build and record any pre-existing failures.
- [ ] Pin a tested libmihomo-android release artifact.
- [ ] Record:
  - wrapper version
  - AAR SHA-256
  - bridgeABI
  - supported ABIs
  - bundled mihomo version from metadata when available
- [ ] Do not use libmihomo main directly in release builds.
- [ ] Select a concrete Zashboard tag/release/commit.
- [ ] Record the Zashboard artifact checksum.
- [ ] Add a third-party dependency manifest under docs or build tooling.
- [ ] Confirm EinkBro license remains compatible with the GPL-3.0 libmihomo/mihomo combination.
- [ ] Remove/disable x86 from the mihomo-enabled variant because libmihomo does not ship x86.

### Suggested files

~~~text
docs/MIHOMO_BROWSER_SPEC.md
docs/MIHOMO_BROWSER_PLAN.md
docs/THIRD_PARTY_MIHOMO.md
gradle/libs.versions.toml
app/build.gradle.kts
~~~

### Acceptance

- Existing EinkBro baseline is documented.
- Exact libmihomo and Zashboard inputs are pinned.
- No moving latest/main dependency is used by release builds.
- Supported ABI set is explicit.

### Commit suggestion

~~~text
docs/build: lock mihomo browser dependency baseline
~~~

---

## Phase 1 — Core modules and libmihomo adapter

### Goal

Introduce the application boundaries without changing normal browser networking yet.

### Tasks

#### Gradle

- [ ] Add :core-mihomo.
- [ ] Add :core-network.
- [ ] Wire both modules through settings.gradle.kts.
- [ ] Add only required dependencies.
- [ ] Integrate the pinned libmihomo AAR into :core-mihomo.
- [ ] Add checksum verification to the build/download step if the AAR is not vendored.
- [ ] Keep library version/hash configuration centralized.

#### core-mihomo API

Create:

~~~text
core-mihomo/src/main/kotlin/.../api/
  MihomoEngine.kt
  MihomoState.kt
  MihomoModels.kt
  MihomoException.kt
~~~

Models should include at least:

- ProxyEndpoint
- ControllerEndpoint
- ProxyGroup
- ProxyNode
- TrafficSnapshot
- ProxyConnection
- RoutingMode
- MihomoProfile

#### Native loader

Create:

~~~text
runtime/LibMihomoLoader.kt
~~~

Responsibilities:

- resolve native library directory
- call Clash.load exactly once
- check Clash.isLoaded
- validate bridgeABI against expected bridge ABI
- expose typed failure

Do not load native libraries from Activity or ViewModel code.

#### Action adapter

Create:

~~~text
runtime/LibMihomoActionClient.kt
runtime/LibMihomoMapper.kt
~~~

Implement coroutine adapters and typed actions for:

- getProxies
- changeProxy
- testDelay
- getTraffic/getTotalTraffic
- getConnections
- closeConnection/closeAllConnections
- queryProxyGroupOrder
- validateConfig
- setupConfig
- updateConfig
- startListener
- stopListener

#### Runtime manager

Create:

~~~text
runtime/LibMihomoEngine.kt
runtime/MihomoRuntimeManager.kt
~~~

Requirements:

- one serialized state machine
- idempotent load/start/stop
- no duplicate runtime
- callback work never blocks Go/JNI callback threads
- state observable with StateFlow

### Tests

- [ ] bridge ABI success.
- [ ] bridge ABI mismatch fails.
- [ ] action envelope success parsing.
- [ ] action envelope error parsing.
- [ ] malformed JSON does not crash callers.
- [ ] concurrent start calls result in one runtime transition.
- [ ] repeated stop is safe.

### Acceptance

- App builds with libmihomo included.
- Native library loads on arm64 emulator/device or an available supported target.
- No app/UI code directly imports Clash.
- No WebView proxy behavior has changed yet.

### Commit suggestion

~~~text
feat(mihomo): add typed libmihomo runtime adapter
~~~

---

## Phase 2 — Safe runtime config, SOCKS5 and browser network gate

### Goal

Make EinkBro automatically browse through embedded mihomo with no VPN permission.

### 2.1 Config storage

Create:

~~~text
config/
  RuntimeConfigBuilder.kt
  ConfigValidator.kt
  ConfigStore.kt
  PortAllocator.kt
~~~

RuntimeConfigBuilder MUST override imported values with application safety settings:

~~~yaml
allow-lan: false
bind-address: 127.0.0.1
port: 0
mixed-port: 0
socks-port: <runtime-port>
external-controller: 127.0.0.1:<controller-port>
secret: <generated-secret>
~~~

### 2.2 Port allocation

PortAllocator:

- [ ] chooses high loopback ports
- [ ] verifies availability
- [ ] allocates separate SOCKS and controller ports
- [ ] handles EADDRINUSE with retry
- [ ] never binds LAN addresses

### 2.3 Config lifecycle

ConfigStore:

- [ ] writes temp file
- [ ] validates
- [ ] fsyncs
- [ ] atomic renames
- [ ] maintains last-known-good config
- [ ] preserves active config on update failure

### 2.4 Start mihomo

Startup:

~~~text
load native
 -> validate bridge ABI
 -> resolve profile
 -> build runtime config
 -> validate config
 -> quickSetup/setup
 -> start listener
 -> check SOCKS
 -> check controller
 -> Running
~~~

### 2.5 core-network

Create:

~~~text
core-network/src/main/kotlin/.../
  BrowserNetworkGateway.kt
  BrowserNetworkState.kt
  WebViewProxyController.kt
  ProxyFeatureSupport.kt
~~~

WebViewProxyController:

- [ ] uses AndroidX WebKit ProxyController only
- [ ] checks the required WebView feature
- [ ] applies socks://127.0.0.1:<port>
- [ ] exposes clear/direct only through explicit state transitions
- [ ] never uses Java system proxy properties
- [ ] never uses reflection hacks

### 2.6 Startup gate

Modify the EinkBro startup flow so network tabs do not restore until BrowserNetworkGateway is Ready when proxy-on-start is enabled.

Gate:

~~~text
Blocked/Starting
 -> mihomo ready
 -> ProxyController applied
 -> Ready
 -> restore tabs
~~~

On failure:

~~~text
Error
 -> do not silently clear proxy
 -> show retry/profile/direct recovery UI
~~~

### 2.7 Crash/failure behavior

If mihomo/listener dies while proxy is enabled:

- [ ] proxy endpoint is not silently cleared
- [ ] new WebView requests fail rather than direct-connect
- [ ] UI indicates proxy error
- [ ] user can retry
- [ ] explicit direct action is available if desired

### Tests

- [ ] runtime security overrides cannot be removed by imported YAML.
- [ ] bad profile preserves last-known-good.
- [ ] port collision retries.
- [ ] WebView feature unsupported path is explicit.
- [ ] startup does not restore tabs early.
- [ ] proxy failure remains fail-closed.
- [ ] HTTPS test page loads through SOCKS5.
- [ ] stop listener causes request failure under fail-closed policy.

### Acceptance

A configured user can:

1. open the app,
2. wait for embedded mihomo startup,
3. browse through SOCKS5 automatically,
4. stop the local proxy and observe that the browser does not silently become direct.

### Commit suggestions

~~~text
feat(mihomo): add safe runtime config and SOCKS listener
feat(network): gate WebView startup behind mihomo proxy
~~~

---

## Phase 3 — Profiles, subscriptions and native quick controls

### Goal

Make proxy use practical without forcing the user into Zashboard for every common operation.

### 3.1 Profile metadata

Add Room entities/repositories for:

- profile ID
- display name
- source type
- local path
- subscription URL reference
- active flag
- last update time
- update status

Keep YAML on disk, not in Room.

### 3.2 Profile sources

Support:

- [ ] local YAML import
- [ ] subscription URL
- [ ] active profile selection
- [ ] manual subscription refresh
- [ ] profile delete
- [ ] last-known-good rollback

Do not add background WorkManager refresh until the base path is stable.

### 3.3 Subscription security

SubscriptionUpdater:

- [ ] downloads with existing OkHttp stack
- [ ] maximum-size limit
- [ ] HTTPS by default
- [ ] timeout/cancellation
- [ ] temp-file handling
- [ ] validation before activation
- [ ] token/query redaction
- [ ] never logs full subscription URL

### 3.4 Native proxy screen

Add a Compose proxy screen/panel in :app.

Initial UX:

~~~text
Status
Active profile
Routing mode
Proxy group
Selected proxy
Delay
Traffic

[Change proxy]
[Test delay]
[Profiles]
[Open Zashboard]
~~~

Data comes from MihomoEngine.

### 3.5 Proxy groups

Use:

- getProxies
- queryProxyGroupOrder
- changeProxy
- testDelay

Requirements:

- preserve configured group order where available
- identify selectable groups
- show current selection
- do not restart the core just to change a selector
- refresh state after selection

### 3.6 Routing mode

Expose Rule / Global / Direct through the runtime update action.

Direct is a mihomo routing mode and is different from clearing Browser Proxy mode.

This distinction must be clear in code and UI:

~~~text
Mihomo Direct mode
  = WebView still connects to mihomo, mihomo chooses DIRECT outbound

Browser Direct mode
  = WebView bypasses mihomo entirely
~~~

Do not conflate them.

### Tests

- [ ] profile CRUD metadata.
- [ ] local import validation.
- [ ] failed subscription update preserves active config.
- [ ] subscription URL redaction.
- [ ] group order mapping.
- [ ] selector change.
- [ ] delay test error/timeout.
- [ ] routing-mode mapping.
- [ ] Mihomo Direct does not clear WebView proxy.

### Acceptance

Normal proxy operation can be managed from native UI without opening the full dashboard.

### Commit suggestions

~~~text
feat(mihomo): add profile and subscription management
feat(proxy-ui): add native proxy quick controls
~~~

---

## Phase 4 — Embedded Zashboard

### Goal

Provide full mihomo management without maintaining a second native dashboard.

### 4.1 Pin assets

- [ ] choose exact Zashboard release/tag/commit
- [ ] build/download exact distribution
- [ ] verify SHA-256
- [ ] prefer no-fonts build where compatible
- [ ] store bundled static assets in app/src/main/assets/zashboard

### 4.2 Dedicated dashboard screen

Create:

~~~text
app/.../proxy/dashboard/
  DashboardScreen.kt
  DashboardWebView.kt
  DashboardNavigationPolicy.kt
~~~

Use WebViewAssetLoader.

Do not use file:///android_asset URLs.

### 4.3 Security policy

Dashboard WebView:

- [ ] JS enabled
- [ ] DOM storage only if required
- [ ] file access off
- [ ] content access off
- [ ] mixed content off
- [ ] release debugging off
- [ ] no broad addJavascriptInterface
- [ ] arbitrary Internet navigation blocked or opened externally
- [ ] appassets origin allowed
- [ ] loopback controller connectivity allowed as required

### 4.4 Connection bootstrap

Use current MihomoState:

- controller host: 127.0.0.1
- controller port: runtime port
- controller secret: generated app secret

Where Zashboard supports them, pass:

~~~text
disableUpgradeCore=1
disableTunMode=1
~~~

The dashboard must not own:

- core downloads
- core replacement
- Android VPN permission
- TUN fd lifecycle

### 4.5 Controller hardening

Tests/validation:

- [ ] controller is loopback only
- [ ] no controller with empty secret
- [ ] imported YAML cannot override controller exposure
- [ ] normal logs do not print the secret
- [ ] Dashboard WebView is not stored in browser history

### Integration tests

- [ ] Zashboard opens with device offline except for loopback.
- [ ] no hosted dashboard asset is required.
- [ ] dashboard reads proxies.
- [ ] dashboard changes a selector.
- [ ] dashboard reads connections/traffic.
- [ ] external navigation escapes to normal browser handling.
- [ ] core-upgrade/TUN controls are disabled.

### Acceptance

Full proxy management works from a locally bundled Zashboard and does not weaken the application security boundary.

### Commit suggestion

~~~text
feat(dashboard): embed pinned local Zashboard
~~~

---

## Phase 5 — Strict application-only VPN

### Goal

Provide an optional capture mode for users who need stronger guarantees than WebView explicit proxying.

Do not start this phase until Browser Proxy MVP is stable.

### 5.1 Service

Create:

~~~text
core-mihomo/.../vpn/
  MihomoVpnService.kt
  TunSession.kt
  SocketProtector.kt
  VpnPermissionManager.kt
~~~

### 5.2 VpnService builder

Default capture policy:

~~~text
addAllowedApplication(applicationContext.packageName)
~~~

Do not capture unrelated apps by default.

### 5.3 libmihomo TUN

MihomoVpnService/TunSession:

- establish TUN fd
- call Clash.startTUN through the adapter
- implement TunInterface
- protect every mihomo outbound fd using VpnService.protect
- stop TUN on teardown
- close fd exactly once

### 5.4 Lifecycle

Handle:

- permission grant
- permission rejection
- onRevoke
- foreground-service notification
- process death
- service restart policy
- mode switching Browser Proxy <-> Strict VPN

### 5.5 Network tests

Production blockers:

- [ ] protect callback verified.
- [ ] no outbound routing loop.
- [ ] browser-only capture verified.
- [ ] IPv4.
- [ ] IPv6.
- [ ] TCP.
- [ ] UDP.
- [ ] DNS.
- [ ] WebSocket.
- [ ] QUIC/HTTP3 behavior tested/documented.
- [ ] revoke cleanup.
- [ ] process-death cleanup.

### Acceptance

Strict VPN does not affect unrelated apps and survives the defined Android lifecycle correctly.

### Commit suggestion

~~~text
feat(vpn): add application-only mihomo strict mode
~~~

---

## Phase 6 — Release hardening, diagnostics and polish

### Goal

Make the implementation shippable and maintainable.

### 6.1 Diagnostics

Add a diagnostics screen showing only non-secret information:

- app version
- libmihomo wrapper version
- bundled mihomo version
- bridgeABI
- active profile
- browser transport
- SOCKS readiness
- controller readiness
- AndroidX WebKit proxy support
- routing mode
- redacted last error

Add copy/export only after SensitiveValueRedactor is applied.

### 6.2 Dependency manifest

Emit a build/release manifest containing:

~~~text
EinkBro commit
libmihomo wrapper version
libmihomo AAR SHA-256
mihomo core version
bridgeABI
Zashboard version/commit
Zashboard SHA-256
supported ABIs
AGP/Kotlin/NDK versions relevant to native packaging
~~~

### 6.3 Release packaging

- [ ] arm64-v8a APK
- [ ] armeabi-v7a APK
- [ ] x86_64 APK
- [ ] AAB where applicable
- [ ] optional universal APK only as secondary artifact
- [ ] no x86 mihomo-enabled APK
- [ ] release R8 succeeds
- [ ] 16 KiB native-page requirements are satisfied
- [ ] artifact checksums generated

### 6.4 Security verification

- [ ] controller never binds to 0.0.0.0.
- [ ] SOCKS listener never binds to LAN.
- [ ] controller secret is non-empty/random.
- [ ] subscription tokens absent from logs.
- [ ] release WebView debugging off.
- [ ] no privileged JS bridge in ordinary browsing.
- [ ] fail-closed leak test passes.
- [ ] imported profile cannot weaken forced runtime invariants.

### 6.5 Performance

Measure:

- app cold start with proxy disabled
- app cold start with proxy enabled
- mihomo idle CPU
- mihomo idle memory
- dashboard lazy creation/destruction
- time to first proxied page

Do not keep the Dashboard WebView alive when the dashboard is closed unless measurement proves a concrete benefit.

### 6.6 E-Ink polish

Only after functional correctness:

- reduced animation in native proxy UI
- high-contrast textual state indicators
- avoid color-only proxy status
- optional Zashboard reduced-motion adjustments with the smallest maintainable adaptation

### MVP release gate

The Browser Proxy release is blocked until all are true:

- [ ] existing EinkBro core flows pass smoke testing
- [ ] dependency pin verification passes
- [ ] native bridge ABI verification passes
- [ ] automatic SOCKS5 routing passes
- [ ] startup race/leak test passes
- [ ] fail-closed test passes
- [ ] profile import passes
- [ ] subscription rollback passes
- [ ] native selector switching passes
- [ ] local Zashboard passes
- [ ] controller hardening passes
- [ ] release ABI packaging passes
- [ ] secrets redaction passes

### Commit suggestion

~~~text
chore(release): harden mihomo browser release pipeline
~~~

---

## 3. Recommended implementation order inside each phase

When multiple files are involved, follow this order:

~~~text
domain/API
 -> implementation
 -> DI wiring
 -> tests
 -> UI integration
 -> end-to-end smoke test
~~~

Avoid building UI against unfinished native behavior.

---

## 4. Koin wiring

Keep DI simple.

Suggested components:

~~~text
single<MihomoEngine> { LibMihomoEngine(...) }
single { LibMihomoActionClient(...) }
single { RuntimeConfigBuilder(...) }
single { ConfigStore(...) }
single { ProfileRepository(...) }
single<BrowserNetworkGateway> { BrowserNetworkGatewayImpl(...) }
~~~

Activities/ViewModels depend on interfaces.

No ViewModel should construct native adapters, OkHttp clients, Room databases or file stores directly.

---

## 5. Error model

Define explicit error families so UI can offer correct recovery:

~~~text
MihomoException
  NativeLoadFailure
  BridgeAbiMismatch
  InvalidProfile
  ListenerBindFailure
  ControllerFailure
  ProxyApplyFailure
  SubscriptionFailure
  RuntimeFailure
~~~

Avoid passing raw Throwable messages all the way into UI.

The user-facing recovery depends on category:

- InvalidProfile -> choose/fix profile
- ListenerBindFailure -> retry port/start
- BridgeAbiMismatch -> app/library build problem
- SubscriptionFailure -> keep current profile
- ProxyApplyFailure -> WebView compatibility/feature problem
- RuntimeFailure -> restart runtime

---

## 6. Out-of-scope work during MVP

Do not add during Phases 0–4 unless required to fix a blocker:

- full system VPN
- per-app selection UI for other apps
- core auto-updater
- Zashboard auto-updater
- cloud sync
- account system
- remote controller exposure
- LAN proxy server
- custom DNS UI duplicating Mihomo config
- full native rule editor
- full native connections dashboard
- another browser engine

These can be evaluated after the browser-proxy MVP is stable.

---

## 7. Final expected architecture

~~~text
                         :app
                  Browser / Compose UI
                    /             \
                   /               \
                  v                 v
          :core-network       :core-mihomo
          WebView gate        MihomoEngine
          ProxyController          |
                  \                |
                   \               v
                    +-------- libmihomo-android
                               /          \
                              v            v
                    SOCKS 127.0.0.1   Controller 127.0.0.1
                              |            |
                              |            v
                              |      bundled Zashboard
                              |
                              v
                           mihomo
                              |
                              v
                           Internet
~~~

The implementation is complete when this architecture is true in code, not merely in documentation.
