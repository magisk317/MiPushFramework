package io.github.magisk317.mipush.manager.client

import io.github.magisk317.mipush.manager.api.ManagerLogExportResultDto

/** Closes wire resources transferred to the manager client after a discarded log-export result. */
internal object ManagerWireResourceCloser {
    fun discardOwned(value: Any?) {
        when (value) {
            is ManagerLogExportResultDto -> runCatching { value.parcelFileDescriptor?.close() }
        }
    }
}
