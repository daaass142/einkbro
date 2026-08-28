# EinkBro Mihomo Frontend — Technology Stack

Status: implementation-ready  
Target branch: `feat/mihomo-browser`  
Pinned advanced dashboard: `Zephyruso/zashboard v3.24.0`

## 1. Decision

The frontend is a hybrid Android/Web architecture.

```text
Native Android UI
  = Android/VPN lifecycle + browser-centric controls

Bundled Zashboard
  = advanced Mihomo management
```

Do not reimplement the whole Zashboard UI in Compose.

Do not let Zashboard own Android `VpnService` permission, foreground-service lifecycle, TUN fd ownership, fail-closed policy or browser transport switching.

## 2. Native Android stack

Use the project's existing Android stack. Do not introduce a second UI framework just for Mihomo.

### Required

- Kotlin
- Jetpack Compose
- Android lifecycle / ViewModel
- Kotlin Coroutines
- StateFlow
- Koin
- AndroidX WebKit
- Android `VpnService`
- Room
- OkHttp
- Android string resources
- existing EinkBro design primitives

### UI rule

Reuse the current EinkBro Compose design language.

Do not migrate the whole app to another component library as part of the Mihomo feature.

If Material 3 is adopted later, migrate it as a separate project-wide task.

## 3. Zashboard stack

The pinned Zashboard `v3.24.0` uses:

- Vue 3.5
- TypeScript 6
- Vite 8
- Tailwind CSS 4
- DaisyUI 5
- Vue Router 5
- VueUse
- TanStack Vue Table
- TanStack Vue Virtual
- Axios
- ReconnectingWebSocket
- ECharts
- DOMPurify
- vue-i18n
- pnpm

We consume the built distribution, not its runtime development server.

Preferred artifact:

```text
dist-no-fonts.zip
```

Reason:

- smaller APK footprint;
- no CDN dependency;
- system font works well on Android/E-Ink;
- avoids shipping another large font family.

## 4. Integration stack

### Static assets

Use:

```text
Gradle verified download
 -> SHA-256 verification
 -> unzip pinned Zashboard artifact
 -> generated Android assets
```

Do not download Zashboard at runtime.

### WebView hosting

Use:

- dedicated `ProxyDashboardActivity`
- dedicated WebView
- `WebViewAssetLoader`
- web-like local origin
- bundled static assets

Recommended origin:

```text
https://appassets.androidplatform.net/zashboard/
```

Do not use:

- `file:///android_asset/`
- remote hosted dashboard as the default
- arbitrary URL loading in the dashboard WebView

### Controller connection

Zashboard connects to the application-owned local Mihomo controller:

```text
127.0.0.1:<runtime-controller-port>
```

with:

- generated random secret;
- loopback-only binding;
- URL bootstrap;
- `disableUpgradeCore=1`;
- `disableTunMode=1`.

The controller is a Mihomo API surface, not an Android application API.

## 5. State management

Native UI state source:

```text
MihomoEngine
MihomoSessionManager
BrowserNetworkGateway
StrictVpnRuntime
ProfileRepository
        |
        v
ProxyViewModel
        |
        v
Compose
```

Use unidirectional data flow.

Compose must not call JNI, `Clash`, Room DAOs, OkHttp or `VpnService` directly.

### Recommended state shape

```kotlin
data class ProxyUiState(
    val enabled: Boolean,
    val transport: ProxyTransportMode,
    val runtime: RuntimeUiStatus,
    val failClosed: Boolean,
    val activeProfile: ProfileUiModel?,
    val profiles: List<ProfileUiModel>,
    val routingMode: RoutingMode,
    val groups: List<ProxyGroupUiModel>,
    val traffic: TrafficUiModel,
    val currentAction: ProxyAction?,
    val error: ProxyUiError?,
)
```

Prefer a typed current action rather than a global `busy: Boolean`.

## 6. Navigation

Use existing Settings navigation.

```text
Settings
 -> Proxy & Mihomo
 -> Advanced Zashboard
```

System UI remains system-owned:

```text
Proxy screen
 -> VpnService.prepare()
 -> Android VPN permission dialog
 -> same Proxy screen
```

Do not implement VPN permission inside WebView.

## 7. Android/Web boundary

No general-purpose JavaScript bridge.

The dashboard must communicate with Mihomo through the Clash/Mihomo controller only.

Allowed bridge between Android and WebView:

- initial page URL / controller bootstrap;
- navigation policy;
- lifecycle ownership.

Forbidden:

- `addJavascriptInterface` exposing application services;
- direct file access;
- Android secrets API exposed to JavaScript;
- VPN service control from arbitrary JS.

## 8. Security baseline

Dashboard WebView:

- JavaScript enabled;
- DOM storage enabled only because dashboard needs it;
- file access disabled;
- content access disabled;
- mixed content disabled;
- third-party cookies disabled;
- WebView debugging only in debug builds;
- external navigation leaves the dedicated dashboard;
- no controller secret in browser history/logs.

Native UI:

- never shows full subscription URL;
- never shows controller secret;
- redacts token/password/auth query values;
- fail-closed defaults ON.

## 9. Testing stack

### Native unit tests

Test:

- UI state mapping;
- transport transitions;
- fail-closed state;
- profile selection;
- routing mode semantics;
- redaction;
- typed error mapping.

### Android instrumentation

Test:

- `VpnService.prepare()`;
- permission rejection/grant;
- Strict VPN lifecycle;
- `addAllowedApplication(packageName)`;
- WebView ProxyController;
- Zashboard local asset load;
- offline local dashboard;
- app process recreation.

### Web asset verification

CI must verify:

- pinned Zashboard SHA-256;
- `index.html` exists after extraction;
- generated assets contain expected JS/CSS;
- no remote CDN is required for no-fonts build.

## 10. Dependency policy

All security-sensitive dependencies and binary assets are pinned.

Must pin:

- libmihomo AAR version + SHA-256;
- bridge ABI;
- Mihomo core version metadata;
- Zashboard version + SHA-256;
- GitHub Actions by commit SHA where practical.

Do not use:

- `latest`;
- floating `main`;
- wildcard versions;
- runtime dashboard auto-update.

## 11. Why not a pure Zashboard frontend

Zashboard is a Clash API dashboard.

Android VPN state includes concepts outside Clash API:

- system VPN permission;
- foreground service;
- TUN file descriptor;
- `VpnService.protect(fd)`;
- Android service revoke;
- app-only capture;
- WebView SOCKS vs Android VPN transport;
- browser fail-closed state.

Therefore pure Zashboard would create the wrong ownership boundary.

The recommended stack keeps Zashboard where it is strongest and keeps Android lifecycle in Android.
