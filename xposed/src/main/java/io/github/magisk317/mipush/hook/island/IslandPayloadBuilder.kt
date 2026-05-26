package io.github.magisk317.mipush.hook.island

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import org.json.JSONObject

object IslandPayloadBuilder {
    private const val BUSINESS = "mipush_framework_push"
    private const val PIC_ICON_KEY = "mipush_icon"
    private const val PIC_ICON = "miui.focus.pic_mipush_icon"
    private const val FOCUS_PICS = "miui.focus.pics"
    private const val FOCUS_ACTIONS = "miui.focus.actions"
    private const val FOCUS_ACTION_PREFIX = "miui.focus.action_"

    fun buildFocusParam(
        context: Context,
        title: String,
        content: String,
        icon: Icon? = null,
        timeoutSecs: Int = 5,
        firstFloat: Boolean = true,
        enableFloat: Boolean = true,
        showNotification: Boolean = true,
        showIslandIcon: Boolean = true,
        highlightColor: String? = null,
        islandOuterGlow: Boolean = false,
        actions: List<Notification.Action> = emptyList(),
    ): String {
        return createNotification(
            context = context,
            title = title,
            content = content,
            icon = icon ?: fallbackIcon(context),
            timeoutSecs = timeoutSecs,
            firstFloat = firstFloat,
            enableFloat = enableFloat,
            showNotification = showNotification,
            showIslandIcon = showIslandIcon,
            actions = actions,
        )
            .buildJsonParam()
            .fixTextButtonJson()
            .injectIslandAppearance(highlightColor, islandOuterGlow)
    }

    fun buildExtras(
        context: Context,
        title: String,
        content: String,
        icon: Icon? = null,
        timeoutSecs: Int = 5,
        firstFloat: Boolean = true,
        enableFloat: Boolean = true,
        showNotification: Boolean = true,
        sourcePackage: String? = null,
        sourceChannelId: String? = null,
        actions: List<Notification.Action> = emptyList(),
        showIslandIcon: Boolean = true,
        highlightColor: String? = null,
        islandOuterGlow: Boolean = false,
    ): Bundle {
        val safeContent = content.ifBlank { title }
        val notification = createNotification(
            context = context,
            title = title,
            content = safeContent,
            icon = icon ?: fallbackIcon(context),
            timeoutSecs = timeoutSecs,
            firstFloat = firstFloat,
            enableFloat = enableFloat,
            showNotification = showNotification,
            showIslandIcon = showIslandIcon,
            actions = actions,
        )
        return Bundle().apply {
            putBundle(
                FOCUS_PICS,
                Bundle().apply {
                    putParcelable(PIC_ICON, icon ?: fallbackIcon(context))
                },
            )
            putString(PIC_ICON, PIC_ICON)
            buildActionsBundle(actions)?.let { actionsBundle ->
                putBundle(FOCUS_ACTIONS, actionsBundle)
                flattenActions(actionsBundle, this)
            }
            putString(
                IslandDispatchContract.FOCUS_PARAM,
                notification
                    .buildJsonParam()
                    .fixTextButtonJson()
                    .injectIslandAppearance(highlightColor, islandOuterGlow),
            )
            putString(IslandDispatchContract.OWNER, IslandDispatchContract.OWNER_MARKER)
            putBoolean(IslandDispatchContract.PROCESSED, true)
            if (islandOuterGlow) {
                putString("miui.bigIsland.effect.src", "outer_glow")
            }
            sourcePackage?.let { putString(IslandDispatchContract.SOURCE_PACKAGE, it) }
            sourceChannelId?.let { putString(IslandDispatchContract.SOURCE_CHANNEL, it) }
        }
    }

    private fun createNotification(
        context: Context,
        title: String,
        content: String,
        icon: Icon,
        timeoutSecs: Int,
        firstFloat: Boolean,
        enableFloat: Boolean,
        showNotification: Boolean,
        showIslandIcon: Boolean,
        actions: List<Notification.Action>,
    ): HyperIslandNotification {
        val builder = HyperIslandNotification.Builder(
            context = context,
            businessName = BUSINESS,
            ticker = title,
        )
            .addPicture(HyperPicture(PIC_ICON_KEY, icon))
            .setIconTextInfo(PIC_ICON_KEY, title, content)
            .setIslandConfig(timeout = timeoutSecs)
            .setIslandFirstFloat(firstFloat)
            .setEnableFloat(enableFloat)
            .setShowNotification(showNotification)
            .setReopen(false)
            .setAodConfig(title = content)

        builder.setSmallIsland(PIC_ICON_KEY)
        builder.setBigIslandInfo(
            left = if (showIslandIcon) {
                ImageTextInfoLeft(
                    type = 1,
                    picInfo = PicInfo(type = 1, pic = PIC_ICON_KEY),
                    textInfo = TextInfo(title = title),
                )
            } else {
                ImageTextInfoLeft(
                    type = 1,
                    textInfo = TextInfo(title = title),
                )
            },
            right = ImageTextInfoRight(
                type = 2,
                textInfo = TextInfo(
                    title = content,
                    narrowFont = true,
                ),
            ),
        )

        val hyperActions = actions.take(2).mapIndexedNotNull { index, action ->
            val pendingIntent = action.actionIntent ?: return@mapIndexedNotNull null
            HyperAction(
                key = "mipush_island_action_$index",
                title = action.title?.toString().orEmpty(),
                pendingIntent = pendingIntent,
                actionIntentType = 2,
            )
        }
        if (hyperActions.isNotEmpty()) {
            hyperActions.forEach { builder.addHiddenAction(it) }
            builder.setTextButtons(*hyperActions.toTypedArray())
        }

        return builder
    }

    private fun buildActionsBundle(actions: List<Notification.Action>): Bundle? {
        val bundle = Bundle()
        actions.take(2).forEachIndexed { index, action ->
            val key = "$FOCUS_ACTION_PREFIX${actionKey(index)}"
            bundle.putParcelable(key, action)
        }
        return if (bundle.isEmpty) null else bundle
    }

    private fun actionKey(index: Int): String = "mipush_island_action_$index"

    private fun flattenActions(actionsBundle: Bundle, target: Bundle) {
        for (key in actionsBundle.keySet()) {
            target.putParcelable(key, actionsBundle.getNotificationAction(key))
        }
    }

    private fun Bundle.getNotificationAction(key: String): Notification.Action? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelable(key, Notification.Action::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelable(key)
        }
    }

    private fun fallbackIcon(context: Context): Icon {
        return Icon.createWithResource(context, android.R.drawable.sym_def_app_icon)
    }

    private fun String.fixTextButtonJson(): String {
        return try {
            val json = JSONObject(this)
            val paramV2 = json.optJSONObject("param_v2") ?: return this
            val buttons = paramV2.optJSONArray("textButton") ?: return this
            for (index in 0 until buttons.length()) {
                val button = buttons.getJSONObject(index)
                val action = button.optString("actionIntent").takeIf { it.isNotBlank() }
                    ?: continue
                button.put("action", action)
                button.remove("actionIntent")
                button.remove("actionIntentType")
            }
            json.toString()
        } catch (_: Throwable) {
            this
        }
    }

    private fun String.injectIslandAppearance(
        highlightColor: String?,
        islandOuterGlow: Boolean,
    ): String {
        if (highlightColor.isNullOrBlank() && !islandOuterGlow) return this
        return try {
            val json = JSONObject(this)
            val paramV2 = json.optJSONObject("param_v2") ?: return this
            val paramIsland = paramV2.optJSONObject("param_island") ?: JSONObject()
            if (!highlightColor.isNullOrBlank()) {
                paramIsland.put("highlightColor", highlightColor)
            }
            if (islandOuterGlow) {
                paramIsland.put("outEffectSrc", "outer_glow")
            }
            paramV2.put("param_island", paramIsland)
            json.toString()
        } catch (_: Throwable) {
            this
        }
    }
}
