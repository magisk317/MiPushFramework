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
import io.github.magisk317.mipush.common.NotificationStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        style: NotificationStyle = NotificationStyle.GENERAL,
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
            style = style,
        )
            .buildJsonParam()
            .normalizeShowNotification(showNotification)
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
        style: NotificationStyle = NotificationStyle.GENERAL,
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
            style = style,
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
                    .normalizeShowNotification(showNotification)
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
        style: NotificationStyle = NotificationStyle.GENERAL,
    ): HyperIslandNotification {
        val builder = HyperIslandNotification.Builder(
            context = context,
            businessName = BUSINESS,
            ticker = title,
        )
            .addPicture(HyperPicture(PIC_ICON_KEY, icon))
            .setIslandConfig(timeout = timeoutSecs)
            .setIslandFirstFloat(firstFloat)
            .setEnableFloat(enableFloat)
            .setShowNotification(showNotification)
            .setReopen(false)
            .setAodConfig(title = content)

        builder.setSmallIsland(PIC_ICON_KEY)

        // 根据分类选择模板
        when (style) {
            NotificationStyle.MESSAGE -> {
                builder.setChatInfo(title = title, content = content, pictureKey = PIC_ICON_KEY)
            }
            NotificationStyle.BANNER -> {
                builder.setBaseInfo(title = title, content = content, pictureKey = PIC_ICON_KEY, type = 2)
            }
            NotificationStyle.ALERT -> {
                builder.setHighlightInfo(title = title, content = content, picKey = PIC_ICON_KEY)
                // 尝试提取倒计时并设置岛倒计时
                val countdownMs = extractCountdownMs(title, content)
                if (countdownMs > 0) {
                    builder.setBigIslandCountdown(countdownMs, PIC_ICON_KEY)
                }
            }
            NotificationStyle.PROMO -> {
                builder.setHighlightInfoV3(primaryText = title, secondaryText = content)
            }
            NotificationStyle.MEDIA -> {
                builder.setCoverInfo(picKey = PIC_ICON_KEY, title = title, content = content)
            }
            NotificationStyle.PROGRESS -> {
                builder.setIconTextInfo(picKey = PIC_ICON_KEY, title = title, content = content)
                // 尝试提取进度并设置岛环形进度
                val progress = extractProgress(content)
                if (progress in 0..100) {
                    builder.setSmallIslandCircularProgress(
                        pictureKey = PIC_ICON_KEY,
                        progress = progress,
                    )
                    builder.setBigIslandProgressCircle(
                        pictureKey = PIC_ICON_KEY,
                        title = title,
                        progress = progress,
                    )
                }
            }
            NotificationStyle.GENERAL -> {
                builder.setBaseInfo(title = title, content = content, pictureKey = PIC_ICON_KEY, type = 1)
            }
        }

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

    /** 从文本中提取进度百分比，返回 -1 表示未找到 */
    private fun extractProgress(text: String): Int {
        Regex("(\\d{1,3})%").find(text)?.let {
            val value = it.groupValues[1].toIntOrNull()
            if (value != null && value in 0..100) return value
        }
        return -1
    }

    /** 从标题/内容中提取倒计时毫秒数 */
    private fun extractCountdownMs(title: String, content: String): Long {
        val text = "$title $content"
        Regex("(\\d+)\\s*(?:分钟|min|mins|minute|minutes)").find(text)?.let {
            val mins = it.groupValues[1].toLongOrNull()
            if (mins != null && mins in 1..1440) return mins * 60 * 1000
        }
        Regex("(\\d+)\\s*(?:小时|hour|hours|hr|hrs)").find(text)?.let {
            val hours = it.groupValues[1].toLongOrNull()
            if (hours != null && hours in 1..72) return hours * 3600 * 1000
        }
        Regex("(\\d+)\\s*(?:秒|sec|second|seconds)").find(text)?.let {
            val secs = it.groupValues[1].toLongOrNull()
            if (secs != null && secs in 1..3600) return secs * 1000
        }
        Regex("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?").find(text)?.let {
            val h = it.groupValues[1].toLongOrNull() ?: 0
            val m = it.groupValues[2].toLongOrNull() ?: 0
            val s = it.groupValues[3].toLongOrNull() ?: 0
            val totalMs = (h * 3600 + m * 60 + s) * 1000
            if (totalMs in 1000..86400000) return totalMs
        }
        return 0
    }

    private fun String.fixTextButtonJson(): String {
        return try {
            val root = Json.parseToJsonElement(this).jsonObject
            val paramV2Element = root["param_v2"]?.jsonObject ?: return this
            val buttonsElement = paramV2Element["textButton"]?.jsonArray ?: return this
            
            val newButtons = JsonArray(buttonsElement.map { buttonElement ->
                val button = buttonElement.jsonObject
                val action = button["actionIntent"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                if (action != null) {
                    buildJsonObject {
                        button.forEach { k, v -> if (k != "actionIntent" && k != "actionIntentType") put(k, v) }
                        put("action", JsonPrimitive(action))
                    }
                } else {
                    button
                }
            })
            
            val newParamV2 = buildJsonObject {
                paramV2Element.forEach { k, v -> if (k != "textButton") put(k, v) }
                put("textButton", newButtons)
            }
            
            buildJsonObject {
                root.forEach { k, v -> if (k != "param_v2") put(k, v) }
                put("param_v2", newParamV2)
            }.toString()
        } catch (_: Throwable) {
            this
        }
    }

    internal fun normalizeShowNotificationJson(raw: String, showNotification: Boolean): String {
        return try {
            val root = Json.parseToJsonElement(raw).jsonObject
            buildJsonObject {
                root.forEach { k, v -> if (k != "isShowNotification" && k != "param_v2") put(k, v) }
                put("isShowNotification", JsonPrimitive(showNotification))
                root["param_v2"]?.jsonObject?.let { paramV2 ->
                    put("param_v2", buildJsonObject {
                        paramV2.forEach { k, v -> if (k != "isShowNotification" && k != "showNotification") put(k, v) }
                        put("isShowNotification", JsonPrimitive(showNotification))
                        put("showNotification", JsonPrimitive(showNotification))
                    })
                }
            }.toString()
        } catch (_: Throwable) {
            raw
        }
    }

    private fun String.normalizeShowNotification(showNotification: Boolean): String =
        normalizeShowNotificationJson(this, showNotification)

    private fun String.injectIslandAppearance(
        highlightColor: String?,
        islandOuterGlow: Boolean,
    ): String {
        if (highlightColor.isNullOrBlank() && !islandOuterGlow) return this
        return try {
            val root = Json.parseToJsonElement(this).jsonObject
            val paramV2Element = root["param_v2"]?.jsonObject ?: return this
            val paramIslandElement = paramV2Element["param_island"]?.jsonObject ?: JsonObject(emptyMap())
            
            val newParamIsland = buildJsonObject {
                paramIslandElement.forEach { k, v -> put(k, v) }
                if (!highlightColor.isNullOrBlank()) put("highlightColor", JsonPrimitive(highlightColor))
                if (islandOuterGlow) put("outEffectSrc", JsonPrimitive("outer_glow"))
            }
            
            val newParamV2 = buildJsonObject {
                paramV2Element.forEach { k, v -> if (k != "param_island") put(k, v) }
                put("param_island", newParamIsland)
            }
            
            buildJsonObject {
                root.forEach { k, v -> if (k != "param_v2") put(k, v) }
                put("param_v2", newParamV2)
            }.toString()
        } catch (_: Throwable) {
            this
        }
    }
}
