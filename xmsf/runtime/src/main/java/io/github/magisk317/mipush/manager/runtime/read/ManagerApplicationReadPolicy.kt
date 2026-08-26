package io.github.magisk317.mipush.manager.runtime.read

import io.github.magisk317.mipush.common.manager.ManagerApplication

object ManagerApplicationReadPolicy {
    val comparator = Comparator<ManagerApplication> { first, second ->
        val priority = displayPriority(first).compareTo(displayPriority(second))
        if (priority != 0) return@Comparator priority
        val receiveTime = second.lastReceiveTimeMs.compareTo(first.lastReceiveTimeMs)
        if (receiveTime != 0) return@Comparator receiveTime
        val pinyin = first.appNamePinYin.compareTo(second.appNamePinYin)
        if (pinyin != 0) return@Comparator pinyin
        first.packageName.compareTo(second.packageName)
    }

    fun matchesQuery(application: ManagerApplication, query: String): Boolean {
        if (query.isBlank()) return true
        val normalized = query.lowercase()
        return application.packageName.lowercase().contains(normalized) ||
            application.appName.lowercase().contains(normalized) ||
            application.appNamePinYin.lowercase().contains(normalized)
    }

    fun matchesFilter(application: ManagerApplication, filterMode: Int): Boolean = when (filterMode) {
        ManagerApplicationReadQuery.FILTER_REGISTERED ->
            application.registeredType == ManagerApplication.RegisteredType.REGISTERED
        ManagerApplicationReadQuery.FILTER_NOT_REGISTERED ->
            application.registeredType == ManagerApplication.RegisteredType.NOT_REGISTERED &&
                application.lastReceiveTimeMs == 0L
        ManagerApplicationReadQuery.FILTER_UNREGISTERED ->
            application.registeredType == ManagerApplication.RegisteredType.UNREGISTERED &&
                application.lastReceiveTimeMs == 0L
        else -> true
    }

    fun inferReason(
        registeredType: Int,
        latestEvent: RegistrationEventSnapshot?,
        hasLocalRegistration: Boolean,
        hasRegSec: Boolean,
    ): String = when {
        registeredType == ManagerApplication.RegisteredType.REGISTERED -> "registered"
        latestEvent == null && !hasLocalRegistration && !hasRegSec -> "never_attempted"
        latestEvent?.type == REGISTRATION_EVENT_UNREGISTERED -> "unregistered_after_attempt"
        latestEvent?.type == REGISTRATION_EVENT_RESULT && latestEvent.result != RESULT_OK ->
            "registration_result_failed"
        latestEvent?.type == REGISTRATION_EVENT_REGISTERING -> "registering_or_waiting_result"
        hasLocalRegistration && registeredType != ManagerApplication.RegisteredType.REGISTERED -> "local_state_stale"
        hasRegSec && !hasLocalRegistration -> "has_secret_but_no_local_reg"
        else -> "unknown"
    }

    private fun displayPriority(application: ManagerApplication): Int = when {
        application.registeredType == ManagerApplication.RegisteredType.REGISTERED -> 0
        application.lastReceiveTimeMs > 0L -> 1
        application.registeredType == ManagerApplication.RegisteredType.UNREGISTERED -> 2
        else -> 3
    }

    private const val REGISTRATION_EVENT_REGISTERING = 2
    private const val REGISTRATION_EVENT_UNREGISTERED = 20
    private const val REGISTRATION_EVENT_RESULT = 21
    private const val RESULT_OK = 0
}
