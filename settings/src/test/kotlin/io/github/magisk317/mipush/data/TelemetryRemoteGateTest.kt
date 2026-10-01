package io.github.magisk317.mipush.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TelemetryRemoteGateTest {

    @Test
    fun `explicit disable is honoured`() {
        assertEquals(true, TelemetryRemoteGate.parseDisabled("""{"analytics_enabled": false}"""))
    }

    @Test
    fun `explicit enable restores the local decision`() {
        assertEquals(false, TelemetryRemoteGate.parseDisabled("""{"analytics_enabled": true}"""))
    }

    @Test
    fun `missing key leaves the cached decision untouched`() {
        assertNull(TelemetryRemoteGate.parseDisabled("""{"other": false}"""))
    }

    @Test
    fun `malformed payload is never trusted`() {
        assertNull(TelemetryRemoteGate.parseDisabled("not json"))
        assertNull(TelemetryRemoteGate.parseDisabled(""))
    }

    @Test
    fun `html error page is not mistaken for a decision`() {
        assertNull(TelemetryRemoteGate.parseDisabled("<!doctype html><html></html>"))
    }
}
