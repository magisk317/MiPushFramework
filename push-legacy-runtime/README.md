# push-legacy-runtime

Planned home for vendored Xiaomi push runtime sources that remain necessary for the
system-package-compatible `com.xiaomi.xmsf` build, but should no longer be edited as product
feature code.

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
