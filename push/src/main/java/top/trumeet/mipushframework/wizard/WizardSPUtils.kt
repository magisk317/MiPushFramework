package top.trumeet.mipushframework.wizard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.ActivityCompat
import top.trumeet.common.Constants
import top.trumeet.mipushframework.main.MainActivity

/**
 * A util store Wizard info to SP
 */
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.magisk317.data.DataStoreManager
import com.xiaomi.xmsf.MiPushFrameworkApp

/**
 * A util store Wizard info to SP
 */
internal object WizardSPUtils {

    @JvmStatic
    fun shouldShowWizard(context: Context): Boolean {
        return runBlocking { DataStoreManager.showWizard.first() }
    }

    @JvmStatic
    fun setShouldShowWizard(value: Boolean, context: Context) {
        MiPushFrameworkApp.applicationScope.launch {
            DataStoreManager.setShowWizard(value)
        }
    }

    @JvmStatic
    fun finishWizard(context: Activity) {
        setShouldShowWizard(false, context)
        ActivityCompat.finishAffinity(context)
        context.startActivity(Intent(context, MainActivity::class.java))
    }
}
