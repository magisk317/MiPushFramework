package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.android.Region
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MIPushAccountUtilsRuntimeTest {
    @Test
    fun `resolveAccountUrl prefers onebox`() {
        assertEquals(
            "http://onebox.test:9085/pass/v2/register",
            MIPushAccountUtilsRuntime.resolveAccountUrl(
                region = Region.China.name,
                oneBoxBuild = true,
                oneBoxHost = "onebox.test",
                sandBoxBuild = false,
            ),
        )
    }

    @Test
    fun `resolveAccountUrl matches known regions`() {
        assertEquals(
            "https://cn.register.xmpush.xiaomi.com/pass/v2/register",
            MIPushAccountUtilsRuntime.resolveAccountUrl(Region.China.name, false, "unused", false),
        )
        assertEquals(
            "https://register.xmpush.global.xiaomi.com/pass/v2/register",
            MIPushAccountUtilsRuntime.resolveAccountUrl(Region.Global.name, false, "unused", false),
        )
        assertEquals(
            "https://fr.register.xmpush.global.xiaomi.com/pass/v2/register",
            MIPushAccountUtilsRuntime.resolveAccountUrl(Region.Europe.name, false, "unused", false),
        )
        assertEquals(
            "https://ru.register.xmpush.global.xiaomi.com/pass/v2/register",
            MIPushAccountUtilsRuntime.resolveAccountUrl(Region.Russia.name, false, "unused", false),
        )
        assertEquals(
            "https://idmb.register.xmpush.global.xiaomi.com/pass/v2/register",
            MIPushAccountUtilsRuntime.resolveAccountUrl(Region.India.name, false, "unused", false),
        )
    }

    @Test
    fun `resolveAccountUrl falls back to sandbox or prod host`() {
        assertEquals(
            "https://sandbox.xmpush.xiaomi.com/pass/v2/register",
            MIPushAccountUtilsRuntime.resolveAccountUrl(null, false, "unused", true),
        )
        assertEquals(
            "https://register.xmpush.xiaomi.com/pass/v2/register",
            MIPushAccountUtilsRuntime.resolveAccountUrl("unknown", false, "unused", false),
        )
    }
}
