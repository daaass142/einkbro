# EinkBro Mihomo Frontend — Architecture

Status: implementation-ready  
Target: Android browser with embedded Mihomo and bundled Zashboard

## 1. Architecture principle

Use a two-surface frontend with one source of truth.

```text
Surface A — Native Compose
  Daily browser/VPN controls

Surface B — Bundled Zashboard
  Advanced Mihomo controls
```

Both surfaces operate on the same Mihomo runtime.

They do not maintain separate proxy state.

## 2. High-level architecture

```text
                         Android :app
                             |
        +--------------------+--------------------+
        |                                         |
        v                                         v
 Native Proxy UI                          ProxyDashboardActivity
 Jetpack Compose                          dedicated WebView
        |                                         |
        v                                         v
 ProxyViewModel                         bundled Zashboard assets
        |                                         |
        +---------------+-------------------------+
                        |
                        v
                  Mihomo controller
                  127.0.0.1:<port>
                        |
                        v
                   :core-mihomo
                        |
                 libmihomo-android
                        |
                        v
                     Mihomo
                        |
            +-----------+-----------+
            |                       |
            v                       v
 Browser SOCKS mode             Strict VPN mode
 ProxyController                VpnService/TUN
```

## 3. Ownership boundaries

### Native Compose owns

- proxy enabled/disabled intent;
- Browser Proxy / Strict VPN transport selection;
- Android VPN permission;
- fail-closed preference;
- active profile;
- profile import;
- subscription creation/refresh;
- runtime startup errors;
- temporary direct recovery;
- lightweight routing mode;
- lightweight node/group control;
- diagnostics;
- opening/closing Zashboard.

### Zashboard owns

- full proxy group view;
- advanced node switching;
- Providers;
- Rules;
- Connections;
- connection termination;
- traffic visualization;
- logs exposed by Mihomo;
- advanced Clash/Mihomo controller features.

### Zashboard must not own

- Android VPN permission;
- starting/stopping `VpnService`;
- TUN fd;
- app capture list;
- WebView ProxyController;
- core binary upgrade;
- Android fail-closed policy;
- profile file persistence;
- subscription secrets persistence.

## 4. Frontend modules

Recommended package structure:

```text
app/
  proxy/
    presentation/
      ProxyRoute.kt
      ProxyScreen.kt
      ProxyViewModel.kt
      ProxyUiState.kt
      ProxyUiEvent.kt
      ProxyUiError.kt

    presentation/components/
      RuntimeStatusCard.kt
      TransportSelector.kt
      FailClosedCard.kt
      ProfileSection.kt
      RoutingModeSelector.kt
      ProxyGroupCard.kt
      NodePickerSheet.kt
      TrafficCard.kt
      DiagnosticsCard.kt

    dashboard/
      ProxyDashboardActivity.kt
      DashboardWebViewFactory.kt
      DashboardNavigationPolicy.kt
      DashboardBootstrap.kt

    vpn/
      StrictVpnController.kt
      StrictVpnRuntime.kt
      MihomoVpnService.kt

    mapper/
      ProxyUiMapper.kt
      ProxyErrorMapper.kt
```

Keep `:core-mihomo` and `:core-network` free of Compose.

## 5. Unidirectional data flow

```text
User event
 -> ProxyViewModel
 -> domain/runtime interface
 -> StateFlow/repository result
 -> map to ProxyUiState
 -> Compose render
```

Example:

```text
Tap "JP-Tokyo-02"
 -> SelectProxy(group, node)
 -> MihomoEngine.changeProxy()
 -> refresh proxy catalog
 -> StateFlow
 -> ProxyGroupCard renders selected node
```

Do not optimistically claim a proxy is selected before Mihomo confirms it.

## 6. Runtime state model

UI should normalize backend states into a small explicit model.

```kotlin
sealed interface RuntimeUiStatus {
    data object Off : RuntimeUiStatus
    data object Starting : RuntimeUiStatus
    data class ProtectedBrowserProxy(...) : RuntimeUiStatus
    data class ProtectedStrictVpn(...) : RuntimeUiStatus
    data object Blocked : RuntimeUiStatus
    data object TemporaryDirect : RuntimeUiStatus
    data class Error(val category: ProxyErrorCategory) : RuntimeUiStatus
}
```

Do not infer status only from `config.proxy.enabled`.

Preference describes intent.

Runtime status describes reality.

## 7. Transport state machine

### Browser Proxy

```text
Disabled
 -> Block WebView
 -> Start Mihomo
 -> Verify SOCKS
 -> Apply ProxyController
 -> ProtectedBrowserProxy
```

Failure:

```text
Starting
 -> failure
 -> Blocked
```

### Strict VPN

```text
Browser Proxy / Off
 -> request Android VPN permission
 -> Block WebView
 -> ensure Mihomo Running
 -> establish TUN
 -> start libmihomo TUN
 -> StrictVpnRuntime.Running
 -> clear WebView explicit SOCKS
 -> ProtectedStrictVpn
```

Unexpected VPN stop:

```text
ProtectedStrictVpn
 -> VPN lost
 -> immediately block WebView
 -> Blocked
```

## 8. Dashboard bootstrap architecture

The Android side creates a bootstrap URL.

Example:

```text
https://appassets.androidplatform.net/zashboard/index.html#/setup
  ?protocol=http
  &hostname=127.0.0.1
  &port=<runtime-port>
  &secret=<runtime-secret>
  &disableUpgradeCore=1
  &disableTunMode=1
```

The secret is transient connection bootstrap data.

Rules:

- do not persist URL in EinkBro history;
- clear dedicated WebView history after load;
- do not log full URL;
- destroy WebView on Activity close;
- do not reuse normal browsing WebView.

## 9. Zashboard adapter strategy

Do not fork Zashboard for MVP.

Use a thin adapter around the upstream distribution.

Adapter responsibilities:

- pinned download;
- checksum verification;
- strip upstream `dist/` archive prefix;
- expose through `WebViewAssetLoader`;
- supply controller bootstrap;
- constrain navigation;
- optionally apply Android theme/background before first paint.

If an upstream incompatibility requires code changes:

1. prefer upstream contribution;
2. otherwise maintain a minimal patch set;
3. do not copy large portions into the Android repo.

## 10. UI information architecture

```text
Proxy & Mihomo
  Runtime Status
  Enable
  Transport
  Safety
  Profiles
  Routing
  Proxy Groups
  Traffic
  Advanced Zashboard
  Diagnostics
```

Daily controls stay above the fold where possible.

Advanced controls remain behind one clear Zashboard entry.

## 11. Component architecture

### RuntimeStatusCard

Inputs:

- runtime status;
- active profile;
- routing mode;
- current node;
- transport.

Actions:

- Retry;
- Temporary Direct;
- return to configured proxy.

### TransportSelector

Inputs:

- selected transport;
- permission status;
- current transition.

Actions:

- Browser Proxy;
- Strict VPN.

No WebView code inside this component.

### ProfileSection

Inputs:

- profiles;
- active profile;
- refresh state.

Actions:

- import;
- add subscription;
- select;
- refresh;
- delete.

### ProxyGroupCard

Inputs:

- group name;
- selected node;
- known delay.

Actions:

- open picker;
- test selected node.

### NodePickerSheet

Designed for large lists:

- search;
- virtualized/lazy list;
- selected marker;
- delay;
- no automatic all-node test.

## 12. Error architecture

Map backend exceptions to UI categories.

```text
NativeLoadFailure      -> APP_INCOMPATIBLE
BridgeAbiMismatch      -> APP_INCOMPATIBLE
InvalidProfile         -> PROFILE_INVALID
ListenerBindFailure    -> RUNTIME_START
ControllerFailure      -> RUNTIME_START
ProxyApplyFailure      -> WEBVIEW_UNSUPPORTED
SubscriptionFailure    -> SUBSCRIPTION
VpnPermissionDenied    -> VPN_PERMISSION
VpnRevoked             -> VPN_REVOKED
RuntimeFailure         -> RUNTIME
```

Compose receives typed errors, not arbitrary raw exceptions.

All displayed raw details pass redaction.

## 13. E-Ink architecture constraints

UI architecture must minimize invalidation.

Therefore:

- no live per-second traffic updates by default;
- no continuously animated connection graphs in native UI;
- no polling all node latency at screen open;
- use manual refresh;
- destroy Zashboard WebView when not used;
- avoid animation-heavy screen transitions;
- ensure status works in grayscale.

## 14. Security architecture

```text
Imported profile
     |
     v
RuntimeConfigBuilder
     |
forces loopback-only listener/controller
     |
     v
Mihomo
```

Frontend cannot override runtime security invariants.

The UI should never offer dangerous controls merely because Mihomo supports them.

## 15. Test architecture

### Unit

- ViewModel reducers/state;
- mapper tests;
- error mapping;
- profile selection;
- Direct-via-Mihomo semantics;
- Strict/Browser mode state transitions.

### Instrumentation

- system VPN permission;
- Zashboard asset loading;
- WebView navigation policy;
- ProxyController state;
- fail-closed recovery;
- Activity recreation.

### Manual release

- grayscale/E-Ink;
- large node list;
- long names;
- screen rotation;
- background/foreground;
- VPN revoke;
- process kill;
- offline dashboard.

## 16. Architecture acceptance

Architecture is correct only if all are true:

- no UI imports `io.github.oviron.libmihomo`;
- no WebView controls Android VPN lifecycle;
- no Compose code owns files/DAO/network clients;
- Zashboard works offline except loopback controller;
- one Mihomo runtime is shared by native UI and Zashboard;
- Strict VPN loss cannot silently become direct browsing;
- controller and SOCKS listeners remain loopback-only.
