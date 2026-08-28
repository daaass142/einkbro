# EinkBro Mihomo Frontend — Product & UI Specification

Status: implementation-ready  
Scope: native VPN/browser UI + embedded Zashboard

## 1. Product intent

The feature must make embedded Mihomo understandable to a browser user without turning EinkBro into a clone of a desktop Clash client.

The native UI covers the common 20% of actions.

Zashboard covers the advanced 80%.

## 2. Main screen

Route:

```text
Settings -> Proxy & Mihomo
```

Screen order:

1. runtime status;
2. enable proxy;
3. transport;
4. fail-closed;
5. active profile;
6. profile list/actions;
7. routing mode;
8. proxy groups;
9. traffic;
10. Zashboard;
11. diagnostics.

## 3. Runtime status

Required states:

- Off
- Starting
- Protected — Browser Proxy
- Protected — Strict VPN
- Blocked
- Error
- Temporary Direct

The first card must answer:

```text
Am I protected?
How?
Using which profile?
Using which route/node?
```

Example:

```text
Mihomo
Protected · Browser Proxy

Japan · Rule
JP-Tokyo-01 · 42 ms
```

Strict VPN:

```text
Mihomo
Protected · Strict VPN

Japan · Rule
Application-only VPN
```

Blocked:

```text
Browsing blocked

Mihomo is unavailable.
Direct Internet access was not allowed.

[Retry]
[Use direct once]
```

## 4. Enable switch

```text
Enable Mihomo              [switch]
```

ON requires an active profile.

If no profile exists, show an actionable empty state.

Do not silently create a default profile.

OFF is an explicit user request for browser direct networking.

## 5. Transport

Two options:

```text
(●) Browser Proxy (SOCKS5)
    WebView traffic through local Mihomo.
    No Android VPN permission.

( ) Strict application-only VPN
    Captures EinkBro through Android VPN/TUN.
    Other applications are not included.
```

Changing to Strict VPN must invoke Android's system permission flow.

Permission rejection keeps the previous mode.

## 6. Fail-closed

Default ON.

Label:

```text
Block on proxy failure
```

Summary:

```text
Prevent silent direct Internet access when Mihomo is expected to be active.
```

If disabled by the user, direct fallback must still be visibly marked Direct.

## 7. Profiles

Primary actions:

```text
[Import YAML] [Add subscription]
```

Profile item:

```text
● Japan
  Subscription · example.com
  Updated 5 min ago

  [Refresh] [More]
```

Never display full subscription query strings.

### Import

Use system document picker.

Maximum profile size follows backend policy.

### Subscription

Fields:

- name;
- HTTPS URL.

Do not support plaintext HTTP by default.

### Delete

Require confirmation.

Deleting the active profile disables proxying unless another profile is selected during the flow.

## 8. Routing mode

Use:

- Rule
- Global
- Direct via Mihomo

Do not label the third option only as `Direct` if it can be confused with browser bypass.

```text
Direct via Mihomo
  WebView -> Mihomo -> DIRECT outbound

Temporary/Browser Direct
  WebView -> Internet
```

## 9. Proxy groups

Card:

```text
Proxy
JP-Tokyo-01                         42 ms

[Change node] [Test delay]
```

Use configured proxy group ordering.

### Node picker

Use a modal bottom sheet or dialog for production UI.

Features:

- search;
- current selection;
- optional known delay;
- lazy list;
- selection without core restart.

Do not automatically test every node.

## 10. Traffic

Native UI is intentionally simple.

```text
Traffic
↓ 24.8 MiB    ↑ 2.1 MiB
[Refresh]
```

Full graphs belong to Zashboard.

## 11. Zashboard

Button:

```text
Open advanced Zashboard
```

Summary:

```text
Providers, rules, connections, logs and full Mihomo management.
```

The dashboard is local and bundled.

It must work without remote dashboard hosting.

### Bootstrap

Android passes:

- protocol;
- controller host;
- controller port;
- secret;
- `disableUpgradeCore=1`;
- `disableTunMode=1`.

The Zashboard TUN switch is hidden because Android owns VPN/TUN lifecycle.

## 12. Dashboard WebView policy

Required:

- dedicated Activity;
- local `WebViewAssetLoader`;
- JS enabled;
- file access disabled;
- content access disabled;
- mixed content disabled;
- third-party cookies disabled;
- release debugging disabled;
- no broad JavaScript interface;
- external navigation leaves the dashboard.

The dashboard must never become a generic browser tab.

## 13. Strict VPN UI flow

```text
Tap Strict VPN
 -> system permission request
 -> permission granted
 -> status "Starting"
 -> TUN established
 -> libmihomo TUN running
 -> status "Protected · Strict VPN"
```

If revoked:

```text
Protected
 -> VPN revoke
 -> browser immediately blocked
 -> show recovery UI
```

## 14. Temporary Direct

This is session-scoped.

It must not change the stored fail-closed preference.

UI:

```text
Temporary Direct

Mihomo is bypassed for this app session.
The saved proxy configuration is unchanged.

[Retry Mihomo]
```

On next app launch, normal configured proxy startup is attempted again.

## 15. Diagnostics

Show:

- app version;
- libmihomo version;
- Mihomo version;
- bridge ABI;
- Zashboard version;
- transport;
- active profile;
- runtime state;
- SOCKS readiness;
- controller readiness;
- WebView Proxy support;
- redacted last error.

Never show:

- controller secret;
- full subscription token;
- raw authentication header.

## 16. E-Ink design

Required:

- high contrast;
- grayscale-safe states;
- text status, not color-only;
- minimal animation;
- minimal continuous refresh;
- system font;
- large readable hit targets;
- simple single-column layout.

Avoid decorative cards nested inside cards.

Avoid live charts in native UI.

## 17. Responsive behavior

Phones:

- one column;
- action buttons may wrap;
- node picker uses bottom sheet/dialog.

Tablets/E-Ink readers:

- constrained readable content width;
- remain primarily one column;
- do not create desktop dashboard density in native UI.

Zashboard itself may use its own responsive mobile layout.

## 18. Accessibility

Required:

- status readable by TalkBack;
- switches have labels and summaries;
- radio row label is clickable;
- errors announced where practical;
- 200% font scaling does not hide important actions;
- state is never conveyed by color alone.

## 19. Loading/progress

Do not globally lock the screen for small operations.

Typed actions:

- Starting runtime
- Switching transport
- Refreshing profile
- Testing node
- Switching node
- Opening dashboard

Only affected controls should be disabled where safe.

## 20. Empty states

No profile:

```text
No Mihomo profiles

Import a YAML file or add an HTTPS subscription.

[Import YAML]
[Add subscription]
```

No groups:

```text
No selectable proxy groups in this profile.
```

Mihomo stopped and dashboard requested:

```text
Start Mihomo before opening Zashboard.
```

## 21. User-facing terminology

Preferred:

- Mihomo
- Browser Proxy
- Strict VPN
- Protected
- Blocked
- Temporary Direct
- Direct via Mihomo
- Profile
- Subscription
- Proxy group
- Node
- Advanced Zashboard

Avoid exposing internal terms unless diagnostic:

- fd
- JNI
- bridge internals
- cgo
- listener recreation
- controller secret

## 22. Acceptance criteria

- [ ] Settings entry exists.
- [ ] Runtime status reflects reality, not only preference.
- [ ] Browser Proxy and Strict VPN are clearly different.
- [ ] Strict VPN uses system permission UI.
- [ ] Fail-closed defaults ON.
- [ ] No-profile state is actionable.
- [ ] Local profile import works.
- [ ] HTTPS subscription works.
- [ ] Sensitive subscription URL is redacted.
- [ ] Routing mode is editable.
- [ ] Direct via Mihomo is not confused with browser direct.
- [ ] Proxy groups show selected nodes.
- [ ] Node picker supports large groups.
- [ ] Delay testing is manual.
- [ ] Traffic summary exists.
- [ ] Advanced Zashboard opens locally.
- [ ] Zashboard core upgrade is disabled.
- [ ] Zashboard TUN UI is disabled.
- [ ] Dashboard works offline except loopback Mihomo.
- [ ] No Android JS bridge exposes privileged app APIs.
- [ ] Diagnostics contains no secrets.
- [ ] VPN loss becomes Blocked under fail-closed.
- [ ] UI remains understandable in grayscale.
