package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.android.Region

object MIPushAccountUtilsRuntime {
    @JvmStatic
    fun resolveAccountUrl(
        region: String?,
        oneBoxBuild: Boolean,
        oneBoxHost: String,
        sandBoxBuild: Boolean,
    ): String {
        if (oneBoxBuild) {
            return "http://$oneBoxHost:9085/pass/v2/register"
        }
        return when (region) {
            Region.China.name -> "https://cn.register.xmpush.xiaomi.com/pass/v2/register"
            Region.Global.name -> "https://register.xmpush.global.xiaomi.com/pass/v2/register"
            Region.Europe.name -> "https://fr.register.xmpush.global.xiaomi.com/pass/v2/register"
            Region.Russia.name -> "https://ru.register.xmpush.global.xiaomi.com/pass/v2/register"
            Region.India.name -> "https://idmb.register.xmpush.global.xiaomi.com/pass/v2/register"
            else -> {
                val host = if (sandBoxBuild) {
                    "sandbox.xmpush.xiaomi.com"
                } else {
                    "register.xmpush.xiaomi.com"
                }
                "https://$host/pass/v2/register"
            }
        }
    }
}
