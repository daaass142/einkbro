# EinkBro Mihomo Browser — Frontend UI/UX Specification

Status: implementation-ready  
Repository: daaass142/einkbro  
Branch: feat/mihomo-browser  
Related documents:

- docs/MIHOMO_BROWSER_SPEC.md
- docs/MIHOMO_BROWSER_PLAN.md

## 1. Product UI goal

The Mihomo feature must feel like a native part of EinkBro rather than a VPN client bolted onto a browser.

The UI has two responsibilities:

1. provide fast, browser-centric controls in native Compose UI;
2. hand advanced Clash/Mihomo management to the bundled local Zashboard.

The native UI must prioritize:

- clarity over density;
- high contrast over decoration;
- text labels over color-only meaning;
- minimal animation for E-Ink;
- explicit proxy state;
- explicit failure state;
- explicit distinction between Mihomo DIRECT and browser bypass/direct networking.

The UI must never expose unsafe controller/network settings that are owned by the application runtime.

---

## 2. Information architecture

Primary navigation:

~~~text
Settings
  └── Proxy & Mihomo
        ├── Status
        ├── Transport
        ├── Profiles
        ├── Routing
        ├── Proxy groups
        ├── Traffic
        ├── Advanced / Zashboard
        └── Diagnostics
~~~

The existing EinkBro Settings screen remains the single entry point.

Do not add a separate launcher Activity, notification-only control surface, or top-level navigation destination for Mihomo in the MVP.

Optional future browser-toolbar quick action may deep-link into the same Proxy screen.

---

## 3. Settings entry

Location:

~~~text
Settings
  ├── UI
  ├── Toolbar
  ├── Behavior
  ├── Gestures
  ├── ...
  ├── Proxy & Mihomo
  ├── Misc
  └── ...
~~~

Label:

~~~text
Proxy & Mihomo
~~~

Icon:

- use an existing network/globe icon where possible;
- do not add a complex custom icon for MVP.

Optional secondary text on the main settings card:

~~~text
Off
Browser Proxy
Strict VPN
Error
~~~

The summary must reflect runtime state, not merely the stored preference.

---

## 4. Proxy main screen

The page is a single vertically scrolling Compose screen.

Recommended section order:

~~~text
1. Runtime status
2. Proxy enable
3. Transport
4. Safety / fail-closed
5. Active profile
6. Profiles
7. Routing mode
8. Traffic
9. Proxy groups
10. Advanced / Zashboard
11. Embedded component versions
12. Diagnostics
~~~

Do not present all details with equal visual weight.

The status card, proxy enable switch, active profile and currently selected proxy group are primary.

---

## 5. Runtime status card

The first card communicates whether browsing is protected.

### 5.1 States

Supported visible states:

~~~text
OFF
STARTING
PROTECTED — Browser Proxy
PROTECTED — Strict VPN
BLOCKED — Proxy unavailable
ERROR
TEMPORARY DIRECT
~~~

### 5.2 Content

Example:

~~~text
Mihomo
Protected · Browser Proxy

Profile: Japan
Mode: Rule
SOCKS: Connected
~~~

For strict mode:

~~~text
Mihomo
Protected · Strict VPN

Profile: Japan
VPN: Active
App-only capture
~~~

For fail-closed:

~~~text
Mihomo
Browsing blocked

Mihomo could not start.
No direct connection was allowed.

[Retry]
[Use direct once]
~~~

### 5.3 Visual language

Never rely only on green/red color.

Each state needs:

- icon or simple symbol;
- visible status word;
- one-line explanation.

Suggested labels:

- Protected
- Starting
- Blocked
- Direct
- Error

E-Ink devices may render colors poorly, so status meaning must survive full grayscale rendering.

---

## 6. Enable Mihomo

Control:

~~~text
Enable Mihomo proxy             [switch]
Route browser traffic through embedded Mihomo.
~~~

Behavior:

- switching ON with no active profile must not silently create a default configuration;
- instead show a profile-required prompt;
- switching OFF explicitly stops application proxying and returns WebView networking to direct;
- this is a user-requested direct state and therefore does not violate fail-closed policy.

When ON is requested:

~~~text
Enabled preference
 -> startup state
 -> runtime/profile validation
 -> proxy/VPN readiness
 -> Protected
~~~

The switch must not appear fully enabled while the runtime is still failing.

If startup fails, the stored intention may remain enabled, but the visible status must be Blocked/Error.

---

## 7. Transport selector

Two modes:

~~~text
(●) Browser proxy (SOCKS5)
( ) Strict application-only VPN
~~~

### Browser proxy description

~~~text
Routes Android WebView traffic through local Mihomo.
No VPN permission is required.
~~~

### Strict VPN description

~~~text
Captures this browser through Android VPN/TUN.
Other apps are not included.
~~~

### Permission flow

When Strict VPN is selected:

~~~text
Tap Strict VPN
 -> VpnService.prepare()
 -> Android system VPN permission dialog
 -> granted
 -> start Strict VPN
 -> wait for Running
 -> clear explicit WebView SOCKS override
 -> Protected · Strict VPN
~~~

If denied:

- remain on the previous transport mode;
- show a concise inline message;
- do not repeatedly trigger the permission dialog.

---

## 8. Fail-closed safety option

Control:

~~~text
Block on proxy failure          [switch]
Prevents silent direct Internet access when Mihomo is expected to be active.
~~~

Default: ON.

When ON:

- Mihomo startup failure blocks browsing;
- SOCKS failure remains blocked;
- Strict VPN unexpected stop returns WebView to blocked state;
- imported bad profile must not result in direct fallback.

When OFF:

- the user has explicitly opted into direct fallback;
- UI must still show that the browser is Direct and not Protected.

Do not call this setting "Kill switch" in UI; "Block on proxy failure" is clearer in a browser context.

---

## 9. Profile section

Header:

~~~text
Profiles
~~~

Primary actions:

~~~text
[Import YAML]  [Add subscription]
~~~

Each profile card:

~~~text
(●) Japan
    Subscription · example.com
    Updated 5 min ago

    [Refresh] [Delete]
~~~

Local profile:

~~~text
( ) Local Test
    Local profile

    [Delete]
~~~

### 9.1 Active profile

Use a RadioButton or a clear selected state.

Selecting a profile:

- updates active profile immediately;
- when proxy is enabled, triggers a controlled runtime restart/reload;
- UI enters Starting until protected again;
- old/current connection is not silently replaced by direct browsing.

### 9.2 Subscription URL privacy

The list must display only a safe host/label.

Never display:

- full subscription URL;
- query token;
- authentication secret.

Allowed:

~~~text
Subscription · example.com
~~~

Forbidden:

~~~text
https://example.com/sub?token=secret-value
~~~

### 9.3 Add subscription dialog

Fields:

~~~text
Profile name
HTTPS subscription URL
~~~

Buttons:

~~~text
[Cancel] [Add]
~~~

Rules:

- Add is disabled until an HTTPS URL is present;
- no plaintext HTTP by default;
- failure remains in dialog or becomes an inline page error;
- never echo the full sensitive URL in an error.

### 9.4 Import YAML

Use Android system document picker.

Accepted content:

- .yaml
- .yml
- text/*
- application/yaml where available

After import:

- validate size;
- create profile;
- optionally activate the newly imported profile;
- do not enable proxy automatically unless this is an explicitly designed onboarding flow.

---

## 10. Subscription refresh

Refresh is explicit in MVP.

Flow:

~~~text
Refresh
 -> download candidate
 -> validate candidate
 -> stage candidate
 -> switch/test runtime when active
 -> commit candidate
 -> Protected
~~~

Failure UI:

~~~text
Update failed
Current profile was kept.

[Retry]
~~~

The existing working source profile must remain visible and active.

Do not present a failed candidate as the current profile.

---

## 11. Routing mode

Native quick control:

~~~text
Routing mode

(●) Rule
( ) Global
( ) Direct
~~~

Important terminology:

### Mihomo Direct

~~~text
WebView -> Mihomo -> DIRECT outbound
~~~

The browser is still connected to Mihomo.

### Browser direct / bypass

~~~text
WebView -> Internet
~~~

Mihomo is bypassed.

The UI must never represent these as the same action.

Recommended labels:

- Rule
- Global
- Direct via Mihomo

If space permits, use "Direct via Mihomo" rather than only "Direct".

---

## 12. Traffic summary

Compact card:

~~~text
Traffic

↓ 24.8 MiB
↑ 2.1 MiB

[Refresh]
~~~

Do not build graphs in native UI for MVP.

Advanced traffic charts belong to Zashboard.

For E-Ink:

- numbers update on manual refresh;
- avoid continuously repainting counters every second;
- if automatic refresh is later added, use a slow cadence and stop when the screen is not visible.

---

## 13. Proxy groups

Each selectable proxy group uses one card.

Example:

~~~text
Proxy
Selected: JP-Tokyo-01

[Change node]   [42 ms]
~~~

Another group:

~~~text
Streaming
Selected: US-LA-02

[Change node]   [Test delay]
~~~

### 13.1 Group ordering

Use Mihomo/queryProxyGroupOrder order where available.

Do not alphabetically destroy configuration order unless no explicit group order exists.

### 13.2 Node picker

MVP may use a DropdownMenu.

For large groups, migrate to a modal sheet/dialog with searchable list.

Each row:

~~~text
(●) JP-Tokyo-01     42 ms
( ) JP-Tokyo-02     57 ms
( ) DIRECT           —
~~~

Do not run delay tests for every node automatically when the page opens.

This wastes power and network resources.

### 13.3 Delay testing

Tap:

~~~text
Test delay
~~~

State:

~~~text
Testing…
~~~

Success:

~~~text
42 ms
~~~

Failure:

~~~text
Failed
~~~

Do not infer "good/bad" exclusively from color.

Optional textual quality categories are allowed:

- Fast
- Normal
- Slow
- Failed

---

## 14. Error presentation

Three levels:

### Inline recoverable

Examples:

- subscription refresh failed;
- delay test timeout;
- profile deletion failed.

Use card/inline message.

### Blocking proxy error

Examples:

- libmihomo failed to load;
- bridge ABI mismatch;
- no active profile while proxy required;
- SOCKS/controller listener did not start;
- Strict VPN failed.

Use a blocking recovery UI:

~~~text
Proxy unavailable

Browsing is blocked to prevent a direct connection.

Reason:
SOCKS listener could not start.

[Retry]
[Choose profile]
[Use direct once]
~~~

### Build/incompatibility error

Example:

~~~text
libmihomo bridge ABI mismatch
~~~

Explain that reinstalling/updating the application is required.

Do not suggest editing profile YAML for a native ABI problem.

---

## 15. Direct-once recovery

"Use direct once" is session-scoped.

Requirements:

- it does not silently disable fail-closed preference;
- it does not permanently disable Mihomo;
- restart/app relaunch should try configured proxy mode again;
- status must visibly say Temporary Direct.

Recommended status:

~~~text
Temporary Direct

Mihomo is bypassed for this app session.
Your saved proxy setting is still enabled.

[Retry Mihomo]
~~~

---

## 16. Zashboard entry

Native button:

~~~text
[Open Zashboard]
~~~

Optional secondary text:

~~~text
Advanced proxy, provider, rule and connection management
~~~

Do not call this simply "Dashboard" without context.

### 16.1 Zashboard loading

Use a dedicated Activity/WebView.

Loading state:

~~~text
Opening Zashboard…
~~~

If Mihomo is not running:

~~~text
Start Mihomo before opening Zashboard.
~~~

### 16.2 Security boundary

Zashboard:

- loads from bundled assets;
- has no arbitrary Android JavaScript bridge;
- is not inserted into browser tab history;
- uses loopback controller + generated secret;
- cannot upgrade the embedded core;
- cannot control Android TUN lifecycle.

### 16.3 External navigation

Links leaving local Zashboard must open through normal browser handling.

Do not allow Zashboard's dedicated WebView to become a general browser.

---

## 17. Diagnostics

Place near the bottom of the screen.

Basic component information:

~~~text
Embedded components

libmihomo 0.3.2-alpha.20260827
mihomo v1.19.31-...
bridgeABI 3
Zashboard 3.24.0
~~~

Runtime diagnostics:

~~~text
Transport: Browser Proxy
Profile: Japan
Runtime: Running
SOCKS: 127.0.0.1:<redacted/runtime-port>
Controller: Ready
WebView Proxy API: Supported
~~~

Do not display the controller secret.

Do not display a full subscription URL.

Optional future:

~~~text
[Copy diagnostics]
~~~

If implemented, all values must pass SensitiveValueRedactor first.

---

## 18. E-Ink design requirements

The Mihomo UI inherits EinkBro's E-Ink-first philosophy.

### 18.1 Motion

Avoid:

- spring animations;
- animated gradients;
- continuous spinners where a static progress message is sufficient;
- continuously animated traffic charts.

Prefer:

- instant state transitions;
- simple progress indicator only while an action is genuinely running;
- no decorative transitions.

### 18.2 Contrast

Every element must remain understandable in grayscale.

Avoid:

- pale gray text on white;
- color-only status;
- low-contrast outlined buttons.

### 18.3 Refresh cost

Minimize whole-screen recomposition and WebView invalidation.

Traffic/delay refresh should be user-driven in MVP.

Zashboard WebView should be created lazily and destroyed on close.

### 18.4 Typography

Reuse the existing EinkBro typography and system scaling.

Do not bundle an additional native UI font merely for Mihomo controls.

Zashboard uses its pinned no-fonts distribution.

---

## 19. Accessibility

Requirements:

- all switches have labels and summaries;
- all icons have meaningful content descriptions where needed;
- status is readable by TalkBack;
- proxy state is not color-only;
- tap targets follow Android minimum sizing conventions;
- radio controls and labels must be clickable as one logical row;
- dynamic errors should be announced appropriately where practical;
- system font scaling must not clip proxy/group names.

Long proxy names:

- prefer wrapping to truncation when practical;
- if truncated, preserve full name in a detail/picker view.

---

## 20. Screen state model

Recommended UI state:

~~~kotlin
data class ProxyUiState(
    val enabled: Boolean,
    val failClosed: Boolean,
    val transportMode: ProxyTransportMode,
    val runtimeStatus: RuntimeUiStatus,
    val activeProfileId: String,
    val profiles: List<ProfileRecord>,
    val routingMode: RoutingMode,
    val groups: List<ProxyGroup>,
    val traffic: TrafficSnapshot,
    val delays: Map<String, Int>,
    val busyAction: ProxyBusyAction?,
    val error: ProxyUiError?,
)
~~~

Prefer explicit busy action over a single generic busy boolean long-term.

Example:

~~~kotlin
sealed interface ProxyBusyAction {
    data object Starting : ProxyBusyAction
    data object Stopping : ProxyBusyAction
    data class RefreshingSubscription(val profileId: String) : ProxyBusyAction
    data class TestingDelay(val proxyName: String) : ProxyBusyAction
    data class SwitchingProxy(val group: String) : ProxyBusyAction
}
~~~

This allows UI to disable only affected controls.

---

## 21. Navigation rules

Settings navigation:

~~~text
Settings
 -> Proxy & Mihomo
 -> Back
 -> Settings
~~~

Zashboard:

~~~text
Proxy & Mihomo
 -> Open Zashboard
 -> dedicated ProxyDashboardActivity
 -> Back
 -> Proxy & Mihomo
~~~

VPN permission:

~~~text
Proxy & Mihomo
 -> Android system VPN dialog
 -> return to same Proxy screen
~~~

Document picker:

~~~text
Proxy & Mihomo
 -> system file picker
 -> return to same Proxy screen
~~~

No action should accidentally finish the entire Settings activity unless intentionally leaving Settings.

---

## 22. Interaction disabling

While a runtime restart is occurring:

Disable:

- profile selection;
- transport switching;
- enable toggle where it could produce conflicting requests.

Keep available:

- Back;
- diagnostics where safe.

While testing one delay:

- only that node's test action should be disabled;
- other unrelated controls should remain usable where safe.

While refreshing one subscription:

- only that profile should show Refreshing;
- do not globally lock the screen unless active runtime replacement is in progress.

---

## 23. Confirmation dialogs

Avoid unnecessary confirmation dialogs.

Require confirmation for:

### Delete profile

~~~text
Delete "Japan"?

This removes the local profile data from this device.

[Cancel] [Delete]
~~~

If it is active:

~~~text
This is the active profile.
Proxying will be disabled after deletion.
~~~

No confirmation needed for:

- switching proxy node;
- testing delay;
- refreshing traffic;
- opening Zashboard.

---

## 24. Empty states

No profiles:

~~~text
No Mihomo profiles

Import a YAML file or add an HTTPS subscription to start.

[Import YAML]
[Add subscription]
~~~

No selectable proxy groups:

~~~text
No proxy groups available in this profile.
~~~

Zashboard remains available only if the controller/runtime is running.

---

## 25. Onboarding behavior

MVP does not need a multi-page onboarding wizard.

First-use flow can remain:

~~~text
Settings
 -> Proxy & Mihomo
 -> Import YAML / Add subscription
 -> select profile
 -> Enable Mihomo
~~~

If future onboarding is added, it must reuse the same underlying controls and repositories.

Do not create a separate hidden configuration path.

---

## 26. Localization

All user-visible strings must use Android string resources.

Default locale may be English.

Existing locale files are allowed to fall back to default strings until translated.

Do not hardcode user-facing strings inside Compose functions except temporary debug text.

Component/version values themselves are not translated.

---

## 27. Security-driven UI rules

The UI must not expose controls for:

- allow-lan;
- bind-address;
- external-controller bind address;
- controller secret;
- arbitrary local listener creation;
- core binary URL;
- core update;
- TUN low-level fd settings.

These are application-owned security invariants.

Advanced profile YAML may contain such keys, but RuntimeConfigBuilder overrides them.

The UI should not imply that imported values for these fields will be honored.

---

## 28. Performance requirements

Native proxy page:

- opening the screen should not restart Mihomo;
- opening the screen should not test every node;
- opening the screen should not refresh subscriptions;
- Proxy groups should load through the existing runtime state/action layer;
- operations must run outside the main thread.

Zashboard:

- lazy-create WebView;
- destroy when closed;
- do not preload during normal browser startup.

---

## 29. Responsive layout

Phone / narrow E-Ink:

- single column;
- buttons may wrap;
- proxy group actions stack if needed.

Tablet / wide E-Ink:

- still prefer one readable main column;
- maximum content width may be constrained;
- do not stretch proxy cards across very wide screens without limit.

A two-column dense VPN-dashboard layout is not required.

---

## 30. Acceptance criteria

The frontend is considered complete for Browser Proxy MVP when:

- [ ] Settings has a visible Proxy & Mihomo entry.
- [ ] Proxy page communicates OFF/STARTING/PROTECTED/BLOCKED/ERROR/DIRECT states.
- [ ] User can import local YAML.
- [ ] User can create an HTTPS subscription.
- [ ] User can select/delete profiles.
- [ ] Subscription tokens are never displayed.
- [ ] User can enable/disable Mihomo.
- [ ] User can switch Browser Proxy / Strict VPN.
- [ ] Strict VPN selection uses Android system permission flow.
- [ ] Fail-closed is enabled by default.
- [ ] Fail-closed failure provides Retry and explicit Direct Once actions.
- [ ] Routing mode can be changed.
- [ ] Mihomo DIRECT is visually distinct from browser direct.
- [ ] Proxy groups show current selection.
- [ ] User can switch proxy nodes.
- [ ] User can test selected-node delay.
- [ ] Traffic values are visible without continuous repaint.
- [ ] Local bundled Zashboard can be opened.
- [ ] Zashboard cannot upgrade core or control Android TUN.
- [ ] Component versions are visible.
- [ ] No controller secret appears in normal UI.
- [ ] No full subscription URL appears in normal UI.
- [ ] Status meaning survives grayscale rendering.
- [ ] TalkBack can identify important switches/actions.
- [ ] Cold start does not create a WebView before proxy readiness when proxy-on-start is active.

Strict VPN UI is release-ready only when the system-level VPN/TUN tests listed in the implementation plan also pass.

---

## 31. Recommended follow-up refinements

After MVP stability:

- toolbar proxy status shortcut;
- searchable large-node picker;
- per-profile update timestamp formatting;
- structured diagnostics export;
- optional manual provider refresh shortcuts;
- better per-operation progress states;
- E-Ink optimized Zashboard CSS override if maintainable;
- optional compact status indicator in browser menu.

Do not implement these before core proxy correctness and leak prevention are proven.

---

## 32. Ownership rule

~~~text
Native Compose UI
  = fast browser-centric controls

Android system UI
  = VPN permission

Dedicated local Zashboard
  = advanced Mihomo management

MihomoEngine / repositories
  = runtime and data state

BrowserNetworkGateway
  = protected/direct/blocked WebView networking
~~~

The frontend must preserve these boundaries and must not directly call libmihomo/JNI.
