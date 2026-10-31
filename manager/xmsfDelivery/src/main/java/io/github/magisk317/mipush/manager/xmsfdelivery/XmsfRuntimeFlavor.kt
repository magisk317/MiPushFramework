package io.github.magisk317.mipush.manager.xmsfdelivery

/**
 * Runtime build variant a device runs, and the channel that variant updates from.
 *
 * `normal` and `vc105` share one versionName and differ only in the runtime
 * versionCode: the `vc105` tier keeps the version code stock Xiaomi ships, for
 * apps that compare against it. An update must therefore never cross tiers —
 * the versionName compare that decides "is this runtime current" cannot tell
 * the two apart, so each tier gets its own generic-package registry package and
 * its own ci-dist staging root, and every manifest is gated on the tier's own
 * versionCode.
 *
 * The tier comes from the installed package, not from a preference: a device
 * with nothing installed yet has no tier to honour, and [Normal] is the tier
 * the release pipeline has always published.
 */
enum class XmsfRuntimeFlavor(
    val label: String,
    val versionCode: Long,
    /** Generic package name holding this tier's runtime payload. */
    val registryPackage: String,
    /** ci-dist staging root named after the runtime package, suffixed per tier. */
    val ciDistRoot: String,
) {
    Normal("normal", BuildConfig.XMSF_RUNTIME_NORMAL_VERSION_CODE, "xmsf-runtime", "com.xiaomi.xmsf"),
    Vc105("vc105", BuildConfig.XMSF_RUNTIME_VC105_VERSION_CODE, "xmsf-runtime-vc105", "com.xiaomi.xmsf-vc105"),
    ;

    companion object {
        /** Tier of an installed runtime; an unknown or absent code means [Normal]. */
        fun forVersionCode(versionCode: Long?): XmsfRuntimeFlavor =
            entries.firstOrNull { it.versionCode == versionCode } ?: Normal
    }
}
