package io.github.magisk317.mipush.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.android.AndroidPushRuntime
import io.github.magisk317.mipush.runtime.android.PushRuntimeRegistrationTaskStore
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MiPushRuntimeRegistrationDispatchTest {
    private val context = mockk<Context>()
    private val adapter = MiPushRuntimeRegistrationExecutionAdapter(context, mockk(relaxed = true))

    @BeforeEach
    fun setUp() {
        mockkStatic(Utils::class, RegisteredApplicationDb::class)
        every { Utils.myUserId() } returns 0
        every { Utils.requireValidUserId(0) } returns 0
        every { RegisteredApplicationDb.getRegisteredApplication("test.app") } returns null
        AndroidPushRuntime.clearStateForTests()
        PushRuntimeRegistrationTaskStore.clearForTests()
        PushRuntimeRegistrationTaskStore.cache("test.app", Intent("register"), "test", androidUserId = 0)
    }

    @AfterEach
    fun tearDown() {
        PushRuntimeRegistrationTaskStore.clearForTests()
        AndroidPushRuntime.clearStateForTests()
        unmockkStatic(Utils::class, RegisteredApplicationDb::class)
    }

    @Test
    fun `missing service component keeps the queued request for retry`() {
        every { context.startService(any()) } returns null

        adapter.dispatchRegistrationTasks("missing-component", null)

        assertEquals(1, PushRuntimeRegistrationTaskStore.pendingCount(androidUserId = 0))
        every { context.startService(any()) } returns mockk<ComponentName>()
        adapter.dispatchRegistrationTasks("retry", null)
        assertEquals(0, PushRuntimeRegistrationTaskStore.pendingCount(androidUserId = 0))
    }

    @Test
    fun `service launch exception keeps the queued request for retry`() {
        every { context.startService(any()) } throws IllegalStateException("service unavailable")

        adapter.dispatchRegistrationTasks("exception", null)

        assertEquals(1, PushRuntimeRegistrationTaskStore.pendingCount(androidUserId = 0))
    }
}
