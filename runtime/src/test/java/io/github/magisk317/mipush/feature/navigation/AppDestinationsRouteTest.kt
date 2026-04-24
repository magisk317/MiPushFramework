package io.github.magisk317.mipush.feature.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AppDestinationsRouteTest {

    @Test
    fun appDetailsRoute_rejectsBlankPackageName() {
        assertThrows(IllegalArgumentException::class.java) {
            AppDestinations.AppDetails.route(" ")
        }
    }

    @Test
    fun appDetailsRoute_encodesSpecialChars() {
        val route = AppDestinations.AppDetails.route("com.example/app")
        assertEquals("app_details/com.example%2Fapp", route)
    }
}
