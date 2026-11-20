package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConnectionStatusIndicatorColorTest {
    @Test
    fun `connection status indicators keep their established colors`() {
        assertEquals(Color(0xFF4CAF50), connectionStatusIndicatorColor("Connected"))
        assertEquals(Color(0xFFFFC107), connectionStatusIndicatorColor("Connecting"))
        assertEquals(Color(0xFFF44336), connectionStatusIndicatorColor("Disconnected"))
        assertEquals(Color.Gray, connectionStatusIndicatorColor("Idle"))
        assertEquals(Color.Gray, connectionStatusIndicatorColor(null))
    }
}
