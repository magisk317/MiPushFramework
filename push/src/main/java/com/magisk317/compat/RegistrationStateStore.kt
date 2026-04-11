package com.magisk317.compat

import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication

object RegistrationStateStore {
    private val logger = object {
        fun i(msg: String) = Napier.i(msg, tag = "RegistrationStateStore")
    }

    enum class Source {
        SERVER_RESULT,
        LOCAL_PROBE
    }

    @JvmStatic
    fun updateIfChanged(
        application: RegisteredApplication,
        @RegisteredApplication.RegisteredType nextType: Int,
        source: Source
    ): Boolean {
        val oldType = application.registeredType
        if (oldType == nextType) {
            return false
        }
        application.registeredType = nextType
        RegisteredApplicationDb.update(application)
        logger.i(
            "registration state changed pkg=${application.packageName}, ${labelOf(oldType)} -> ${labelOf(nextType)}, source=$source"
        )
        return true
    }

    private fun labelOf(@RegisteredApplication.RegisteredType type: Int): String {
        return when (type) {
            RegisteredApplication.RegisteredType.Registered -> "Registered"
            RegisteredApplication.RegisteredType.Unregistered -> "Unregistered"
            else -> "NotRegistered"
        }
    }
}

