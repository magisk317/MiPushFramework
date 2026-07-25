# MiPushFramework architecture docs

## Start here (authoritative)

| Doc | Purpose |
| --- | --- |
| [boundary-model.md](boundary-model.md) | Module/package boundaries, data-plane idiom, trust rules |
| [manager-runtime-app-split-plan.md](manager-runtime-app-split-plan.md) | **Current** dual-APK packaging + post-split status |
| [current-runtime-call-flow.md](current-runtime-call-flow.md) | Runtime spine from XMSF init → delivery/notify |

## Feature / ROM notes (keep)

| Doc | Purpose |
| --- | --- |
| [connection-status-page.md](connection-status-page.md) | Connection status UI behavior |
| [device-baseline-hyperos3.md](device-baseline-hyperos3.md) | HyperOS 3 device baseline |
| [multi-user-999-support.md](multi-user-999-support.md) | Multi-user / XSpace notes |
| [xposed-notification-boundary.md](xposed-notification-boundary.md) | Xposed notification ownership |

## Archive (historical only — do not implement from these)

See [`archive/`](archive/): old split phase narrative, refactor plans, dump audits, Android 17 impact drafts.

When archive conflicts with **Start here**, the start-here docs win.
