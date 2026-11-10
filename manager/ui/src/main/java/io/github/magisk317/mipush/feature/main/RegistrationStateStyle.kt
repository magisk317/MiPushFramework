package io.github.magisk317.mipush.feature.main

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import io.github.magisk317.mipush.manager.application.ManagerApplication
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

    private enum class DisplayState { Denied, Pending, Registered, Observed, Unregistered, NotRegistered }

    // Admission overrides the badge without rewriting the independent server registration result.
    private fun displayStateOf(app: ManagerApplication): DisplayState = when {
        app.blocked || app.type == ManagerApplication.Type.DENY -> DisplayState.Denied
        app.type == ManagerApplication.Type.ASK && app.registrationPending -> DisplayState.Pending
        isConfirmedRegistered(app) -> DisplayState.Registered
        hasObservedActivity(app) -> DisplayState.Observed
        app.registeredType == ManagerApplication.RegisteredType.UNREGISTERED -> DisplayState.Unregistered
        else -> DisplayState.NotRegistered
    }

    @StringRes
    fun registrationLabelResOf(app: ManagerApplication): Int = when (displayStateOf(app)) {
        DisplayState.Denied -> R.string.app_registration_denied
        DisplayState.Pending -> R.string.app_registration_pending
        DisplayState.Registered -> R.string.app_registered
        DisplayState.Observed -> R.string.app_registration_observed
        DisplayState.Unregistered -> R.string.app_registered_error
        DisplayState.NotRegistered -> R.string.status_app_not_registered
    }

    fun registrationColorOf(app: ManagerApplication): Color = when (displayStateOf(app)) {
        DisplayState.Denied -> ErrorColor
        DisplayState.Pending, DisplayState.Observed, DisplayState.Unregistered -> YellowColor
        DisplayState.Registered -> GreenColor
        DisplayState.NotRegistered -> Color.Unspecified
    }

    fun colorOf(app: ManagerApplication): Color {
        return if (!app.existServices) ErrorColor
        else registrationColorOf(app)
    }
}
