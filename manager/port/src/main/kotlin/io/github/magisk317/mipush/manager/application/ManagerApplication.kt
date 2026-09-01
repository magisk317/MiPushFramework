package io.github.magisk317.mipush.manager.application

import kotlinx.serialization.Serializable

@Serializable
data class ManagerApplication(
    val id: Long? = null,
    val userId: Int = 0,
    val packageName: String = "",
    val type: Int = Type.ASK,
    val notificationOnRegister: Boolean = false,
    val blocked: Boolean = false,
    val islandEnabled: Boolean = true,
    val islandFocusNotification: Boolean = false,
    val registeredType: Int = RegisteredType.NOT_REGISTERED,
    val existServices: Boolean = false,
    val appName: String = "",
    val appNamePinYin: String = "",
    val lastReceiveTimeMs: Long = 0L,
) {
    object Type {
        const val ASK = 0
        const val ALLOW = 2
        const val DENY = 3
        const val ALLOW_ONCE = -1
    }

    object RegisteredType {
        const val NOT_REGISTERED = 0
        const val REGISTERED = 1
        const val UNREGISTERED = 2
    }
}
