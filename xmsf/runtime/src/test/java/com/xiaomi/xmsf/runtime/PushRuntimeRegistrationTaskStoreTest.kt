package io.github.magisk317.mipush.runtime.android

import android.content.Intent
import io.github.magisk317.mipush.runtime.core.PushRegistrationState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PushRuntimeRegistrationTaskStoreTest {

    @Test
    fun `cache keeps latest task per package and updates runtime state`() {
        AndroidPushRuntime.clearStateForTests()
        PushRuntimeRegistrationTaskStore.clearForTests()

        PushRuntimeRegistrationTaskStore.cache(
            packageName = "com.example.app",
            intent = Intent("first"),
            source = "test",
            reason = "first",
            androidUserId = 0,
        )
        PushRuntimeRegistrationTaskStore.cache(
            packageName = "com.example.app",
            intent = Intent("second"),
            source = "test",
            reason = "second",
            androidUserId = 0,
        )

        val pending = PushRuntimeRegistrationTaskStore.pendingTasks(androidUserId = 0)
        val snapshot = AndroidPushRuntime.snapshot()

        assertEquals(1, pending.size)
        assertEquals("com.example.app", pending.single().packageName)
        assertEquals("second", pending.single().reason)
        assertEquals(PushRegistrationState.Registering, snapshot.lastRegistrationState)
        assertEquals("com.example.app", snapshot.lastRegistrationPackage)
    }

    @Test
    fun `failed dispatch is requeued and successful dispatch clears queue`() {
        AndroidPushRuntime.clearStateForTests()
        PushRuntimeRegistrationTaskStore.clearForTests()
        val dispatched = mutableListOf<String>()

        PushRuntimeRegistrationTaskStore.cache(
            packageName = "com.example.one",
            intent = Intent("one"),
            source = "test",
            reason = "one",
            androidUserId = 0,
        )
        PushRuntimeRegistrationTaskStore.cache(
            packageName = "com.example.two",
            intent = Intent("two"),
            source = "test",
            reason = "two",
            androidUserId = 0,
        )

        val firstPass = PushRuntimeRegistrationTaskStore.dispatchAll("dispatch", androidUserId = 0, dispatcher = { packageName, _ ->
            dispatched += packageName
            packageName == "com.example.one"
        })

        assertEquals(1, firstPass)
        assertEquals(1, PushRuntimeRegistrationTaskStore.pendingCount(androidUserId = 0))
        assertEquals("com.example.two", PushRuntimeRegistrationTaskStore.pendingTasks(androidUserId = 0).single().packageName)

        val secondPass = PushRuntimeRegistrationTaskStore.dispatchAll("retry", androidUserId = 0, dispatcher = { _, _ -> true })

        assertEquals(1, secondPass)
        assertEquals(0, PushRuntimeRegistrationTaskStore.pendingCount(androidUserId = 0))
        assertEquals(PushRegistrationState.Registering, AndroidPushRuntime.getRegistrationRecord("com.example.one", androidUserId = 0)?.state)
        assertTrue(dispatched.contains("com.example.one"))
        assertTrue(dispatched.contains("com.example.two"))
    }

    @Test
    fun `clear rejects invalid user ids instead of falling back to primary`() {
        PushRuntimeRegistrationTaskStore.clearForTests()

        assertThrows<IllegalArgumentException> {
            PushRuntimeRegistrationTaskStore.clear("com.example.app", -1)
        }
    }
}
