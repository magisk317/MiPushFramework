package io.github.magisk317.mipush.common.logging

/**
 * Marks a class whose log tag must survive minification.
 *
 * `logI`/`logW`/`logE` build the tag from `javaClass.simpleName`, so a minified build turns it
 * into an opaque short name that only resolves against one flavor's mapping.txt — the same short
 * name can point at different classes in `normal` and `vc105`. Annotating the classes that own
 * lines in the shipped runtime log keeps those tags greppable while everything else stays
 * obfuscated. The matching `-keepnames` rule lives in `xmsf/proguard-rules.pro` and
 * `xmsf/shell/proguard-rules.pro`; `scripts/checks/verify_release_artifact.sh` fails the build
 * if a marked class ends up renamed.
 */
annotation class StableLogTag
