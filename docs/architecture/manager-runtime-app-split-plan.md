# Manager And Runtime Application Split Plan

## Current packaging (authoritative)

| Piece | Package / module | Role |
| --- | --- | --- |
| Runtime APK | `com.xiaomi.xmsf` (`:app` + `:xmsf`) | Push runtime, Binder `ManagerRuntimeService`, no manager UI |
| Manager APK | `io.github.magisk317.mipush` (`:mipush` + `:manager`) | Compose UI + Xposed module |
| UI library | `:manager` | Reusable UI; starts only as **remote host** |
| Wire | `:manager-api` / `:manager-client` | Versioned Binder protocol + client |

 inter-package rule: manager never loads XMSF Koin; XMSF never bootstraps manager UI.

Build tasks (no composition/bundled/split flavors):

- `:app:assembleNormalDebug` / `:app:assembleNormalRelease` (plus `vc105*`)
- `:mipush:assembleDebug` / `:mipush:assembleRelease`

Legacy XMSF component names: thin `activity-alias` → `ManagerUiRedirectActivity` → manager package.

## Post-split status

| Item | Status |
| --- | --- |
| Dual APK packaging | **Done** |
| `MiPushHostApp` empty (no manager bootstrap) | **Done** |
| `ManagerDependencies.startAsRemoteHost` / `ensureStarted` | **Done** |
| Production data plane remote-primary (`Remote*Source` → VM) | **Done** |
| `Comparing*` dual-source harness | **Test-only** (`manager/src/test/.../Comparing*`) |
| Settings/runtime pref writes via Binder | **Done** (allowlisted keys) |
| Write path `RemoteWriteSupport.execute` (suspend) + `executeBlocking` bridge for sync façades | **Done** (Gateway full suspend still follow-up) |
| In-app navigation prefers same-package intents | **Mostly done**; `LegacyUiEntryPoints` for cross-package only |

## Data-plane idiom (chosen)

- **Read path:** `Remote*Source` + `ManagerRuntimeClient` (suspend, explicit unavailable statuses)
- **Write path:** `RemoteWriteSupport` / `ManagerRuntimeClient.write*` (Binder ops)
- **`Manager*Gateway`:** compatibility façade over the same remote stack for older UI call sites;
  new code should prefer Source/Client. Gateway binder methods are moving to `suspend`.

## Historical phase narrative

The long Phase 1–6 migration write-up (in-process primary, Comparing, bundled composition, etc.) is
**archived** and must not drive new work:

- [`archive/manager-runtime-app-split-plan-historical.md`](archive/manager-runtime-app-split-plan-historical.md)

Where that file conflicts with this document, **this document wins**.
