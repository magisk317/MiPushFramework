# Manager And Runtime Application Split Plan

## Decision

MiPushFramework should move from the current bundled manager/runtime application to two installable
applications while keeping one repository:

- `:app` remains the `com.xiaomi.xmsf` runtime application.
- `:mipush` becomes the `io.github.magisk317.mipush` manager application and continues packaging
  the Xposed module.
- `:manager` remains the reusable Compose UI layer, but stops relying on an in-process XMSF Koin
  container.

This reuses the two APKs that the project already builds. It does not introduce a third application.
The current all-in-one composition remains a transition and compatibility baseline until the split
has device evidence across supported ROMs.

The current architecture documents remain authoritative for shipped behavior until the packaging
switch phase is complete. This document describes the target and migration path, not current
runtime behavior.

## Implementation Status

The Phase 1 transport implementation now exists without changing application packaging:

- `:manager-api` owns the versioned AIDL handshake, capability identifiers, and size-framed wire
  DTOs;
- XMSF exposes a main-process Binder service behind a signature permission and per-call caller
  verification;
- `:manager-client` models missing, incompatible, denied, timed-out, and transiently disconnected
  states, and reconnects through a fresh handshake after Binder death;
- the connection snapshot has a complete wire mapping, including keepalive and ping intervals;
- the XMSF-packaged manager publishes the existing in-process snapshot first and compares the
  Binder result asynchronously without exposing snapshot values in mismatch diagnostics;
- `:mipush` owns a process-scoped transport probe that exercises the real manager package identity
  while keeping unavailable and unsupported runtime states non-blocking.

The Phase 2 application read slice now exists behind the same transport:

- `:manager-api` raises the protocol minor, adds the `application_list`, `application_detail`, and
  `application_diagnostics` capabilities, and defines size-framed, primitive-only DTOs for the
  application query, page, summary, detail, stats, and diagnostics surfaces with keyset pagination;
- XMSF adds a read-only application reader that projects installed packages and stored registration
  rows without persisting a reconciliation result, and maps them to the wire DTOs behind the Binder
  service;
- `:manager-client` exposes typed application page, detail, and diagnostics calls, treats a legitimate
  not-found detail as a successful null result, and keeps a missing capability or malformed response
  local to that call;
- the manager UI keeps its in-process gateway as the primary path and compares the Binder result
  asynchronously, reporting only field names without leaking snapshot values.

The manager UI still uses the existing in-process gateways as its primary path. Gradle unit tests,
detekt, and the bundled app compilation cover the local Phase 1 and Phase 2 contracts. Cross-package
device and ROM evidence remains pending under the current no-device-test policy, so the all-in-one
path remains the shipped baseline. The next implementation cut is the remaining Phase 2 read paths
(events, configuration catalog, notification-channel summaries, and log export); each screen stays on
its existing gateway until its Binder result has comparison evidence.

## Prior Art And Rejected Paths

The upstream history contains a complete earlier attempt and its reversals:

- `ee6b6f796` split the manager and push service into two APKs with `IPushController` AIDL, explicit
  binding, and Binder death notification. It also ignored the `bindService` result, exposed no
  protocol version, used a normal-level bind permission without per-call package/signature checks,
  and offered a synchronous helper that waited up to ten seconds.
- `9563cab855` and `5eba16d3ae` then patched unbind/retry edge cases around disconnects. They did
  not establish explicit session ownership; those failure modes now have dedicated lifecycle tests
  instead of catch-and-retry patches.
- `94cb74f1fa` replaced the primary Binder route with `ContentProvider.call`, including a synthetic
  300 ms ready callback. Its exported provider retained normal-level read access, and the route had
  no durable death/reconnect boundary, so it is unsuitable as the general management RPC transport.
- `7a604baf39` fused manager and push back into one APK. Later, `7147ba4858` removed cross-module
  database access and `8422a15696` folded the single-consumer provider module into push. This shows
  that storage ownership matters, but extra runtime modules do not create an application boundary.
- `1259427f53` and `28038fd3c5` subsequently extracted service abilities and dependency interfaces
  while retaining the single runtime APK. The reusable lesson is interface ownership inside the
  runtime, not another installable service or shared database.
- `96dfb0afd` exposed a permission-protected provider for plugin APIs, while `796fe47bf` briefly
  introduced an empty SDK module and `65fb15c9d` reverted that standalone boundary. These are
  provider/plugin precedents, not evidence for a general manager-to-runtime transport.

The current fork already established the corresponding domain seams in `ba13135608`, `64cd3e3cbd`,
and `f87c235693`. The earlier `06d19a936` split `:app` and `:manager` only as modules inside one
APK, and `e2735f018` documents why `compileOnly` dependencies appeared to work while both halves
were co-packaged. It also folded the single-consumer `runtime-android-core` module back into XMSF
in `c682ac416b` (after the `98824b9bd`/`0aa478589` extraction) and removed the redundant protocol
module in `0623d89c4`. Therefore this plan reuses the gateway/adapter work, adds only the
cross-application `manager-api` and `manager-client` boundaries, and keeps the all-in-one build as
a measured fallback. It does not reuse Android-owned gateway objects as wire DTOs or recreate a
one-consumer runtime module.

## Why This Direction

The reference comparison identifies the real trade-off:

- bundling avoids cross-application IPC and ROM restrictions, but forces the UI into the
  `com.xiaomi.xmsf` package and couples manager delivery to runtime delivery;
- splitting lets the manager use its normal application identity, evolve independently, and be
  distributed separately, but introduces IPC availability and compatibility risks.

The repository is already close enough to make a staged split practical:

- `:mipush` is an installable `io.github.magisk317.mipush` APK, but currently has only a minimal
  `Application` and no manager launcher;
- `:manager` no longer has a Gradle dependency on `:xmsf`;
- the manager already consumes named gateway interfaces instead of importing the deep Xiaomi
  runtime directly;
- the remaining coupling is concentrated in same-process Koin startup, mixed settings ownership,
  Android-specific gateway types, and shared process/storage assumptions.

The split should therefore happen at the application and transport boundary. Further mechanical
fragmentation of the XMSF runtime would not solve the manager/runtime ownership problem.

## Target Dependency Graph

```text
                         +------------------+
                         |   manager-api    |
                         | protocol + DTOs  |
                         +---------+--------+
                                   ^
                    uses           |           implements
                                   |
+---------+   +----------------+   |   +----------------+   +---------+
| mipush  |-->| manager        |-->|   | xmsf           |<--| app     |
| app host|   | UI/ViewModels  |   |   | runtime/server |   | XMSF APK|
+----+----+   +-------+--------+   |   +----------------+   +---------+
     |                |             |
     |                v             |
     |        +---------------+      |
     +------->| manager-client|------+
              | Binder client |
              +---------------+

mipush also packages :xposed; app does not package :manager after cutover.
```

### `manager-api`

Owns the versioned management protocol, capability identifiers, wire DTOs, and AIDL surface. It
must not depend on runtime implementations in `core`, `common`, `settings`, or `xmsf`.

The existing `common.manager` contracts are useful domain seams but are not an IPC protocol:

- `Context`, `Intent`, `Uri`, and `File` have process/package ownership semantics;
- `NotificationChannel` and `NotificationChannelGroup` are platform objects rather than stable
  wire models;
- unbounded lists and payload byte arrays can exceed Binder transaction limits.

Wire DTOs must use primitives and explicitly versioned parcelables. Large or streamed content uses
`ParcelFileDescriptor`, and collections use bounded pagination.

### `manager-client`

Owns explicit binding to the runtime service, signature/caller validation errors, request timeouts,
Binder death handling, reconnect with backoff, and coroutine/Flow adapters. It exposes typed results
such as available, runtime missing, incompatible, permission denied, timed out, and temporarily
disconnected. A missing capability disables only that feature.

### `manager`

Owns Compose UI, navigation, ViewModels, and UI-only state. It depends on `manager-api`,
`manager-client`, UI libraries, and a manager-local preference store. It ultimately removes its
remaining direct dependency on `core`; externally visible runtime state is represented by
`manager-api` DTOs.

### `mipush`

Becomes the standalone manager host. It owns:

- manager `Application` and Koin bootstrap;
- launcher, wizard, and manager Activity declarations;
- UI preferences and persisted URI grants;
- widgets and user-facing share/open intents;
- the existing Xposed module packaging.

If an application-store build cannot include Xposed metadata, add a `market` variant that excludes
`:xposed`. Do not create a third APK until a real distribution requirement proves that necessary.

### `xmsf` And `app`

`xmsf` remains the runtime owner for the push connection, Room databases, stock compatibility
surfaces, notification operations, effective runtime settings, root/runtime actions, configuration
engine, and log production. It implements the management Binder service and maps wire DTOs to the
existing runtime adapters.

`app` remains the thin `com.xiaomi.xmsf` packaging shell. After cutover it no longer depends on
`:manager`, starts no manager Koin module, and contains no manager widgets. A small compatibility
launcher may remain temporarily to open the standalone manager or show that it is not installed.

## IPC Contract

Use an explicit Binder/AIDL service rather than extending the existing diagnostic or island
ContentProviders into a general RPC surface.

The first call is a handshake that returns:

- protocol major and minor versions;
- runtime application version;
- supported capability identifiers;
- maximum page/payload sizes;
- optional compatibility or degraded-mode reason.

Compatibility rules:

- a major mismatch blocks only remote management and leaves XMSF push runtime operation intact;
- a minor mismatch is allowed through capability negotiation;
- the manager supports at least the current and previous runtime protocol where feasible;
- unknown fields and capabilities are ignored;
- unsupported operations return a typed unsupported result, not a process exception;
- every remote call has a timeout and cancellation boundary.

Use narrow typed operations rather than exposing internal Koin objects or databases. Event and
application queries are paginated. Log bundles, config imports, and other large data use file
descriptors. Runtime change notifications use a registered callback with polling as a fallback.

The current synchronous AIDL methods have an important cancellation limit: coroutine timeout can
release the client session and return a typed timeout, but it cannot forcibly interrupt a Binder
`transact` that is already blocked in the caller process. `manager-client` therefore caps the
number of in-flight blocking calls, quarantines late results, and makes a timed-out feature
skippable; once the cap is exhausted it returns a typed timeout without queueing another blocking
call or starting an unbounded reconnect loop. A future callback/one-way transport is required
before treating cancellation as a hard thread-reclamation guarantee.

## Trust Boundary

The runtime service is exported behind a signature-level permission and also verifies the Binder
calling UID and signing certificate on every transaction. The service accepts only an explicit
component bind from the manager package.

The current XMSF and MiPush APKs use the same release signing configuration, so this model fits the
existing release set. Store distribution must preserve the trusted signing identity. If a store
re-signs the manager, support requires an explicit trusted-certificate allowlist or a deliberate
user pairing design; the service must not be opened broadly to work around store signing.

ROMs that block or kill cross-application binding are treated as a visible degraded state. The
client reconnects, individual features remain skippable, and push delivery continues independently.

## State Ownership

The current `PreferenceRepository` mixes UI and runtime state and cannot simply be copied to the
manager process.

Runtime-owned state stays under `com.xiaomi.xmsf`:

- XMPP host and connection controls;
- push-service foreground/keepalive behavior;
- notification, focus/island, registration, and app policy;
- log and event retention;
- active configuration snapshot;
- registration/event databases and runtime counters.

Manager-owned state moves to `io.github.magisk317.mipush`:

- theme and UI presentation;
- selected tabs, filters, sort, and search history;
- wizard/onboarding progress;
- manager-only remote source editing state;
- Storage Access Framework URI grants.

A SAF URI grant belongs to the manager package and must not be sent to XMSF as though XMSF could
open it. The manager reads selected content and uploads it through a file descriptor; XMSF validates
and atomically replaces its private active configuration snapshot.

The runtime remains authoritative for applications, events, notification channels, and logs. The
manager never opens the XMSF Room database or runtime DataStore directly.

## Migration Phases

### Phase 1: Prove The Transport

Add `manager-api` and `manager-client` without changing APK composition. Implement handshake,
signature authentication, capability negotiation, reconnect, timeouts, and the connection snapshot
as the first read-only vertical slice.

Exit criteria:

- in-process and Binder connection snapshots match in contract tests;
- runtime absent, manager absent, protocol mismatch, binder death, and timeout states are covered;
- unsupported capability handling does not block other manager pages;
- the existing all-in-one build remains unchanged for users.

### Phase 2: Move Read Paths

Move application list/details, events, configuration catalog, notification-channel summaries, and
log export behind the client. Add pagination and file-descriptor tests before moving large data.

Exit criteria:

- manager code no longer accesses runtime databases or platform notification objects directly;
- Binder transaction-size tests cover worst-case pages and payload metadata;
- reconnecting does not duplicate event actions or leak callbacks.

### Phase 3: Split Preferences And Configuration

Classify every existing preference as manager-owned or runtime-owned. Introduce separate stores and
a one-time migration snapshot. Move config content transfer to validated file-descriptor upload and
keep XMSF's active snapshot authoritative.

Exit criteria:

- upgrade from the bundled release preserves effective runtime behavior;
- downgrade has a documented boundary and does not corrupt either store;
- failed config upload leaves the previous active configuration intact.

### Phase 4: Move Write Paths

Remote application state changes, event delete/restore/replay, configuration activation, root
actions, permission repair, Zygisk configuration, and runtime controls. Commands use request IDs and
idempotency rules where a retry could otherwise duplicate work.

Exit criteria:

- every write has a typed success/failure/unsupported result;
- retry after Binder death cannot repeat a destructive action silently;
- caller identity is tested for every privileged endpoint.

### Phase 5: Host Manager In `mipush`

Add `:manager` and `:manager-client` to `:mipush`, start a manager-owned Koin container, move manager
Activity declarations and widgets, and switch internal navigation to explicit package-scoped
actions. Keep the XMSF-packaged manager enabled as an internal comparison build.

Exit criteria:

- a clean install of both APKs completes onboarding and all supported manager flows;
- updating either APK independently produces compatible or explicitly degraded behavior;
- widget and deep-link behavior is verified with XMSF running, stopped, missing, and upgraded.

### Phase 6: Remove Manager From The Default XMSF APK

Remove `:manager` from `:app`, remove `ManagerDependencies` bootstrap from `MiPushHostApp`, and move
the current XMSF widgets to `:mipush`. Keep a thin compatibility launcher for one transition cycle.

Maintain an all-in-one build variant as a regression baseline until split builds pass the ROM
matrix. Decide later, from device evidence, whether that variant remains a supported fallback or is
retired.

## Verification Matrix

The split is not complete from JVM tests alone. Each phase needs:

- protocol unit and compatibility tests for current/previous versions;
- Binder instrumentation tests with process death and rebind;
- unauthorized UID/certificate tests;
- transaction-size, pagination, timeout, and file-descriptor lifecycle tests;
- upgrade tests from the last bundled release;
- normal and vc105 XMSF builds;
- manager builds with and without the optional Xposed/market variant;
- representative AOSP, MIUI/HyperOS, and ROMs known to restrict background or cross-app binding.

Push registration, long connection, downstream delivery, and notification posting must continue
when the manager is missing, incompatible, force-stopped, or unable to bind.

## First Implementation Batch

The first code batch is intentionally limited to:

1. `manager-api` protocol version, capabilities, handshake, and connection snapshot DTO;
2. authenticated runtime Binder service;
3. `manager-client` bind/rebind, Binder death, timeout, and typed availability state;
4. a connection-status adapter that can compare in-process and IPC results;
5. tests for missing runtime, incompatible protocol, missing capability, timeout, and process death.

It must not move databases, write operations, configuration files, Activities, or Koin ownership.
That narrow slice proves whether the split survives the exact IPC and ROM risks identified in the
reference comparison before the project commits to the expensive migration steps.

## Non-Goals

- Splitting the repository or release tags before the IPC contract is stable.
- Moving stock XMSF ABI or vendored runtime behavior into the manager.
- Letting the manager access XMSF private files or databases directly.
- Making an unsupported manager capability block the push runtime or unrelated manager features.
- Removing the all-in-one comparison build before cross-ROM evidence exists.
