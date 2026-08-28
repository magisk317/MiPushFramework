package io.github.magisk317.mipush.manager.client

import android.os.ParcelFileDescriptor
import io.github.magisk317.mipush.manager.api.ManagerLogExportResultDto
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class ManagerWireResourceCloserTest {
    @Test
    fun `discard closes only the PFD transferred by a log export result`() {
        val descriptor = mockk<ParcelFileDescriptor>(relaxed = true)

        ManagerWireResourceCloser.discardOwned(
            ManagerLogExportResultDto(success = true, parcelFileDescriptor = descriptor),
        )

        verify(exactly = 1) { descriptor.close() }
    }

    @Test
    fun `discard ignores values that do not transfer a manager-owned wire resource`() {
        val descriptor = mockk<ParcelFileDescriptor>(relaxed = true)

        ManagerWireResourceCloser.discardOwned("not-a-wire-result")
        ManagerWireResourceCloser.discardOwned(null)

        verify(exactly = 0) { descriptor.close() }
    }
}
