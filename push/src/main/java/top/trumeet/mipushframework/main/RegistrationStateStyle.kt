package top.trumeet.mipushframework.main

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.xiaomi.xmsf.R
import top.trumeet.mipush.provider.entities.RegisteredApplication

object RegistrationStateStyle {

    val ErrorColor = Color(0xFFF41804)
    val GreenColor = Color(0xff4caf50)
    val YellowColor = Color(0xffff9800)

    fun isConfirmedRegistered(app: RegisteredApplication): Boolean {
        return app.registeredType == RegisteredApplication.RegisteredType.Registered
    }

    fun hasObservedActivity(app: RegisteredApplication): Boolean {
        return app.lastReceiveTime.time > 0L
    }

    fun contentOf(app: RegisteredApplication, context: Context): Pair<String, Color> {
        val prefix =
            if (!app.existServices) context.getString(R.string.mipush_services_not_found) + " - "
            else ""
        return Pair(prefix + registrationLabelOf(app, context), colorOf(app))
    }

    fun registrationLabelOf(app: RegisteredApplication, context: Context): String {
        return when (app.registeredType) {
            RegisteredApplication.RegisteredType.Registered -> context.getString(R.string.app_registered)
            else -> {
                if (hasObservedActivity(app)) {
                    context.getString(R.string.app_registration_observed)
                } else if (app.registeredType == RegisteredApplication.RegisteredType.Unregistered) {
                    context.getString(R.string.app_registered_error)
                } else {
                    context.getString(R.string.status_app_not_registered)
                }
            }
        }
    }

    fun registrationColorOf(app: RegisteredApplication): Color {
        return if (hasObservedActivity(app) && !isConfirmedRegistered(app)) YellowColor
        else when (app.registeredType) {
            RegisteredApplication.RegisteredType.Registered -> GreenColor
            RegisteredApplication.RegisteredType.Unregistered -> YellowColor
            else -> Color.Unspecified
        }
    }

    fun colorOf(app: RegisteredApplication): Color {
        return if (!app.existServices) ErrorColor
        else registrationColorOf(app)
    }
}
