package io.github.magisk317.mipush.hook.amap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AmapDriveLiveViewStateTest {
    @Test
    fun `driving data becomes useful notification text`() {
        val state = AmapDriveLiveViewState()

        val content = state.accept(
            dataType = 3,
            rawData = """{"distance":"300","unit":"米","nextAction":"右转","nextTarget":"进入长安街"}""",
        )

        assertEquals("300米 右转", content?.title)
        assertEquals("进入长安街", content?.content)
        assertEquals(content, state.current())
    }

    @Test
    fun `partial navigation data remains useful`() {
        val state = AmapDriveLiveViewState()

        val content = state.accept(
            dataType = 3,
            rawData = """{"distance":"80","unit":"米","nextAction":"","nextTarget":""}""",
        )

        assertEquals("80米", content?.title)
        assertEquals("80米", content?.content)
    }

    @Test
    fun `invalid update does not erase the last route instruction`() {
        val state = AmapDriveLiveViewState()
        val valid = state.accept(
            dataType = 3,
            rawData = """{"distance":"1.2","unit":"公里","nextAction":"直行","nextTarget":"沿主路行驶"}""",
        )

        assertEquals(valid, state.accept(dataType = 3, rawData = "not-json"))
        assertEquals(valid, state.accept(dataType = 4, rawData = "[]"))
    }

    @Test
    fun `structured fields do not escape the hook callback`() {
        val state = AmapDriveLiveViewState()
        val valid = state.accept(
            dataType = 3,
            rawData = """{"distance":"500","unit":"米","nextAction":"左转","nextTarget":"进入辅路"}""",
        )

        assertEquals(
            valid,
            state.accept(
                dataType = 3,
                rawData = """{"distance":{"value":"300"},"unit":[],"nextAction":{},"nextTarget":[]}""",
            ),
        )
    }

    @Test
    fun `clear event drops stale route instruction`() {
        val state = AmapDriveLiveViewState()
        state.accept(
            dataType = 3,
            rawData = """{"distance":"300","unit":"米","nextAction":"右转","nextTarget":"进入长安街"}""",
        )

        assertNull(state.accept(dataType = 6, rawData = ""))
        assertNull(state.current())
    }
}
