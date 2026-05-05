package com.xiaomi.channel.commonutils.msa

class OtherIdManager : IdManager {
    override fun getAAID(): String? = null

    override fun getOAID(): String? = null

    override fun getUDID(): String? = null

    override fun getVAID(): String? = null

    override fun isAllowOAID(): Boolean = false

    override fun isSupported(): Boolean = false
}
