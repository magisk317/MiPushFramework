package com.xiaomi.channel.commonutils.msa

interface IdManager {
    fun getAAID(): String?
    fun getOAID(): String?
    fun getUDID(): String?
    fun getVAID(): String?
    fun isAllowOAID(): Boolean
    fun isSupported(): Boolean
}
