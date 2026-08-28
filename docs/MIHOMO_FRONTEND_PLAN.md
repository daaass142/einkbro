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

- [x] inventory current Proxy UI implementation;
- [x] define `RuntimeUiStatus`;
- [x] replace ambiguous global `busy` with typed `ProxyAction`;
- [x] define typed `ProxyUiError`;
- [x] add UI mappers;
- [x] confirm all proxy/runtime state comes through ViewModel/domain interfaces;
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

- [x] implement `RuntimeStatusCard`;
- [x] add Off/Starting/Protected/Blocked/Error/Temporary Direct states;
- [x] implement Browser Proxy vs Strict VPN selector;
- [x] wire system VPN permission;
- [x] keep previous transport if permission denied;
- [x] show fail-closed option;
- [x] implement Retry and Direct Once actions;
- [x] add grayscale-safe text/status treatment.

Tests:

- [x] state mapper tests;
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

- [x] profile empty state;
- [x] import YAML action;
- [x] add subscription dialog;
- [x] safe subscription host display;
- [x] active profile radio/selection;
- [x] refresh state per profile;
- [x] delete confirmation;
- [x] active-profile deletion behavior;
- [x] last update status;
- [x] error redaction.

Tests:

- [x] URL token never rendered;
- [ ] failed refresh keeps active profile;
- [ ] deleting active profile behaves explicitly;
- [x] loading state scoped to one profile.

Commit:

```text
feat(proxy-ui): complete profile and subscription UX
```

## Phase F3 — Routing and node control

Goal: cover frequent Mihomo controls natively.

Tasks:

- [x] Rule / Global / Direct via Mihomo selector;
- [x] proxy group cards;
- [x] preserve runtime group order;
- [x] replace production DropdownMenu with searchable node picker dialog;
- [x] add node search;
- [x] lazy list for large groups;
- [x] manual delay test;
- [x] show current node delay;
- [x] per-action loading;
- [x] traffic summary.

Tests:

- [ ] node switch does not restart core;
- [ ] Direct via Mihomo does not clear browser proxy;
- [x] 500+ node filtering is covered by unit tests;
- [ ] delay timeout state;
- [ ] no all-node test on screen open.

Commit:

```text
feat(proxy-ui): add production routing and node picker UX
```

## Phase F4 — Zashboard production integration

Goal: make the bundled Zashboard the advanced console.

Tasks:

- [x] keep exact pinned `v3.24.0`;
- [x] keep verified `dist-no-fonts.zip`;
- [x] verify archive extraction in CI;
- [x] assert generated `index.html` exists;
- [x] use `WebViewAssetLoader`;
- [x] use HTTP appassets origin intentionally while the loopback controller is HTTP, with mixed content disabled;
- [x] load `/zashboard/index.html#/setup`;
- [x] pass loopback host/port/secret;
- [x] pass `disableUpgradeCore=1`;
- [x] pass `disableTunMode=1`;
- [x] block arbitrary dashboard navigation;
- [x] open external links in normal browser;
- [x] clear dashboard history;
- [x] destroy WebView on close;
- [x] no `addJavascriptInterface`.

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

- [x] remove unnecessary native proxy animations/spinners;
- [ ] check grayscale status;
- [ ] check dark/light theme;
- [ ] check 200% font scale;
- [x] add semantic roles to interactive switch/radio/node rows;
- [x] enforce large tap targets;
- [x] keep native traffic refresh manual;
- [x] avoid auto node tests;
- [ ] check long profile/node names;
- [ ] check portrait/landscape;
- [x] constrain and center tablet/wide-screen content width.

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

- [x] diagnostics card;
- [x] runtime/controller/SOCKS readiness;
- [x] component versions;
- [x] WebView feature support;
- [x] redacted last error;
- [x] no secret/full subscription URL;
- [ ] optional copy diagnostics with redaction;
- [ ] release WebView debugging assertion;
- [ ] third-party cookie assertion;
- [x] navigation policy tests.

Commit:

```text
feat(proxy-ui): add safe frontend diagnostics
```

## Phase F7 — CI and release gate

Goal: prevent frontend regressions from reaching debug/release builds.

CI tasks:

- [x] unit tests;
- [x] lint;
- [x] assembleDebug;
- [x] assembleRelease;
- [x] bundleRelease;
- [x] verify Zashboard checksum;
- [x] verify `index.html`;
- [x] verify expected ABIs;
- [x] generate SHA256SUMS;
- [x] publish debug preview after green build.

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


## Current validation boundary

The checked items above mean the implementation exists in code or CI and is
covered by the stated automated checks. They do **not** replace Android hardware
validation.

The following remain release blockers until exercised on a real/emulated Android
environment where applicable:

- Android VPN grant/reject and revoke lifecycle;
- process kill/restart while Strict VPN is active;
- browser fail-closed leak test;
- Strict VPN IPv4/IPv6, TCP/UDP and DNS;
- WebSocket and QUIC/HTTP3 behavior;
- locally bundled Zashboard with Internet disabled except loopback;
- grayscale/E-Ink device review, 200% font scale, rotation and long-name review.

Do not mark these complete based only on JVM tests or APK compilation.
