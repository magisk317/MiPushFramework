package io.github.magisk317.mipush.time

/**
 * Wall-clock for the common sources.
 *
 * `System.currentTimeMillis()` is a JVM/Android API, so commonMain cannot name
 * it: under KMP separate compilation common code is resolved against metadata
 * KLIBs only and platform declarations are invisible there. Each platform
 * source set supplies the one-line actual.
 */
expect fun platformCurrentTimeMillis(): Long
