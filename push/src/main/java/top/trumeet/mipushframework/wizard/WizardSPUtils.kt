package top.trumeet.mipushframework.wizard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.ActivityCompat
import top.trumeet.common.Constants
import top.trumeet.mipushframework.main.MainPage

/**
 * A util store Wizard info to SP
 */
internal object WizardSPUtils {
    private fun getSp(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(
            Constants.WIZARD_SP_NAME,
            Context.MODE_PRIVATE
        )
    }

    @JvmStatic
    fun shouldShowWizard(context: Context): Boolean {
        return getSp(context).getBoolean(Constants.KEY_SHOW_WIZARD, true)
    }

    @JvmStatic
    fun setShouldShowWizard(value: Boolean, context: Context) {
        getSp(context).edit().putBoolean(Constants.KEY_SHOW_WIZARD, value).apply()
    }

    @JvmStatic
    fun finishWizard(context: Activity) {
        setShouldShowWizard(false, context)
        ActivityCompat.finishAffinity(context)
        context.startActivity(Intent(context, MainPage::class.java))
    }
}
