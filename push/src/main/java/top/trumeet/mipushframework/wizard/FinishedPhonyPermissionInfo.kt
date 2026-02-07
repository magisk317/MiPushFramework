package top.trumeet.mipushframework.wizard

import android.content.Context
import com.xiaomi.xmsf.R

class FinishedPhonyPermissionInfo(context: Context) : DisplayOnlyPhonyPermissionInfo(context) {
    override val permissionTitle: String
        get() = context.getString(R.string.app_name)

    override val permissionDescription: String
        get() = context.getString(R.string.wizard_descr_finish)
}
