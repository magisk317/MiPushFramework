package io.github.magisk317.mipush.service.runtime

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * Hidden-API-free-at-compile-time registration for Android's process observer Binder.
 *
 * The Android 16 framework dump from OS 3.0.315 uses IActivityManager transactions 122/123 and
 * an IProcessObserver that includes `onProcessStarted`. Those numbers and callback ordering are
 * platform-generated and have changed across Android releases, so both sides are resolved from
 * the runtime Stub fields instead of pinning the current device values in app code.
 */
internal class ProcessObserverCompat(
    private val onProcessStarted: (pid: Int, processName: String) -> Unit,
    private val onForegroundActivitiesChanged: (pid: Int, uid: Int, foreground: Boolean) -> Unit,
    private val onProcessDied: (pid: Int, uid: Int) -> Unit,
) {
    private var registration: Registration? = null

    @Synchronized
    fun register(): Boolean {
        if (registration != null) return true
        return runCatching {
            val layout = resolveRuntimeLayout()
            val activityManager = activityManagerBinder()
            val observer = ObserverBinder(
                layout,
                onProcessStarted,
                onForegroundActivitiesChanged,
                onProcessDied,
            )
            transactObserver(activityManager, layout.activityManagerDescriptor, layout.register, observer)
            registration = Registration(activityManager, observer, layout)
            Log.i(TAG, "Registered process observer with runtime transaction layout")
            true
        }.onFailure {
            Log.w(TAG, "Process observer unavailable; keep-alive will use polling", it)
        }.getOrDefault(false)
    }

    @Synchronized
    fun unregister() {
        val current = registration ?: return
        registration = null
        runCatching {
            transactObserver(
                current.activityManager,
                current.layout.activityManagerDescriptor,
                current.layout.unregister,
                current.observer,
            )
        }.onFailure {
            Log.w(TAG, "Failed to unregister process observer", it)
        }
    }

    internal data class TransactionLayout(
        val activityManagerDescriptor: String,
        val register: Int,
        val unregister: Int,
        val processObserverDescriptor: String,
        val processStarted: Int?,
        val foregroundActivitiesChanged: Int,
        val processDied: Int,
    )

    private data class Registration(
        val activityManager: IBinder,
        val observer: IBinder,
        val layout: TransactionLayout,
    )

    private class ObserverBinder(
        private val layout: TransactionLayout,
        private val startedCallback: (Int, String) -> Unit,
        private val foregroundCallback: (Int, Int, Boolean) -> Unit,
        private val diedCallback: (Int, Int) -> Unit,
    ) : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(layout.processObserverDescriptor)
                return true
            }
            if (layout.processStarted == code) {
                data.enforceInterface(layout.processObserverDescriptor)
                val pid = data.readInt()
                data.readInt() // processUid
                data.readInt() // packageUid
                data.readString() // packageName
                startedCallback(pid, data.readString().orEmpty())
                return true
            }
            return when (code) {
                layout.foregroundActivitiesChanged -> {
                    data.enforceInterface(layout.processObserverDescriptor)
                    val pid = data.readInt()
                    val uid = data.readInt()
                    // AIDL booleans are encoded as an int on both old and current Android. Reading
                    // the raw value keeps this callback compatible below Parcel.readBoolean().
                    foregroundCallback(pid, uid, data.readInt() != 0)
                    true
                }

                layout.processDied -> {
                    data.enforceInterface(layout.processObserverDescriptor)
                    diedCallback(data.readInt(), data.readInt())
                    true
                }

                else -> super.onTransact(code, data, reply, flags)
            }
        }
    }

    companion object {
        private const val TAG = "ProcessObserverCompat"

        internal fun resolveLayout(
            activityManagerStub: Class<*>,
            processObserverStub: Class<*>,
        ): TransactionLayout {
            return TransactionLayout(
                activityManagerDescriptor = staticString(activityManagerStub, "DESCRIPTOR"),
                register = staticInt(activityManagerStub, "TRANSACTION_registerProcessObserver"),
                unregister = staticInt(activityManagerStub, "TRANSACTION_unregisterProcessObserver"),
                processObserverDescriptor = staticString(processObserverStub, "DESCRIPTOR"),
                // Stock 7.4.67-C ProcessObserver implements this callback, and Android 16 sends
                // the process name in it. Older framework AIDLs do not expose the transaction,
                // so keep it optional and continue resolving foreground/death callbacks there.
                processStarted = optionalStaticInt(
                    processObserverStub,
                    "TRANSACTION_onProcessStarted",
                ),
                foregroundActivitiesChanged = staticInt(
                    processObserverStub,
                    "TRANSACTION_onForegroundActivitiesChanged",
                ),
                processDied = staticInt(processObserverStub, "TRANSACTION_onProcessDied"),
            )
        }

        private fun resolveRuntimeLayout(): TransactionLayout {
            return resolveLayout(
                Class.forName("android.app.IActivityManager\$Stub"),
                Class.forName("android.app.IProcessObserver\$Stub"),
            )
        }

        private fun activityManagerBinder(): IBinder {
            val serviceManager = Class.forName("android.os.ServiceManager")
            return requireNotNull(
                serviceManager.getMethod("getService", String::class.java)
                    .invoke(null, "activity") as? IBinder,
            ) { "ActivityManager Binder is unavailable" }
        }

        private fun transactObserver(
            activityManager: IBinder,
            descriptor: String,
            transaction: Int,
            observer: IBinder,
        ) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(descriptor)
                data.writeStrongBinder(observer)
                check(activityManager.transact(transaction, data, reply, 0)) {
                    "ActivityManager rejected process observer transaction"
                }
                reply.readException()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }

        private fun staticInt(type: Class<*>, fieldName: String): Int {
            return type.getDeclaredField(fieldName)
                .apply { isAccessible = true }
                .getInt(null)
        }

        private fun staticString(type: Class<*>, fieldName: String): String {
            return type.getDeclaredField(fieldName)
                .apply { isAccessible = true }
                .get(null) as String
        }

        private fun optionalStaticInt(type: Class<*>, fieldName: String): Int? {
            return runCatching { staticInt(type, fieldName) }.getOrNull()
        }
    }
}
