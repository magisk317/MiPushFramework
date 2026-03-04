package top.trumeet.mipushframework.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationsRouteTest {

    @Test(expected = IllegalArgumentException::class)
    fun appDetailsRoute_rejectsBlankPackageName() {
        AppDestinations.AppDetails.route(" ")
    }

    @Test
    fun appDetailsRoute_encodesSpecialChars() {
        val route = AppDestinations.AppDetails.route("com.example/app")
        assertEquals("app_details/com.example%2Fapp", route)
    }
}
