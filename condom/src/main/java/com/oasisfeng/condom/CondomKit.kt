package com.oasisfeng.condom

import android.content.Context

/**
 * Kit interface to extend the functionality of Condom.
 *
 * Created by Oasis on 2017/7/21.
 */
interface CondomKit {

    interface SystemServiceSupplier {
        /** @return the system service instance (may be cached by caller if appropriate).  */
        fun getSystemService(context: Context, name: String): Any?
    }

    interface CondomKitRegistry {
        fun addPermissionSpoof(permission: String)
        fun registerSystemService(name: String, supplier: SystemServiceSupplier)
    }

    /**
     * Register desired functionality with the methods in [CondomKitRegistry].
     * The registry instance must never be used outside this method.
     */
    fun onRegister(registry: CondomKitRegistry)
}
