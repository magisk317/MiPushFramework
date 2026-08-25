package io.github.magisk317.mipush.service.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProcessObserverCompatTest {
    @Test
    fun `transaction layout is resolved from runtime stub fields`() {
        val layout = ProcessObserverCompat.resolveLayout(
            FakeActivityManagerStub::class.java,
            FakeProcessObserverStub::class.java,
        )

        assertEquals("fake.activity", layout.activityManagerDescriptor)
        assertEquals(701, layout.register)
        assertEquals(709, layout.unregister)
        assertEquals("fake.observer", layout.processObserverDescriptor)
        assertEquals(29, layout.processStarted)
        assertEquals(31, layout.foregroundActivitiesChanged)
        assertEquals(47, layout.processDied)
    }

    private class FakeActivityManagerStub {
        companion object {
            @JvmField
            val DESCRIPTOR = "fake.activity"

            @JvmField
            val TRANSACTION_registerProcessObserver = 701

            @JvmField
            val TRANSACTION_unregisterProcessObserver = 709
        }
    }

    private class FakeProcessObserverStub {
        companion object {
            @JvmField
            val DESCRIPTOR = "fake.observer"

            @JvmField
            val TRANSACTION_onProcessStarted = 29

            @JvmField
            val TRANSACTION_onForegroundActivitiesChanged = 31

            @JvmField
            val TRANSACTION_onProcessDied = 47
        }
    }
}
