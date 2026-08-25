package io.github.magisk317.mipush.feature.main

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.manager.R

object RegistrationStateStyle {

    val ErrorColor = Color(0xFFF41804)
    val GreenColor = Color(0xff4caf50)
    val YellowColor = Color(0xffff9800)

    fun isConfirmedRegistered(app: ManagerApplication): Boolean {
        return app.registeredType == ManagerApplication.RegisteredType.REGISTERED
    }

    fun hasObservedActivity(app: ManagerApplication): Boolean {
        return app.lastReceiveTimeMs > 0L
    }

    fun contentOf(app: ManagerApplication): Pair<Int, Color> {
        return Pair(registrationLabelResOf(app), colorOf(app))
    }

    @StringRes
    fun registrationLabelResOf(app: ManagerApplication): Int {
        return when (app.registeredType) {
            ManagerApplication.RegisteredType.REGISTERED -> R.string.app_registered
            else -> {
                if (hasObservedActivity(app)) {
                    R.string.app_registration_observed
                } else if (app.registeredType == ManagerApplication.RegisteredType.UNREGISTERED) {
                    R.string.app_registered_error
                } else {
                    R.string.status_app_not_registered
                }
            }
        }
    }

    fun registrationColorOf(app: ManagerApplication): Color {
        return if (hasObservedActivity(app) && !isConfirmedRegistered(app)) YellowColor
        else when (app.registeredType) {
            ManagerApplication.RegisteredType.REGISTERED -> GreenColor
            ManagerApplication.RegisteredType.UNREGISTERED -> YellowColor
            else -> Color.Unspecified
        }
    }

    fun colorOf(app: ManagerApplication): Color {
        return if (!app.existServices) ErrorColor
        else registrationColorOf(app)
    }
}
