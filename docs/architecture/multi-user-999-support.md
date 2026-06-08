# Multi-User And 999-User Support Notes

## Current State

The Xposed module is now user-selectable for third-party application scope. Its packaged
`scope.list` only keeps the hook-required system entries:

- `android` / `system` for system-server hooks.
- `com.android.systemui` for notification island and focus-display hooks.
- `com.xiaomi.xmsf` for XMSF-side notification and authorization hooks.

Third-party applications are intentionally not packaged in the static scope. Users should select
the target main, cloned, or 999-user application manually in LSPosed.

## 999-User Support Boundary

This change removes the static Xposed-scope blocker, but it does not make the whole runtime
multi-user aware yet.

Observed blockers:

- `RegisteredApplication` is keyed by package name only, so main-user and 999-user copies of the
  same package currently share registration status, blocked state, and island preferences.
- `XmsfManagerApplicationGateway.loadPackagesOnDevice(...)` enumerates packages through the
  current process package manager. It has no explicit secondary-user enumeration path.
- `RegistrationStateCompat` only probes primary-user local registration artifacts, so diagnostics
  can miss valid registrations created by cloned apps.
- UI and IPC models such as `ManagerApplication` carry `packageName` but no `userId`, so a cloned
  app cannot be selected or configured independently from the primary-user package.

## Follow-Up Plan

Full 999-user support should be handled as a separate data-model migration:

1. Add an application identity model such as `(packageName, userId)` while preserving package-only
   compatibility for existing data.
2. Migrate registered-application storage, notification preferences, registration diagnostics, and
   event queries to the new identity.
3. Add a package-manager adapter that can enumerate selected user profiles with root or privileged
   APIs when available, and degrade gracefully when not.
4. Teach force-register, local registration probing, and app launch paths to operate against the
   selected user.
5. Add contract tests for package-only migration and cloned-app display behavior before enabling
   user-visible claims of complete 999 support.
