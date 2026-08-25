package io.github.magisk317.mipush.runtime.store.adapter

import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventRow

/**
 * Android-specific extension properties and functions for [RuntimeEventRow].
 *
 * These were previously inlined on the production [Event] Room entity. After the
 * KMP split, the pure data fields live in [RuntimeEventRow] while all
 * Android-dependent logic (Thrift deserialization, IntDef annotations) is
 * collected here.
 */

// ── Thrift container ────────────────────────────────────────────────────────

/**
 * Lazily deserialises [RuntimeEventRow.payload] into the Thrift container.
 *
 * Equivalent to the old `Event.container` property. Returns `null` when the
 * payload is absent or cannot be decoded.
 */
fun RuntimeEventRow.container(): XmPushActionContainer? =
    payload?.let { XMPushUtils.packToContainer(it) }

// ── Type constants ──────────────────────────────────────────────────────────

/**
 * Mirrors the values formerly declared inside `Event.Type.Companion`.
 * Same-to `com.xiaomi.xmpush.thrift.ActionType`.
 */
object EventRowType {
    const val SendMessage = 0
    const val Registration = 2
    const val Subscription = 3
    const val UnSubscription = 4
    const val AckMessage = 6
    const val SetConfig = 7
    const val ReportFeedback = 8
    const val Notification = 9
    const val Command = 10
    const val MultiConnectionBroadcast = 11
    const val MultiConnectionResult = 12
    const val UnRegistration = 20

    /** Custom type not present in upstream ActionType. */
    const val RegistrationResult = 21
}

// ── ResultType constants ────────────────────────────────────────────────────

/**
 * Mirrors the values formerly declared inside `Event.ResultType.Companion`.
 */
object EventRowResultType {
    const val OK = 0
    const val DENY_DISABLED = 1
    const val DENY_USER = 2
}
