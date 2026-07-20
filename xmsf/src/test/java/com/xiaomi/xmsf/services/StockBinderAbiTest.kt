package com.xiaomi.xmsf.services

import android.app.Application
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import com.xiaomi.micloudsdk.sync.IMiCloudPushService
import com.xiaomi.xmsf.push.service.IHttpService
import com.xiaomi.xmsf.push.service.IStatService
import com.xiaomi.xmsf.services.keepalive.strategy.IKeepAliveStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class StockBinderAbiTest {
    @Test
    fun `main process bridge uses stock transaction ordering`() {
        val stub = object : IMainProcBridge.Stub() {
            override fun getOnlineBooleanConfig(key: Int, defaultValue: Boolean) = !defaultValue
            override fun getOnlineIntConfig(key: Int, defaultValue: Int) = key + defaultValue
            override fun getOnlineStringConfig(key: Int, defaultValue: String?) = "$key:$defaultValue"
        }

        assertSame(stub, stub.queryLocalInterface(IMainProcBridge.Stub.DESCRIPTOR))
        assertSame(stub, IMainProcBridge.Stub.asInterface(stub))
        assertEquals(12, transact(stub, 1, { writeInt(5); writeInt(7) }) { readInt() })
        assertEquals("5:fallback", transact(stub, 2, { writeInt(5); writeString("fallback") }) { readString() })
        assertEquals(0, transact(stub, 3, { writeInt(5); writeInt(1) }) { readInt() })

        val remote = ForwardingRemoteBinder(stub)
        val proxy = requireNotNull(IMainProcBridge.Stub.asInterface(remote))
        assertEquals(12, proxy.getOnlineIntConfig(5, 7))
        assertEquals(1, remote.lastCode)
        assertEquals("5:fallback", proxy.getOnlineStringConfig(5, "fallback"))
        assertEquals(2, remote.lastCode)
        assertEquals(false, proxy.getOnlineBooleanConfig(5, true))
        assertEquals(3, remote.lastCode)
    }

    @Test
    fun `sub process bridge attaches descriptor and remains synchronous`() {
        var calls = 0
        val stub = object : ISubProcBridge.Stub() {
            override fun notifyOnlineConfigChanged() {
                calls++
            }
        }

        assertSame(stub, stub.queryLocalInterface(ISubProcBridge.Stub.DESCRIPTOR))
        assertSame(stub, ISubProcBridge.Stub.asInterface(stub))
        transact<Unit>(stub, 1, {}, { })
        assertEquals(1, calls)

        val remote = ForwardingRemoteBinder(stub)
        ISubProcBridge.Stub.asInterface(remote)!!.notifyOnlineConfigChanged()
        assertEquals(1, remote.lastCode)
        assertEquals(2, calls)
    }

    @Test
    fun `keep alive strategy is one way and locally discoverable`() {
        var received: String? = null
        val stub = object : IKeepAliveStrategy.Stub() {
            override fun updateKeepAliveStrategy(configJson: String?) {
                received = configJson
            }
        }
        assertSame(stub, stub.queryLocalInterface(IKeepAliveStrategy.Stub.DESCRIPTOR))
        assertSame(stub, IKeepAliveStrategy.Stub.asInterface(stub))

        val remote = RecordingBinder()
        IKeepAliveStrategy.Stub.asInterface(remote)!!.updateKeepAliveStrategy("{}")
        assertEquals(1, remote.lastCode)
        assertEquals(IBinder.FLAG_ONEWAY, remote.lastFlags)

        oneWay(stub, 1) { writeString("strategy") }
        assertEquals("strategy", received)
    }

    @Test
    fun `http service exposes both stock transactions`() {
        val stub = object : IHttpService.Stub() {
            override fun doHttpPost(str: String?, map: Map<*, *>?) = "cn:$str:${map?.get("k")}"
            override fun doHttpPostIntl(str: String?, map: Map<*, *>?) = "intl:$str:${map?.get("k")}"
        }
        assertSame(stub, stub.queryLocalInterface(IHttpService.Stub.DESCRIPTOR))
        assertSame(stub, IHttpService.Stub.asInterface(stub))
        assertEquals("cn:url:v", transactMapCall(stub, 1))
        assertEquals("intl:url:v", transactMapCall(stub, 2))

        val remote = ForwardingRemoteBinder(stub)
        val proxy = requireNotNull(IHttpService.Stub.asInterface(remote))
        assertEquals("cn:url:v", proxy.doHttpPost("url", mapOf("k" to "v")))
        assertEquals(1, remote.lastCode)
        assertEquals("intl:url:v", proxy.doHttpPostIntl("url", mapOf("k" to "v")))
        assertEquals(2, remote.lastCode)
    }

    @Test
    fun `stat service exposes both one way stock transactions`() {
        var event: String? = null
        var international: Map<*, *>? = null
        val stub = object : IStatService.Stub() {
            override fun insertEvent(str: String?) {
                event = str
            }

            override fun insertEventIntl(map: Map<*, *>?) {
                international = map
            }
        }
        assertSame(stub, stub.queryLocalInterface(IStatService.Stub.DESCRIPTOR))
        assertSame(stub, IStatService.Stub.asInterface(stub))

        val remote = RecordingBinder()
        IStatService.Stub.asInterface(remote)!!.insertEventIntl(mapOf("region" to "intl"))
        assertEquals(2, remote.lastCode)
        assertEquals(IBinder.FLAG_ONEWAY, remote.lastFlags)

        oneWay(stub, 1) { writeString("event") }
        oneWay(stub, 2) { writeMap(mapOf("region" to "intl")) }
        assertEquals("event", event)
        assertEquals("intl", international?.get("region"))
    }

    @Test
    fun `micloud worker binder keeps stock inout intent ABI`() {
        var received: Intent? = null
        val stub = object : IMiCloudPushService.Stub() {
            override fun startWork(intent: Intent?) {
                received = intent
                intent?.putExtra("handled", true)
            }
        }
        assertSame(stub, stub.queryLocalInterface(IMiCloudPushService.Stub.DESCRIPTOR))
        assertSame(stub, IMiCloudPushService.Stub.asInterface(stub))

        val response = transact(stub, 1, {
            writeInt(1)
            Intent("work").writeToParcel(this, 0)
        }) {
            val present = readInt()
            assertEquals(1, present)
            Intent.CREATOR.createFromParcel(this)
        }
        assertEquals("work", received?.action)
        assertTrue(response.getBooleanExtra("handled", false))

        val remote = ForwardingRemoteBinder(stub)
        val proxyIntent = Intent("proxy-work")
        IMiCloudPushService.Stub.asInterface(remote)!!.startWork(proxyIntent)
        assertEquals(1, remote.lastCode)
        assertTrue(proxyIntent.getBooleanExtra("handled", false))
    }

    private fun transactMapCall(stub: Binder, code: Int): String? = transact(
        stub,
        code,
        {
            writeString("url")
            writeMap(mapOf("k" to "v"))
        },
        { readString() },
    )

    private fun oneWay(stub: Binder, code: Int, writeBody: Parcel.() -> Unit) {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(descriptorFor(stub))
            data.writeBody()
            assertTrue(stub.transact(code, data, null, IBinder.FLAG_ONEWAY))
        } finally {
            data.recycle()
        }
    }

    private fun <T> transact(
        stub: Binder,
        code: Int,
        writeBody: Parcel.() -> Unit,
        readBody: Parcel.() -> T,
    ): T {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(descriptorFor(stub))
            data.writeBody()
            assertTrue(stub.transact(code, data, reply, 0))
            reply.readException()
            return reply.readBody()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun descriptorFor(stub: Binder): String = when (stub) {
        is IMainProcBridge -> IMainProcBridge.Stub.DESCRIPTOR
        is ISubProcBridge -> ISubProcBridge.Stub.DESCRIPTOR
        is IKeepAliveStrategy -> IKeepAliveStrategy.Stub.DESCRIPTOR
        is IHttpService -> IHttpService.Stub.DESCRIPTOR
        is IStatService -> IStatService.Stub.DESCRIPTOR
        is IMiCloudPushService -> IMiCloudPushService.Stub.DESCRIPTOR
        else -> error("Unknown binder")
    }

    private class RecordingBinder : Binder() {
        var lastCode: Int = -1
        var lastFlags: Int = -1

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            lastCode = code
            lastFlags = flags
            return true
        }
    }

    private class ForwardingRemoteBinder(
        private val delegate: IBinder,
    ) : Binder() {
        var lastCode: Int = -1

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            lastCode = code
            return delegate.transact(code, data, reply, flags)
        }
    }
}
