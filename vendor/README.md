# vendor

Home for vendored Xiaomi push runtime sources that remain necessary for the
system-package-compatible `com.xiaomi.xmsf` build, but should no longer be edited as product
feature code.

The module name is `vendor` (not `legacy`): this code is **active, load-bearing runtime**
that is packaged into every build (it carries the long-connection `XMPushService`, the
SMACK/Slim transport, and telemetry). "vendor" describes its *provenance and edit policy*
— third-party Xiaomi code that is frozen and consumed through facades — not that it is
dead or deprecated.

Examples:
- `com.xiaomi.channel.*`
- `com.xiaomi.network.*`
- `com.xiaomi.smack.*`
- `com.xiaomi.slim.*`
- `com.xiaomi.clientreport.*`
- `com.xiaomi.stats.*`
- `com.xiaomi.tinyData.*`

Rules:
- Preserve external behavior and binary/wire compatibility.
- Expose only focused facades to product-owned code.
- Prefer deletion or replacement over feature growth inside this module.
