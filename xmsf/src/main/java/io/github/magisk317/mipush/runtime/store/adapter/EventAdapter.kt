package io.github.magisk317.mipush.runtime.store.adapter

import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventRow

/**
 * Android-specific extension properties and functions for [RuntimeEventRow].
 *
 * These were previously inlined on the production [Event] Room entity. After the
 * KMP split, the pure data fields live in [RuntimeEventRow] while all
 * Android-dependent logic (Thrift deserialization) is collected here.
 */

/**
 * Lazily deserialises [RuntimeEventRow.payload] into the Thrift container.
 *
 * Equivalent to the old `Event.container` property. Returns `null` when the
 * payload is absent or cannot be decoded.
 */
fun RuntimeEventRow.container(): XmPushActionContainer? =
    payload?.let { XMPushUtils.packToContainer(it) }
