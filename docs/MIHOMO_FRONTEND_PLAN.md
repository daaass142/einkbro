# EinkBro Mihomo Frontend — Implementation Plan

Status: executable  
Target branch: `feat/mihomo-browser`

Related:

- `MIHOMO_FRONTEND_TECH_STACK.md`
- `MIHOMO_FRONTEND_ARCHITECTURE.md`
- `MIHOMO_FRONTEND_SPEC.md`
- `MIHOMO_BROWSER_UI_SPEC.md`

## Phase F0 — Baseline and UI state model

Goal: stabilize the frontend contract before visual polish.

Tasks:

- [ ] inventory current Proxy UI implementation;
- [ ] define `RuntimeUiStatus`;
- [ ] replace ambiguous global `busy` with typed `ProxyAction`;
- [ ] define typed `ProxyUiError`;
- [ ] add UI mappers;
- [ ] confirm all state comes through ViewModel/domain interfaces;
- [ ] remove any direct runtime/storage calls from composables.

Acceptance:

- Compose renders only immutable UI state;
- UI events go through ViewModel;
- no Compose/JNI coupling.

Commit:

```text
refactor(proxy-ui): define typed frontend state model
```

## Phase F1 — Main proxy status and transport UX

Goal: make protection state obvious.

Tasks:

- [ ] implement `RuntimeStatusCard`;
- [ ] add Off/Starting/Protected/Blocked/Error/Temporary Direct states;
- [ ] implement Browser Proxy vs Strict VPN selector;
- [ ] wire system VPN permission;
- [ ] keep previous transport if permission denied;
- [ ] show fail-closed option;
- [ ] implement Retry and Direct Once actions;
- [ ] add grayscale-safe icons/text.

Tests:

- [ ] state mapper tests;
- [ ] permission denial;
- [ ] Strict VPN start;
- [ ] VPN revoke -> Blocked;
- [ ] Direct Once is session-only.

Commit:

```text
feat(proxy-ui): add runtime status and transport controls
```

## Phase F2 — Profile and subscription UX

Goal: make profile setup understandable without exposing secrets.

Tasks:

- [ ] profile empty state;
- [ ] import YAML action;
- [ ] add subscription dialog;
- [ ] safe subscription host display;
- [ ] active profile radio/selection;
- [ ] refresh state per profile;
- [ ] delete confirmation;
- [ ] active-profile deletion behavior;
- [ ] last update status;
- [ ] error redaction.

Tests:

- [ ] URL token never rendered;
- [ ] failed refresh keeps active profile;
- [ ] deleting active profile behaves explicitly;
- [ ] loading state scoped to one profile.

Commit:

```text
feat(proxy-ui): complete profile and subscription UX
```

## Phase F3 — Routing and node control

Goal: cover frequent Mihomo controls natively.

Tasks:

- [ ] Rule / Global / Direct via Mihomo selector;
- [ ] proxy group cards;
- [ ] preserve runtime group order;
- [ ] replace production DropdownMenu with node picker sheet/dialog;
- [ ] add node search;
- [ ] lazy list for large groups;
- [ ] manual delay test;
- [ ] show current node delay;
- [ ] per-action loading;
- [ ] traffic summary.

Tests:

- [ ] node switch does not restart core;
- [ ] Direct via Mihomo does not clear browser proxy;
- [ ] 500+ node list remains usable;
- [ ] delay timeout state;
- [ ] no all-node test on screen open.

Commit:

```text
feat(proxy-ui): add production routing and node picker UX
```

## Phase F4 — Zashboard production integration

Goal: make the bundled Zashboard the advanced console.

Tasks:

- [ ] keep exact pinned `v3.24.0`;
- [ ] keep verified `dist-no-fonts.zip`;
- [ ] verify archive extraction in CI;
- [ ] assert generated `index.html` exists;
- [ ] use `WebViewAssetLoader`;
- [ ] prefer HTTPS appassets origin;
- [ ] load `/zashboard/index.html#/setup`;
- [ ] pass loopback host/port/secret;
- [ ] pass `disableUpgradeCore=1`;
- [ ] pass `disableTunMode=1`;
- [ ] block arbitrary dashboard navigation;
- [ ] open external links in normal browser;
- [ ] clear dashboard history;
- [ ] destroy WebView on close;
- [ ] no `addJavascriptInterface`.

Integration tests:

- [ ] dashboard index loads;
- [ ] JS/CSS assets load;
- [ ] dashboard works with Internet disabled except loopback;
- [ ] proxy list loads;
- [ ] node switch works;
- [ ] connection page works;
- [ ] no upgrade-core button;
- [ ] no TUN toggle.

Commit:

```text
feat(dashboard): harden bundled Zashboard integration
```

## Phase F5 — E-Ink and accessibility pass

Goal: make the feature feel native to EinkBro.

Tasks:

- [ ] remove unnecessary animations;
- [ ] check grayscale status;
- [ ] check dark/light theme;
- [ ] check 200% font scale;
- [ ] content descriptions;
- [ ] large tap targets;
- [ ] minimize native traffic repaint frequency;
- [ ] avoid auto node tests;
- [ ] check long profile/node names;
- [ ] check portrait/landscape;
- [ ] check tablet content width.

Optional:

- reduced-motion dashboard CSS only if upstream dashboard is problematic on E-Ink;
- keep this as a minimal overlay/patch, not a fork.

Acceptance:

- all primary actions usable on E-Ink;
- no state relies on color;
- no persistent animation.

Commit:

```text
feat(proxy-ui): optimize Mihomo controls for E-Ink
```

## Phase F6 — Diagnostics and frontend security

Goal: give enough support information without leaking secrets.

Tasks:

- [ ] diagnostics card;
- [ ] runtime/controller/SOCKS readiness;
- [ ] component versions;
- [ ] WebView feature support;
- [ ] redacted last error;
- [ ] no secret/full subscription URL;
- [ ] optional copy diagnostics with redaction;
- [ ] release WebView debugging assertion;
- [ ] third-party cookie assertion;
- [ ] navigation policy tests.

Commit:

```text
feat(proxy-ui): add safe frontend diagnostics
```

## Phase F7 — CI and release gate

Goal: prevent frontend regressions from reaching debug/release builds.

CI tasks:

- [ ] unit tests;
- [ ] lint;
- [ ] assembleDebug;
- [ ] assembleRelease;
- [ ] bundleRelease;
- [ ] verify Zashboard checksum;
- [ ] verify `index.html`;
- [ ] verify expected ABIs;
- [ ] generate SHA256SUMS;
- [ ] publish debug preview after green build.

Manual release blockers:

- [ ] Android VPN grant/reject;
- [ ] Android VPN revoke;
- [ ] process kill/restart;
- [ ] browser fail-closed;
- [ ] WebView proxy path;
- [ ] Strict VPN IPv4;
- [ ] Strict VPN IPv6;
- [ ] TCP;
- [ ] UDP;
- [ ] DNS;
- [ ] WebSocket;
- [ ] QUIC behavior;
- [ ] local Zashboard offline;
- [ ] E-Ink grayscale.

Commit:

```text
ci(proxy-ui): enforce frontend release gate
```

## Recommended development order

```text
F0 State model
 -> F1 Protection/transport
 -> F2 Profiles
 -> F3 Nodes/routing
 -> F4 Zashboard
 -> F5 E-Ink/accessibility
 -> F6 Diagnostics/security
 -> F7 Release gate
```

Do not start by visually restyling Zashboard.

Correct Android lifecycle and security boundaries come first.

## Definition of Done

Frontend implementation is complete when:

- native page handles all common browser/VPN actions;
- Zashboard handles advanced Mihomo management;
- Android owns all VPN lifecycle;
- Zashboard cannot control core upgrade/TUN lifecycle;
- no duplicated source of truth exists;
- no sensitive token/secret appears in normal UI;
- local dashboard works offline;
- fail-closed status is visible and enforced;
- tests/lint/debug/release build are green;
- system-level VPN release blockers pass on Android.
