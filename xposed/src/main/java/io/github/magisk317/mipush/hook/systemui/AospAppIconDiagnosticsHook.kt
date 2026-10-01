package io.github.magisk317.mipush.hook.systemui

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.ImageView
import io.github.magisk317.mipush.common.BuildConfig
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.common.notification.iconpack.digestIdentity
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.callMethod
import io.github.magisk317.xposed.currentApplication
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.getHookObjectField
import io.github.magisk317.xposed.hookAllMethods
import io.github.magisk317.xposed.hookMethod

/**
 * Read-only diagnostics for the Android/Pixel and Samsung notification app-icon paths.
 *
 * This intentionally does not replace any drawable. It records enough information to distinguish
 * the Notification.smallIcon, app-icon provider, Samsung icon-tray, and Notification.largeIcon
 * paths in one capture. Package/user/notification identities are always one-way digests.
 */
internal class AospAppIconDiagnosticsHook {
    private val tag = "AppIconDiagnostics"

    fun hook(classLoader: ClassLoader) {
        val results = listOf(
            "row_icon_provider" to hookNotificationRowIconProvider(classLoader),
            "app_icon_provider" to hookAppIconProvider(classLoader),
            "row_content_binder" to hookNotificationRowContentBinder(classLoader),
            "row_icon_view" to hookNotificationRowIconView(classLoader),
            "template_wrapper" to hookTemplateViewWrapper(classLoader),
            "icon_manager" to hookIconManager(classLoader),
        )
        val installed = results.filter { it.second }.map { it.first }
        val missing = results.filterNot { it.second }.map { it.first }
        XLog.i(
            tag,
            "HookSystemUI app-icon diagnostics commit=${BuildConfig.GIT_COMMIT} " +
                "installed=[${installed.joinToString()}] missing=[${missing.joinToString()}]",
        )
    }

    private fun hookNotificationRowIconProvider(classLoader: ClassLoader): Boolean {
        val className = "com.android.systemui.statusbar.notification.row.icon." +
            "NotificationRowIconViewInflaterFactory\$createIconProvider\$2"
        return runCatching {
            classLoader.findClass(className).apply {
                hookAllMethods("shouldShowAppIcon") {
                    doAfter {
                        val owner = thisObject ?: return@doAfter
                        val sbn = readField(owner, "\$sbn") as? StatusBarNotification
                        val context = readField(owner, "\$context") as? Context
                        val shown = result as? Boolean
                        logNotification(
                            stage = "row_icon_provider_should_show",
                            sbn = sbn,
                            notification = sbn?.notification,
                            context = context,
                            extra = "result=$shown contextPkgDigest=${digest(context?.packageName)}",
                        )
                    }
                }
                hookAllMethods("getAppIcon") {
                    doAfter {
                        val owner = thisObject ?: return@doAfter
                        val sbn = readField(owner, "\$sbn") as? StatusBarNotification
                        val context = readField(owner, "\$context") as? Context
                        val drawable = result as? Drawable
                        logNotification(
                            stage = "row_icon_provider_get_app_icon",
                            sbn = sbn,
                            notification = sbn?.notification,
                            context = context,
                            extra = "contextPkgDigest=${digest(context?.packageName)} " +
                                "drawable=${describeDrawable(drawable)}",
                        )
                    }
                }
            }
            XLog.d(tag, "installed notification row app-icon provider diagnostics class=$className")
            true
        }.onFailure {
            XLog.d(tag, "notification row app-icon provider diagnostics unavailable " +
                "class=$className error=${it.javaClass.simpleName}")
        }.getOrDefault(false)
    }

    private fun hookAppIconProvider(classLoader: ClassLoader): Boolean {
        val classNames = listOf(
            "com.android.systemui.statusbar.notification.row.icon.AppIconProviderImpl",
            "com.android.systemui.notifications.content.icon.AppIconProviderImpl",
        )
        var installed = false
        classNames.forEach { className ->
            runCatching {
                classLoader.findClass(className).hookMethod(
                    "getOrFetchAppIcon",
                    UserHandle::class.java,
                    String::class.java,
                    String::class.java,
                ) {
                    doAfter {
                        val user = args.getOrNull(0) as? UserHandle
                        val packageName = args.getOrNull(1) as? String
                        val style = args.getOrNull(2) as? String
                        XLog.i(
                            tag,
                            "stage=app_icon_provider_fetch provider=${providerName(className)} " +
                                "pkgDigest=${digest(packageName)} userDigest=${digestUser(user)} " +
                                "style=${style ?: "none"} result=${describeDrawable(result as? Drawable)}",
                        )
                    }
                }
                XLog.d(tag, "installed app-icon provider fetch diagnostics class=$className")
                installed = true
            }.onFailure {
                XLog.d(tag, "app-icon provider fetch diagnostics unavailable " +
                    "class=$className error=${it.javaClass.simpleName}")
            }
        }
        return installed
    }

    private fun hookNotificationRowContentBinder(classLoader: ClassLoader): Boolean {
        val className = "com.android.systemui.statusbar.notification.row." +
            "NotificationRowContentBinderImpl\$Companion\$applyRemoteView\$listener\$1"
        return runCatching {
            classLoader.findClass(className).hookMethod("onViewApplied", View::class.java) {
                doAfter {
                    val listener = thisObject ?: return@doAfter
                    val entry = readField(listener, "\$entry")
                    val row = readField(listener, "\$row")
                    val sbn = statusBarNotificationFromEntry(entry) ?: statusBarNotificationFromRow(row)
                    val root = args.getOrNull(0) as? View
                    logNotification(
                        stage = "binder_on_view_applied_after",
                        sbn = sbn,
                        notification = sbn?.notification,
                        context = root?.context,
                        extra = "listener=${listener.javaClass.name} " +
                            "entryClass=${entry?.javaClass?.name ?: "none"} " +
                            describeFinalView(root),
                    )
                }
            }
            XLog.d(tag, "installed notification binder final-view diagnostics class=$className")
            true
        }.onFailure {
            XLog.d(tag, "notification binder final-view diagnostics unavailable " +
                "class=$className error=${it.javaClass.simpleName}")
        }.getOrDefault(false)
    }

    private fun hookNotificationRowIconView(classLoader: ClassLoader): Boolean {
        return runCatching {
            classLoader.findClass("com.android.internal.widget.NotificationRowIconView")
                .hookMethod("setImageIcon", Icon::class.java) {
                    doBefore {
                        val view = thisObject as? ImageView ?: return@doBefore
                        XLog.i(
                            tag,
                            "stage=row_icon_view_set_image_icon_before " +
                                "view=${view.javaClass.name} passed=${describeIcon(args.getOrNull(0) as? Icon)}",
                        )
                    }
                    doAfter {
                        val view = thisObject as? ImageView ?: return@doAfter
                        val appIcon = readField(view, "mAppIcon") as? Drawable
                        XLog.i(
                            tag,
                            "stage=row_icon_view_set_image_icon_after " +
                                "view=${view.javaClass.name} appIcon=${describeDrawable(appIcon)} " +
                                "drawable=${describeDrawable(view.drawable)}",
                        )
                    }
                }
            XLog.d(tag, "installed NotificationRowIconView setImageIcon diagnostics")
            true
        }.onFailure {
            XLog.d(tag, "NotificationRowIconView diagnostics unavailable " +
                "error=${it.javaClass.simpleName}")
        }.getOrDefault(false)
    }

    private fun hookTemplateViewWrapper(classLoader: ClassLoader): Boolean {
        return runCatching {
            val rowClass = classLoader.findClass(
                "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow",
            )
            classLoader.findClass(
                "com.android.systemui.statusbar.notification.row.wrapper.NotificationTemplateViewWrapper",
            ).hookMethod("onContentUpdated", rowClass) {
                doAfter {
                    val row = args.getOrNull(0)
                    val sbn = statusBarNotificationFromRow(row)
                    val notification = sbn?.notification
                    val rightIcon = readField(thisObject, "mRightIcon") as? ImageView
                    val headerIcon = readField(thisObject, "mIcon") as? ImageView
                    logNotification(
                        stage = "template_on_content_updated_after",
                        sbn = sbn,
                        notification = notification,
                        context = (rightIcon ?: headerIcon)?.context,
                        extra = "largeIcon=${describeIcon(notification?.largeIconCompat())} " +
                            "rightDrawable=${describeDrawable(rightIcon?.drawable)} " +
                            "rightTag=${describeIconTag(rightIcon)} " +
                            "headerDrawable=${describeDrawable(headerIcon?.drawable)} " +
                            "headerTag=${describeIconTag(headerIcon)}",
                    )
                }
            }
            XLog.d(tag, "installed notification template large-icon diagnostics")
            true
        }.onFailure {
            XLog.d(tag, "notification template large-icon diagnostics unavailable " +
                "error=${it.javaClass.simpleName}")
        }.getOrDefault(false)
    }

    private fun hookIconManager(classLoader: ClassLoader): Boolean {
        return runCatching {
            val entryClass = classLoader.findClass(
                "com.android.systemui.statusbar.notification.collection.NotificationEntry",
            )
            classLoader.findClass("com.android.systemui.statusbar.notification.icon.IconManager")
                .hookMethod("getIconDescriptor", entryClass, Boolean::class.javaPrimitiveType!!) {
                    doAfter {
                        val entry = args.getOrNull(0)
                        val sbn = statusBarNotificationFromEntry(entry)
                        val descriptor = result
                        val descriptorIcon = readField(descriptor, "icon") as? Icon
                        logNotification(
                            stage = "icon_manager_get_descriptor_after",
                            sbn = sbn,
                            notification = sbn?.notification,
                            extra = "descriptor=${descriptor?.javaClass?.name ?: "none"} " +
                                "descriptorIcon=${describeIcon(descriptorIcon)} " +
                                "descriptorType=${readField(descriptor, "type") ?: "none"}",
                        )
                    }
                }
            XLog.d(tag, "installed IconManager small-icon descriptor diagnostics")
            true
        }.onFailure {
            XLog.d(tag, "IconManager descriptor diagnostics unavailable " +
                "error=${it.javaClass.simpleName}")
        }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    private fun logNotification(
        stage: String,
        sbn: StatusBarNotification?,
        notification: Notification?,
        context: Context? = null,
        extra: String,
    ) {
        val extras = notification?.extras
        val targetPackage = resolveTargetPackage(extras)
        val postingPackage = sbn?.packageName
        val localFallback = postingPackage == XMSF_PACKAGE_NAME &&
            !targetPackage.isNullOrBlank() && targetPackage != XMSF_PACKAGE_NAME
        val userId = sbn?.userId ?: -1
        XLog.i(
            tag,
            "commit=${BuildConfig.GIT_COMMIT} stage=$stage postingPkgDigest=${digest(postingPackage)} " +
                "targetPkgDigest=${digest(targetPackage)} userDigest=${digestUserId(userId)} " +
                "sbnKeyDigest=${digest(sbn?.key)} id=${sbn?.id ?: -1} tagDigest=${digest(sbn?.tag)} " +
                "localXmsfFallback=$localFallback managed=${isManaged(extras)} " +
                "settings=${describeSettings(context ?: currentApplication(), userId)} " +
                "extras=${describeExtras(extras, targetPackage)} " +
                "smallIcon=${describeIcon(notification?.smallIcon)} " +
                "largeIcon=${describeIcon(notification?.largeIconCompat())} " +
                "flags=0x${notification?.flags?.toString(16) ?: "0"} " +
                extra,
        )
    }

    private fun describeFinalView(root: View?): String {
        if (root == null) return "root=none"
        val iconView = root.findViewById(android.R.id.icon) as? ImageView
        if (iconView == null) return "root=${root.javaClass.name} iconView=none"
        val useAppIconId = resourceId(iconView, "use_app_icon")
        val imageIconTagId = resourceId(iconView, "image_icon_tag")
        val useAppIcon = if (useAppIconId != 0) iconView.getTag(useAppIconId) else null
        val imageIconTag = if (imageIconTagId != 0) iconView.getTag(imageIconTagId) else null
        val internalAppIcon = readField(iconView, "mAppIcon") as? Drawable
        return "root=${root.javaClass.name} iconView=${iconView.javaClass.name} " +
            "drawable=${describeDrawable(iconView.drawable)} " +
            "internalAppIcon=${describeDrawable(internalAppIcon)} " +
            "useAppIconTag=${useAppIcon ?: "none"} " +
            "imageIconTag=${describeIconTagValue(imageIconTag)} " +
            "useAppIconId=$useAppIconId imageIconTagId=$imageIconTagId"
    }

    private fun describeIconTag(view: ImageView?): String {
        if (view == null) return "none"
        val id = resourceId(view, "image_icon_tag")
        return if (id == 0) "id=0" else describeIconTagValue(view.getTag(id))
    }

    private fun describeIconTagValue(value: Any?): String = when (value) {
        is Icon -> describeIcon(value)
        null -> "none"
        else -> value.javaClass.simpleName
    }

    private fun describeSettings(context: Context?, userId: Int): String {
        if (context == null) return "context=none"
        val resolver = context.contentResolver
        fun read(name: String): Int = runCatching<Int> {
            Settings.System.getInt(resolver, name, -1)
        }.getOrDefault(-1)
        return "showNotificationAppIcon=${read("show_notification_app_icon")} " +
            "colorThemeAppIcon=${read("colortheme_app_icon")}"
    }

    private fun describeExtras(extras: Bundle?, targetPackage: String?): String {
        if (extras == null) return "none"
        return "size=${extras.size()} targetKey=${resolveTargetKey(extras) ?: "none"} " +
            "targetPresent=${!targetPackage.isNullOrBlank()} " +
            "preferSmallIcon=${extras.getBoolean("android.app.preferSmallIcon", false)} " +
            "showSmallIcon=${extras.getBoolean("android.showSmallIcon", false)} " +
            "showBigPictureWhenCollapsed=${extras.getBoolean("android.showBigPictureWhenCollapsed", false)} " +
            "hideStatusBar=${extras.getBoolean("android.hideStatusBarNotification", false)} " +
            "requestPromoted=${extras.getBoolean("android.requestPromotedOngoing", false)} " +
            "iconPackIdentity=${digest(extras.getString("mipush_icon_pack_source_identity"))}"
    }

    private fun describeIcon(icon: Icon?): String {
        if (icon == null) return "none"
        val type = runCatching { icon.type }.getOrDefault(-1)
        val typeName = when (type) {
            Icon.TYPE_BITMAP -> "BITMAP"
            Icon.TYPE_RESOURCE -> "RESOURCE"
            Icon.TYPE_DATA -> "DATA"
            Icon.TYPE_URI -> "URI"
            Icon.TYPE_ADAPTIVE_BITMAP -> "ADAPTIVE_BITMAP"
            6 -> "RESOURCE_ADAPTIVE"
            else -> type.toString()
        }
        val resource = if (type == Icon.TYPE_RESOURCE || type == 6) {
            " resPackageDigest=${digest(runCatching { icon.resPackage }.getOrNull())} " +
                "resId=0x${runCatching { icon.resId }.getOrDefault(0).toString(16)}"
        } else {
            ""
        }
        val dataLength = if (type == Icon.TYPE_DATA) {
            " dataLength=${runCatching { icon.callMethod("getDataLength") as? Int }.getOrNull() ?: -1}"
        } else {
            ""
        }
        return "type=$typeName$resource$dataLength"
    }

    private fun describeDrawable(drawable: Drawable?): String {
        if (drawable == null) return "none"
        return "${drawable.javaClass.name}(${drawable.intrinsicWidth}x${drawable.intrinsicHeight})"
    }

    private fun statusBarNotificationFromEntry(entry: Any?): StatusBarNotification? {
        if (entry == null) return null
        return runCatching { entry.callMethod("getSbn") as? StatusBarNotification }.getOrNull()
            ?: (readField(entry, "mSbn") as? StatusBarNotification)
    }

    private fun statusBarNotificationFromRow(row: Any?): StatusBarNotification? {
        if (row == null) return null
        val entry = runCatching { row.callMethod("getEntryAdapter") }.getOrNull()
        return statusBarNotificationFromEntry(entry)
    }

    private fun readField(owner: Any?, fieldName: String): Any? =
        runCatching { getHookObjectField(owner, fieldName) }.getOrNull()

    private fun resourceId(view: View, name: String): Int = runCatching {
        view.resources.getIdentifier(name, "id", "com.android.systemui")
    }.getOrDefault(0)

    private fun resolveTargetPackage(extras: Bundle?): String? {
        if (extras == null) return null
        return TARGET_KEYS.firstNotNullOfOrNull { key ->
            extras.getString(key)?.takeIf { it.isNotBlank() }
        }
    }

    private fun resolveTargetKey(extras: Bundle): String? =
        TARGET_KEYS.firstOrNull { !extras.getString(it).isNullOrBlank() }

    private fun isManaged(extras: Bundle?): Boolean =
        TARGET_KEYS.any { key -> extras?.containsKey(key) == true } ||
            extras?.getBoolean("mipush_mock_replay_receipt", false) == true

    private fun digest(value: String?): String =
        value?.takeIf { it.isNotBlank() }?.let(::digestIdentity) ?: "none"

    private fun digestUser(user: UserHandle?): String =
        digestUserId(runCatching { user?.callMethod("getIdentifier") as? Int }.getOrNull() ?: -1)

    private fun digestUserId(userId: Int): String = digestIdentity("user:$userId")

    private fun providerName(className: String): String =
        if (className.contains("notifications.content.icon")) "pixel" else "samsung"

    private companion object {
        private val TARGET_KEYS = listOf(
            "target_package",
            "miui.targetPkg",
            "xmsf_target_package",
            "mipush_mock_replay_source_package",
        )
    }
}

@Suppress("DEPRECATION")
private fun Notification.largeIconCompat(): Icon? = runCatching { getLargeIcon() }.getOrNull()
