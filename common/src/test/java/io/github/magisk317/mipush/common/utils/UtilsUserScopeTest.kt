package io.github.magisk317.mipush.common.utils

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class UtilsUserScopeTest {
    @Test
    fun lastReceiveTimeKeys_areScopedByAndroidUser() {
        assertNotEquals(
            Utils.lastReceiveTimePreferenceKey("com.example.app", 0),
            Utils.lastReceiveTimePreferenceKey("com.example.app", 999),
        )
    }

    @Test
    fun regSecKeys_areScopedByAndroidUser() {
        assertNotEquals(
            Utils.regSecPreferenceKey("com.example.app", 0),
            Utils.regSecPreferenceKey("com.example.app", 999),
        )
    }
}
