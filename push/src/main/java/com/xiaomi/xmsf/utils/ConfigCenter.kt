package com.xiaomi.xmsf.utils

/**
 * Compatibility alias — all callers should migrate to [io.github.magisk317.mipush.app.ConfigCenter].
 *
 * This typealias exists so that legacy code under `com.xiaomi.xmsf` can continue to resolve
 * `ConfigCenter` without a source move. New code must import the canonical location directly.
 */
typealias ConfigCenter = io.github.magisk317.mipush.app.ConfigCenter
