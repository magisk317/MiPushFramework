package io.github.magisk317.mipush.manager.remote

import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Binder-backed gateways used when the manager UI runs in the standalone `:mipush` process.
 * Supported reads/writes go through [ManagerRuntimeClient]; unsupported capabilities stay local
 * no-ops so individual screens degrade without blocking the rest of the host.
 */
internal fun emitManager(
    stage: String,
    result: String,
    reason: String,
    statusOk: Boolean = true,
    targetPackage: String? = null,
) {
    val attrs = mutableMapOf(
        "result" to result,
        "duration_ms" to "0",
        "process" to "manager",
        "stage" to stage,
        "reason" to reason,
    )
    if (!targetPackage.isNullOrBlank()) {
        attrs["target_package"] = targetPackage
    }
    MagiskOtel.event(
        name = "app.monitor",
        attributes = attrs,
        statusOk = statusOk,
    )
}


internal object RemoteRuntimeLog {
    // Startup/bind races are expected after force-stop or dual-APK process churn.
    fun unavailable(operation: String, status: Enum<*>) {
        when (status.name) {
            "BINDING",
            "DISCONNECTED",
            "TEMPORARILY_DISCONNECTED",
            -> logD("$operation unavailable status=$status")
            else -> logW("$operation unavailable status=$status")
        }
    }
}

internal class ParcelFileDescriptorAutoClose(
    private val descriptor: android.os.ParcelFileDescriptor,
) : java.io.Closeable {
    private val input = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)

    fun copyTo(output: FileOutputStream) {
        input.copyTo(output)
    }

    fun inputStream(): InputStream = input

    override fun close() {
        input.close()
    }
}

internal class CountingOutputStream(
    private val delegate: OutputStream,
) : OutputStream() {
    var bytesWritten: Long = 0L
        private set

    override fun write(byteValue: Int) {
        delegate.write(byteValue)
        bytesWritten += 1L
    }

    override fun write(buffer: ByteArray) {
        delegate.write(buffer)
        bytesWritten += buffer.size.toLong()
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        delegate.write(buffer, offset, length)
        bytesWritten += length.toLong()
    }

    override fun flush() = delegate.flush()

    override fun close() = delegate.close()
}
