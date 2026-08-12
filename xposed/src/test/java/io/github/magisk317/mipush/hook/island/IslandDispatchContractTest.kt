package io.github.magisk317.mipush.hook.island

import android.app.Application
import android.os.Bundle
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

// Keep Robolectric: this test relies on Android framework implementations indirectly;
// android.jar unit-test stubs throw "Method ... not mocked" without the extension.
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class IslandDispatchContractTest {
    @Test
    fun `native param and remote view payloads both block generated fallback`() {
        assertTrue(
            IslandDispatchContract.hasNativeFocusPayload(
                Bundle().apply { putString(IslandDispatchContract.FOCUS_PARAM, "native") },
            ),
        )
        assertTrue(
            IslandDispatchContract.hasNativeFocusPayload(
                Bundle().apply { putString(IslandDispatchContract.FOCUS_REMOTE_VIEW, "native") },
            ),
        )
        assertFalse(IslandDispatchContract.hasNativeFocusPayload(Bundle()))
    }
}
