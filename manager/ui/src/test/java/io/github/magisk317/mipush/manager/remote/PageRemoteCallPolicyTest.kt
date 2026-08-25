package io.github.magisk317.mipush.manager.remote

import io.github.magisk317.mipush.manager.cache.RefreshGeneration
import io.github.magisk317.mipush.manager.cache.SnapshotKey
import io.github.magisk317.mipush.manager.client.RemotePriority
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PageRemoteCallPolicyTest {
    @Test
    fun `budgets map page work to the required scheduler priority`() {
        assertEquals(RemotePriority.TRANSITION_CRITICAL, PageRemoteCallPolicy.firstScreen.priority)
        assertEquals(RemotePriority.VISIBLE_PAGE, PageRemoteCallPolicy.visiblePage.priority)
        assertEquals(RemotePriority.USER_ACTION, PageRemoteCallPolicy.budget(userInitiated = true, backgroundRefresh = false).priority)
        assertEquals(RemotePriority.BACKGROUND_REFRESH, PageRemoteCallPolicy.budget(userInitiated = false, backgroundRefresh = true).priority)
    }

    @Test
    fun `snapshot key keeps page query filter and user scopes distinct`() {
        val base = SnapshotKey(page = "events", query = "q", filter = "f", userId = 0)
        assertEquals(base, base.copy())
        assertEquals(false, base == base.copy(query = "other"))
        assertEquals(false, base == base.copy(filter = "other"))
        assertEquals(false, base == base.copy(userId = 10))
        assertEquals(false, base == base.copy(page = "apps"))
    }

    @Test
    fun `refresh generation increments without changing content scope`() {
        val initial = RefreshGeneration(0)
        val first = initial.next()
        val second = first.next()
        assertEquals(RefreshGeneration(0), initial)
        assertEquals(RefreshGeneration(1), first)
        assertEquals(RefreshGeneration(2), second)
    }
}
