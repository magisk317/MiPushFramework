package io.github.magisk317.mipush.main.viewmodel

import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.remote.RemoteWriteSupport
import kotlinx.coroutines.delay

data class IconResourcesUpdateResult(
    val isSuccess: Boolean,
    val detail: String,
    val isUpToDate: Boolean = false,
)

fun interface IconResourcesUpdateRequester {
    suspend fun requestIconResourcesUpdate(): IconResourcesUpdateResult
}

/**
 * Triggers [ManagerProtocol.WRITE_OP_FETCH_ICON_RESOURCES] on the runtime process, which downloads
 * the latest ANIP release bundle (~1.4 MB) and may exceed the manager call timeout. Retries with a
 * stable requestId: the runtime idempotency store single-flights duplicates and returns the
 * completed outcome once the in-flight fetch finishes.
 */
class RemoteIconResourcesUpdateRequester(
    private val client: ManagerRuntimeClient,
) : IconResourcesUpdateRequester {

    override suspend fun requestIconResourcesUpdate(): IconResourcesUpdateResult {
        val requestId = RemoteWriteSupport.resolveRequestId()
        repeat(ICON_FETCH_ATTEMPTS) {
            val result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_FETCH_ICON_RESOURCES,
                requestId = requestId,
            )
            if (result != null) {
                val details = result.details
                return when {
                    RemoteWriteSupport.isSuccess(result) &&
                        details == ManagerProtocol.WRITE_DETAIL_ICON_FETCH_UP_TO_DATE ->
                        IconResourcesUpdateResult(
                            isSuccess = true,
                            detail = details,
                            isUpToDate = true,
                        )
                    RemoteWriteSupport.isSuccess(result) ->
                        IconResourcesUpdateResult(isSuccess = true, detail = details)
                    else -> IconResourcesUpdateResult(isSuccess = false, detail = details)
                }
            }
            // Null result means the binder call timed out or the runtime was momentarily
            // unavailable; the fetch keeps running on the runtime side, so poll again.
            delay(ICON_FETCH_RETRY_INTERVAL_MS)
        }
        return IconResourcesUpdateResult(isSuccess = false, detail = "runtime_unavailable")
    }

    private companion object {
        const val ICON_FETCH_ATTEMPTS = 12
        const val ICON_FETCH_RETRY_INTERVAL_MS = 3_000L
    }
}
