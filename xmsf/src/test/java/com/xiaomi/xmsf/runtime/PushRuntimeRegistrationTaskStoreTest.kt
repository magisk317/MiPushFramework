package io.github.magisk317.mipush.runtime.android

import android.content.Intent
import io.github.magisk317.mipush.runtime.core.PushRegistrationState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushRuntimeRegistrationTaskStoreTest {

    @Test
    fun `cache keeps latest task per package and updates runtime state`() {
        AndroidPushRuntime.clearStateForTests()
        PushRuntimeRegistrationTaskStore.clear()

        PushRuntimeRegistrationTaskStore.cache(
            packageName = "com.example.app",
            intent = Intent("first"),
            source = "test",
            reason = "first"
        )
        PushRuntimeRegistrationTaskStore.cache(
            packageName = "com.example.app",
            intent = Intent("second"),
            source = "test",
            reason = "second"
        )

        val pending = PushRuntimeRegistrationTaskStore.pendingTasks()
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
        PushRuntimeRegistrationTaskStore.clear()
        val dispatched = mutableListOf<String>()

        PushRuntimeRegistrationTaskStore.cache(
            packageName = "com.example.one",
            intent = Intent("one"),
            source = "test",
            reason = "one"
        )
        PushRuntimeRegistrationTaskStore.cache(
            packageName = "com.example.two",
            intent = Intent("two"),
            source = "test",
            reason = "two"
        )

        val firstPass = PushRuntimeRegistrationTaskStore.dispatchAll("dispatch") { packageName, _ ->
            dispatched += packageName
            packageName == "com.example.one"
        }

        assertEquals(1, firstPass)
        assertEquals(1, PushRuntimeRegistrationTaskStore.pendingCount())
        assertEquals("com.example.two", PushRuntimeRegistrationTaskStore.pendingTasks().single().packageName)

        val secondPass = PushRuntimeRegistrationTaskStore.dispatchAll("retry") { _, _ -> true }

        assertEquals(1, secondPass)
        assertEquals(0, PushRuntimeRegistrationTaskStore.pendingCount())
        assertEquals(PushRegistrationState.Registering, AndroidPushRuntime.getRegistrationRecord("com.example.one")?.state)
        assertTrue(dispatched.contains("com.example.one"))
        assertTrue(dispatched.contains("com.example.two"))
    }
}
