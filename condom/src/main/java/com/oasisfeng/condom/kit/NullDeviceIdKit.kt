package com.oasisfeng.condom.kit

import android.Manifest
import android.content.Context
import com.oasisfeng.condom.CondomKit

class NullDeviceIdKit : CondomKit, CondomKit.SystemServiceSupplier {
    override fun onRegister(registry: CondomKit.CondomKitRegistry) {
        registry.addPermissionSpoof(Manifest.permission.READ_PHONE_STATE)
        registry.registerSystemService(Context.TELEPHONY_SERVICE, this)
    }

    override fun getSystemService(context: Context, name: String): Any? = null
}
