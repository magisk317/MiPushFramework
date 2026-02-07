package top.trumeet.mipushframework.wizard.permission

import android.content.Context
import android.content.Intent

object PermissionInfoFactory {
    private val intentKey = PermissionInfo::class.java.simpleName

    @JvmStatic
    fun create(className: String?, context: Context): PermissionInfo? {
        if (className == null) {
            return null
        }
        return try {
            val clazz = Class.forName(className)
            val constructor = clazz.getConstructor(Context::class.java)
            constructor.newInstance(context) as PermissionInfo
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @JvmStatic
    fun bindPermissionInfo(
        intent: Intent,
        permissionInfoClass: Class<out PermissionInfo>
    ): Intent {
        return intent.putExtra(intentKey, permissionInfoClass.canonicalName)
    }

    @JvmStatic
    fun createFrom(intent: Intent, context: Context): PermissionInfo? {
        val permissionInfoClass = intent.getStringExtra(intentKey)
        return create(permissionInfoClass, context)
    }
}
