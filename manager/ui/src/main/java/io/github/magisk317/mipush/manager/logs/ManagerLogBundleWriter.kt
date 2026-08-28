package io.github.magisk317.mipush.manager.logs

import android.content.Context
import java.io.OutputStream

/**
 * UI-host local-save boundary for a generated diagnostic archive.
 *
 * The destination is opened by the Manager UI after the user chooses it through SAF. Remote
 * implementations can stream Binder data straight into that destination instead of materializing
 * a second cache archive merely to share it.
 */
interface ManagerLogBundleWriter {
    suspend fun writeLogBundle(
        context: Context,
        destination: OutputStream,
    ): ManagerLogBundleWriteResult
}

data class ManagerLogBundleWriteResult(
    val success: Boolean,
    val details: String,
)
