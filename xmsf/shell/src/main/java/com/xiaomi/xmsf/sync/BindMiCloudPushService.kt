package com.xiaomi.xmsf.sync

import android.app.IntentService
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import co.touchlab.kermit.Logger
import com.xiaomi.micloudsdk.sync.IMiCloudPushService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class BindMiCloudPushService : IntentService("BindMiCloudPushService") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) {
            Logger.withTag(TAG).w { "Ignoring restarted service without a request" }
            return
        }
        val bindIntent = intent.getParcelableExtra<Intent>(EXTRA_BIND_INTENT)
        if (bindIntent == null) {
            Logger.withTag(TAG).w { "Ignoring request without key_to_bind_intent" }
            return
        }

        bindIntent.component = resolveTargetComponent(packageManager)
        val connectedBinder = AtomicReference<IBinder?>()
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (connectedBinder.compareAndSet(null, service)) {
                    connected.countDown()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }

        var bound = false
        try {
            bound = bindService(bindIntent, connection, BIND_AUTO_CREATE)
            if (!bound) {
                Logger.withTag(TAG).w { "MiCloud push target refused binding" }
                return
            }
            if (!connected.await(CONNECTION_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                Logger.withTag(TAG).w { "Timed out waiting for MiCloud push target" }
                return
            }
            val remote = IMiCloudPushService.Stub.asInterface(connectedBinder.get())
            if (remote == null) {
                Logger.withTag(TAG).w { "MiCloud push target returned no binder" }
                return
            }
            remote.startWork(bindIntent)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            Logger.withTag(TAG).w(interrupted) { "Interrupted while waiting for MiCloud push target" }
        } catch (failure: RemoteException) {
            Logger.withTag(TAG).w(failure) { "MiCloud push work failed" }
        } catch (failure: SecurityException) {
            Logger.withTag(TAG).w(failure) { "MiCloud push target rejected binding" }
        } finally {
            if (bound) {
                runCatching { unbindService(connection) }
            }
        }
    }

    companion object {
        const val EXTRA_BIND_INTENT = "key_to_bind_intent"
        private const val TAG = "BindMiCloudPushService"
        private const val CONNECTION_TIMEOUT_MINUTES = 3L
        internal val PRIMARY_COMPONENT = ComponentName(
            "com.miui.cloudservice",
            "com.miui.cloudservice.push.MiCloudPushService",
        )
        internal val FALLBACK_COMPONENT = ComponentName(
            "com.miui.micloudsync",
            "com.miui.micloudsync.push.MicloudPushService",
        )

        internal fun resolveTargetComponent(packageManager: PackageManager): ComponentName {
            val primary = Intent().setComponent(PRIMARY_COMPONENT)
            return if (packageManager.queryIntentServices(primary, 0).isNullOrEmpty()) {
                FALLBACK_COMPONENT
            } else {
                PRIMARY_COMPONENT
            }
        }
    }
}
