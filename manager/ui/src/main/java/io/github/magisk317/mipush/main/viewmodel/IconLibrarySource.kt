package io.github.magisk317.mipush.main.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.remote.RemoteWriteSupport
import android.os.SystemClock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Base64

private const val TAG = "IconLibrarySource"

/** Wire shape of one ANIP icon library entry (keep in sync with runtime LibraryEntry). */
@Serializable
data class IconLibraryEntry(
    val packageName: String,
    val label: String = "",
    val color: Int? = null,
    val overlay: Boolean = false,
    val updatedAt: Long? = null,
    /** Icon library category: "app" | "game" | "system". */
    val category: String = "app",
)

data class IconLibraryPage(
    val entries: List<IconLibraryEntry>,
    val nextOffset: Int,
    /** Human-readable trace of what the runtime answered; surfaced in the UI when the page is empty. */
    val diagnostic: String? = null,
)

interface IconLibrarySource {
    /** Never returns null: failures are reported through [IconLibraryPage.diagnostic]. */
    suspend fun loadLibraryPage(offset: Int): IconLibraryPage
    suspend fun loadIconBitmap(packageName: String): ImageBitmap?
}

/**
 * Reads the ANIP icon library from the runtime process through the write-commands channel:
 * metadata pages carry JSON in details, single icons come back as base64 PNG scaled down
 * for preview use.
 */
class RemoteIconLibrarySource(
    private val client: ManagerRuntimeClient,
) : IconLibrarySource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun loadLibraryPage(offset: Int): IconLibraryPage {
        val startedAt = SystemClock.elapsedRealtime()
        val result = runCatching {
            RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_GET_ICON_LIBRARY_PAGE,
                intArgument = offset,
                userId = 0,
            )
        }.getOrElse { error ->
            Logger.w(tag = TAG) {
                "icon library call threw offset=$offset error=${error.javaClass.simpleName}: ${error.message?.take(120)}"
            }
            return IconLibraryPage(
                emptyList(),
                offset,
                "call threw ${error.javaClass.simpleName}: ${error.message?.take(120)}",
            )
        }
        val tookMs = SystemClock.elapsedRealtime() - startedAt
        if (result == null) {
            Logger.w(tag = TAG) { "icon library page unavailable offset=$offset (runtime target missing)" }
            return IconLibraryPage(
                emptyList(),
                offset,
                "no result from runtime (target missing) tookMs=$tookMs",
            )
        }
        if (!RemoteWriteSupport.isSuccess(result)) {
            Logger.w(tag = TAG) {
                "icon library page rejected offset=$offset status=${result.status} details=${result.details.take(140)}"
            }
            return IconLibraryPage(
                emptyList(),
                offset,
                "op rejected status=${result.status} tookMs=$tookMs details=${result.details.take(160)}",
            )
        }
        val payload = result.details.substringAfter('|', "")
        if (payload.isEmpty()) {
            Logger.w(tag = TAG) { "icon library page empty payload offset=$offset" }
            return IconLibraryPage(
                emptyList(),
                offset,
                "empty payload tookMs=$tookMs raw details=${result.details.take(160)}",
            )
        }
        val decoded = runCatching {
            json.decodeFromString(ListSerializer(IconLibraryEntry.serializer()), payload)
        }
        val entries = decoded.getOrElse { error ->
            Logger.w(tag = TAG) {
                "icon library page decode failed offset=$offset payloadLen=${payload.length} error=${error.message?.take(140)}"
            }
            return IconLibraryPage(
                emptyList(),
                offset,
                "decode failed tookMs=$tookMs payloadLen=${payload.length} " +
                    "error=${error.message?.take(120)} head=${payload.take(120)}",
            )
        }
        Logger.i(tag = TAG) {
            "icon library page ok offset=$offset entries=${entries.size} payloadLen=${payload.length} tookMs=$tookMs"
        }
        val diagnostic =
            "ok entries=${entries.size} payloadLen=${payload.length} tookMs=$tookMs head=${payload.take(60)}"
        return IconLibraryPage(entries, nextOffset = offset + entries.size, diagnostic = diagnostic)
    }

    override suspend fun loadIconBitmap(packageName: String): ImageBitmap? {
        val result = runCatching {
            RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_LOAD_ICON_BITMAP,
                packageName = packageName,
                userId = 0,
            )
        }.getOrNull()
        if (result == null || !RemoteWriteSupport.isSuccess(result)) {
            Logger.w(tag = TAG) {
                "icon bitmap unavailable pkg=$packageName status=${result?.status} details=${result?.details?.take(80)}"
            }
            return null
        }
        val payload = result.details.substringAfter('|', "")
        if (payload.isEmpty()) return null
        return runCatching {
            val bytes = Base64.getDecoder().decode(payload)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bitmap ->
                val scaled = if (bitmap.width > PREVIEW_SIZE_PX) {
                    Bitmap.createScaledBitmap(bitmap, PREVIEW_SIZE_PX, PREVIEW_SIZE_PX, true)
                } else {
                    bitmap
                }
                scaled.asImageBitmap()
            }
        }.getOrNull()
    }

    private companion object {
        const val PREVIEW_SIZE_PX = 96
    }
}
