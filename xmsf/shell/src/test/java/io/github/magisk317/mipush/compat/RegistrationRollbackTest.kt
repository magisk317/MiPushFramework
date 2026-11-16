package io.github.magisk317.mipush.compat

import io.github.magisk317.mipush.app.di.withRegistrationRollback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RegistrationRollbackTest {
    @Test
    fun `confirmed registration keeps admission but failed feedback restores it`() = runBlocking {
        for (success in listOf(false, true)) {
            var restored = 0
            val feedback = withRegistrationRollback(isConfirmed = { it }, rollback = { restored++ }) { success }
            assertEquals(success, feedback)
            assertEquals(if (success) 0 else 1, restored)
        }
    }

    @Test
    fun `launch failure restores admission before propagating the same exception`() {
        var restored = 0
        val failure = IllegalStateException("root launch failed")
        val thrown = assertThrows<IllegalStateException> {
            runBlocking {
                withRegistrationRollback<Boolean>(isConfirmed = { it }, rollback = { restored++ }) { throw failure }
            }
        }
        assertSame(failure, thrown)
        assertEquals(1, restored)
    }

    @Test
    fun `cancellation while waiting restores admission and is never swallowed`() {
        var restored = 0
        val cancellation = CancellationException("manager disconnected")
        val thrown = assertThrows<CancellationException> {
            runBlocking {
                withRegistrationRollback<Boolean>(isConfirmed = { it }, rollback = { restored++ }) { throw cancellation }
            }
        }
        assertSame(cancellation, thrown)
        assertEquals(1, restored)
    }
}
