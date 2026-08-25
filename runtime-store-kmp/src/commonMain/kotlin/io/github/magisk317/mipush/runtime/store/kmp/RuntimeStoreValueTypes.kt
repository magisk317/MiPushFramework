package io.github.magisk317.mipush.runtime.store.kmp

/** Stable persisted event type identifiers shared by storage and Android adapters. */
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
    const val RegistrationResult = 21
}

/** Persisted event result identifiers shared by storage and Android adapters. */
object EventRowResultType {
    const val OK = 0
    const val DENY_DISABLED = 1
    const val DENY_USER = 2
}

/** Persisted registration permission identifiers. */
object RegisteredAppType {
    const val ASK = 0
    const val ALLOW = 2
    const val DENY = 3
    const val ALLOW_ONCE = -1
}

/** Persisted registration state identifiers. */
object RegisteredAppRegisteredType {
    const val NotRegistered = 0
    const val Registered = 1
    const val Unregistered = 2
}

/** Per-application island settings read from the runtime store. */
data class RuntimeIslandSettings(
    val enabled: Boolean,
    val focusNotification: Boolean,
)
