package io.github.magisk317.mipush.hook.island

import android.os.Bundle
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IslandDispatchContractTest {
    @Test
    fun `native param and remote view payloads both block generated fallback`() {
        val paramBundle = mockk<Bundle>()
        every { paramBundle.containsKey(IslandDispatchContract.FOCUS_PARAM) } returns true
        every { paramBundle.containsKey(IslandDispatchContract.FOCUS_REMOTE_VIEW) } returns false
        assertTrue(IslandDispatchContract.hasNativeFocusPayload(paramBundle))

        val rvBundle = mockk<Bundle>()
        every { rvBundle.containsKey(IslandDispatchContract.FOCUS_PARAM) } returns false
        every { rvBundle.containsKey(IslandDispatchContract.FOCUS_REMOTE_VIEW) } returns true
        assertTrue(IslandDispatchContract.hasNativeFocusPayload(rvBundle))

        val emptyBundle = mockk<Bundle>()
        every { emptyBundle.containsKey(any()) } returns false
        assertFalse(IslandDispatchContract.hasNativeFocusPayload(emptyBundle))
    }
}
