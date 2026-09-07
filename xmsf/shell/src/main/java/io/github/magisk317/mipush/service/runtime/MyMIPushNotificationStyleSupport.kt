package io.github.magisk317.mipush.service.runtime

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.MIPushNotificationViewSupport
import io.github.magisk317.mipush.notification.SweetTagHandler
import io.github.magisk317.mipush.notification.SweetNotificationCoordinator
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.common.utils.Utils
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.notification.NotificationController.getBitmapFromUri
import io.github.magisk317.mipush.notification.NotificationController.getLargeIcon
import io.github.magisk317.mipush.notification.NotificationController.roundLargeIconIfConfigured
import java.util.LinkedHashMap
import io.github.magisk317.xposed.logging.MagiskOtel

internal object MyMIPushNotificationStyleSupport {
    private const val TAG = "MyNotificationStyle"
    
    private const val NOTIFICATION_BIG_STYLE_MIN_LEN = 25
    private const val MAX_CACHED_CONVERSATIONS = 128
    private const val MAX_MESSAGES_PER_CONVERSATION = 25
    private const val CONVERSATION_HISTORY_TTL_MS = 2 * 60 * 60 * 1000L

    private data class ConversationKey(
        val userId: Int,
        val packageName: String,
        val notificationId: Int,
        val conversationId: String?
    )

    private data class CachedMessage(
        val key: String,
        val message: NotificationCompat.MessagingStyle.Message
    )

    private data class ConversationHistory(
        val messages: MutableList<CachedMessage>,
        var updatedElapsedMs: Long
    )

    private data class PersonWithAvatar(
        val person: Person,
        val avatar: Bitmap?
    )

    private val conversationHistories =
        object : LinkedHashMap<ConversationKey, ConversationHistory>(16, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<ConversationKey, ConversationHistory>
            ): Boolean = size > MAX_CACHED_CONVERSATIONS
        }

    fun normalStyleNotificationBuilder(
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String
    ): NotificationCompat.Builder {
        // Stock 7.4.67-C adds style 5 after the retained 3.7.9 SDK and renders it with Xiaomi-only
        // RemoteViews. Use the same payload fields in a standard notification so the card remains
        // readable when those private layouts are unavailable.
        val sweetCard = SweetNotificationCoordinator.resolveCardContent(metaInfo.extra)
        val title = sweetCard?.title ?: metaInfo.title.orEmpty()
        val description = sweetCard?.text ?: metaInfo.description.orEmpty()
        val renderedTitle = SweetTagHandler.renderFtHtmlIfNeeded(title)
        val renderedDescription = SweetTagHandler.renderFtHtmlIfNeeded(description)
        val bigPic = getBigPic(context, metaInfo, sweetCard?.backgroundUri)

        val styleReason = when {
            sweetCard != null -> "sweet_standard"
            bigPic != null -> "big_picture"
            description.length > NOTIFICATION_BIG_STYLE_MIN_LEN -> "big_text"
            else -> "normal"
        }
        MagiskOtel.event(
            name = "push.event",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "style_path",
                "reason" to styleReason,
                "target_package" to packageName,
            ),
            statusOk = true,
        )
        return NotificationCompat.Builder(context, "xmsf.default").apply {
            if (bigPic != null) {
                val style = NotificationCompat.BigPictureStyle()
                style.bigPicture(bigPic)
                style.setBigContentTitle(renderedTitle)
                setStyle(style)
            } else if (description.length > NOTIFICATION_BIG_STYLE_MIN_LEN) {
                val style = NotificationCompat.BigTextStyle()
                style.bigText(renderedDescription)
                style.setBigContentTitle(renderedTitle)
                setStyle(style)
            }

            val titleAndDesp = if (sweetCard != null) {
                arrayOf(title, description)
            } else {
                determineTitleAndDespByDIP(context, metaInfo)
            }
            // Stock 7.4.67-C sweetnotification.e renders alert, left, and right through its
            // SweetTagHandler. The first standard fallback rendered only the joined body and left
            // alert markup visible, so use the rendered title for style 5 as well.
            setContentTitle(if (sweetCard != null) renderedTitle else titleAndDesp[0])
            setContentText(SweetTagHandler.renderFtHtmlIfNeeded(titleAndDesp[1]))

            val smallIconId = MIPushNotificationViewSupport.getIdForSmallIcon(context, packageName)
            if (smallIconId != 0) {
                setSmallIcon(smallIconId)
            }
        }
    }

    fun messagingStyleNotificationBuilder(
        context: Context,
        container: XmPushActionContainer,
        notificationId: Int,
        message: NotificationCompat.MessagingStyle.Message,
        pkgCtx: Context,
        userId: Int = resolveTargetUserId(context, container.packageName),
    ): NotificationCompat.Builder {
        val packageName = MIPushNotificationHelper.getTargetPackage(container)
        MagiskOtel.event(
            name = "push.event",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "app",
                "stage" to "style_path",
                "reason" to "messaging",
                "target_package" to packageName.orEmpty(),
            ),
            statusOk = true,
        )
        val metaInfo = container.metaInfo
        val conversation = getConversationFor(context, metaInfo, packageName)
        val groupConversation = isGroupConversation(metaInfo)
        val messages = collectConversationMessages(packageName, notificationId, metaInfo, message, userId)
        return createMessageStyleNotificationBuilder(
            context,
            container,
            messages,
            pkgCtx,
            packageName,
            conversation,
            getMessagingUser(pkgCtx, packageName),
            groupConversation
        )
    }

    fun createMessage(
        context: Context,
        container: XmPushActionContainer,
        pkgCtx: Context
    ): NotificationCompat.MessagingStyle.Message? {
        val metaInfo = container.metaInfo
        val custom = XMPushUtils.getConfiguration(metaInfo)
        val senderMessage = custom.conversationMessage(null) ?: return null
        return createMessage(context, pkgCtx, container.packageName, metaInfo, senderMessage)
    }

    fun getSdkIntentForMessagingStyle(
        context: Context,
        container: XmPushActionContainer,
        packageName: String
    ) = MyMIPushNotificationIntentSupport.getSdkIntent(context, container)
        ?: context.packageManager.getLaunchIntentForPackage(packageName)

    private fun getBigPic(
        context: Context,
        metaInfo: PushMetaInfo,
        preferredUri: String? = null,
    ): Bitmap? {
        val configuration = XMPushUtils.getConfiguration(metaInfo)
        val bigPicUri = preferredUri ?: configuration.notificationBigPicUri(null)
        return Global.iconCache().getBitmap(
            context,
            bigPicUri,
            object : io.github.magisk317.mipush.common.cache.IconCache.Converter<String, Bitmap> {
                override fun convert(ctx: Context, b: String): Bitmap {
                    return getBitmapFromUri(ctx, b, 1024 * 1024)
                        ?: throw IllegalArgumentException("Failed to load bitmap from $b")
                }
            }
        )
    }

    fun clearConversationHistory(
        packageName: String,
        notificationId: Int,
        userId: Int = currentUserId(),
    ) {
        synchronized(conversationHistories) {
            val iterator = conversationHistories.keys.iterator()
            var removed = 0
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (
                    key.userId == userId &&
                    key.packageName == packageName &&
                    key.notificationId == notificationId
                ) {
                    iterator.remove()
                    removed++
                }
            }
            if (removed > 0) {
                logD("clear conversation history pkg=$packageName id=$notificationId removed=$removed")
            }
        }
    }

    fun clearConversationHistories(packageName: String, userId: Int = currentUserId()) {
        synchronized(conversationHistories) {
            conversationHistories.keys.removeIf {
                it.userId == userId && it.packageName == packageName
            }
        }
    }

    private fun collectConversationMessages(
        packageName: String,
        notificationId: Int,
        metaInfo: PushMetaInfo,
        message: NotificationCompat.MessagingStyle.Message,
        userId: Int,
    ): List<NotificationCompat.MessagingStyle.Message> {
        val key = conversationKey(packageName, notificationId, metaInfo, userId)
        val messageKey = messageKey(metaInfo, message)
        val fallbackMessageKey = messageKey(null, message)
        val now = SystemClock.elapsedRealtime()
        var seededCount = 0
        var appended = false
        val messages = synchronized(conversationHistories) {
            var history = conversationHistories[key]
            if (history != null && now - history.updatedElapsedMs > CONVERSATION_HISTORY_TTL_MS) {
                conversationHistories.remove(key)
                history = null
            }
            if (history == null) {
                val seededMessages = activeStyleMessages(packageName, notificationId, userId)
                    .distinctBy { messageKey(null, it) }
                    .takeLast(MAX_MESSAGES_PER_CONVERSATION)
                    .map { CachedMessage(messageKey(null, it), it) }
                    .toMutableList()
                seededCount = seededMessages.size
                history = ConversationHistory(seededMessages, now)
                conversationHistories[key] = history
            }
            if (history.messages.none { it.key == messageKey || it.key == fallbackMessageKey }) {
                history.messages.add(CachedMessage(messageKey, message))
                appended = true
            }
            while (history.messages.size > MAX_MESSAGES_PER_CONVERSATION) {
                history.messages.removeAt(0)
            }
            history.updatedElapsedMs = now
            history.messages.map { it.message }
        }
        logD(
            "conversation history pkg=$packageName id=$notificationId conversation=${key.conversationId} " +
                "messages=${messages.size} seeded=$seededCount appended=$appended messageKey=$messageKey"
        )
        return messages
    }

    private fun conversationKey(
        packageName: String,
        notificationId: Int,
        metaInfo: PushMetaInfo,
        userId: Int,
    ): ConversationKey {
        val custom = XMPushUtils.getConfiguration(metaInfo)
        val conversationId = custom.conversationId(null)
            ?: if (metaInfo.isSetNotifyId()) metaInfo.notifyId.toString() else null
        return ConversationKey(userId, packageName, notificationId, conversationId)
    }

    private fun currentUserId(): Int = Utils.requireValidUserId(Utils.myUserId())

    private fun messageKey(
        metaInfo: PushMetaInfo?,
        message: NotificationCompat.MessagingStyle.Message
    ): String {
        val pushId = metaInfo?.id
        if (!pushId.isNullOrBlank()) {
            return "id:$pushId"
        }
        val person = message.person
        val sender = person?.key ?: person?.name?.toString().orEmpty()
        return "msg:${message.timestamp}:$sender:${message.text}"
    }

    private fun activeStyleMessages(
        packageName: String,
        notificationId: Int,
        userId: Int,
    ): List<NotificationCompat.MessagingStyle.Message> {
        try {
            val activeNotification = findActiveNotification(packageName, notificationId, userId)
            if (activeNotification != null) {
                val activeStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(activeNotification)
                if (activeStyle != null) {
                    return activeStyle.messages
                }
            }
        } catch (e: Exception) {
            logE("Failed to read active messaging notification", e)
        }
        return emptyList()
    }

    @Suppress("DEPRECATION")
    private fun findActiveNotification(
        packageName: String,
        notificationId: Int,
        userId: Int,
    ) =
        io.github.magisk317.mipush.notification.NotificationController.getNotificationManagerEx()
            .getActiveNotifications(packageName)
            ?.firstOrNull { it != null && it.id == notificationId && it.userId == userId }
            ?.notification

    private fun resolveTargetUserId(context: Context, packageName: String?): Int {
        require(!packageName.isNullOrBlank()) { "Unable to resolve target package user" }
        return runCatching {
            val uid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0),
                ).uid
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0).uid
            }
            require(uid >= 0) { "Invalid target package uid: $uid" }
            (uid.toLong() / 100_000L).toInt()
        }.getOrElse { error("Unable to resolve target package user for $packageName: ${it.message}") }
    }

    private fun createMessageStyleNotificationBuilder(
        context: Context,
        container: XmPushActionContainer,
        messages: List<NotificationCompat.MessagingStyle.Message>,
        pkgCtx: Context,
        packageName: String,
        conversation: PersonWithAvatar,
        messagingUser: Person,
        groupConversation: Boolean
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, "xmsf.default").apply {
            attachMessagingStyle(messages, conversation.person, messagingUser, groupConversation, this)
            conversation.avatar?.let { setLargeIcon(it) }
            addShortcutToEnableMessagingStyle(context, container, pkgCtx, packageName, conversation.person, this)
        }
    }

    private fun attachMessagingStyle(
        messages: List<NotificationCompat.MessagingStyle.Message>,
        conversation: Person,
        messagingUser: Person,
        groupConversation: Boolean,
        notificationBuilder: NotificationCompat.Builder
    ) {
        val style = NotificationCompat.MessagingStyle(messagingUser)
        if (groupConversation) {
            style.setConversationTitle(conversation.name)
        }
        style.setGroupConversation(groupConversation)
        messages.forEach { style.addMessage(it) }
        notificationBuilder.setStyle(style)
    }

    private fun addShortcutToEnableMessagingStyle(
        context: Context,
        container: XmPushActionContainer,
        pkgCtx: Context,
        packageName: String,
        group: Person,
        notificationBuilder: NotificationCompat.Builder
    ) {
        try {
            val key = group.key ?: group.name.toString()
            val intent = getSdkIntentForMessagingStyle(context, container, packageName) ?: return
            val shortcut = ShortcutInfoCompat.Builder(pkgCtx, key)
                .setIntent(intent)
                .setLongLived(true)
                .setShortLabel(group.name ?: key)
                .setIcon(group.icon)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(pkgCtx, shortcut)
            notificationBuilder.setShortcutInfo(shortcut)
        } catch (_: Throwable) {
        }
    }

    private fun createMessage(
        context: Context,
        pkgCtx: Context,
        packageName: String,
        metaInfo: PushMetaInfo,
        senderMessage: String
    ): NotificationCompat.MessagingStyle.Message {
        val atLeastP = pkgCtx.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.P
        val person = if (isGroupConversation(metaInfo) || atLeastP) {
            getPerson(context, metaInfo, packageName).person
        } else {
            null
        }
        return NotificationCompat.MessagingStyle.Message(senderMessage, metaInfo.messageTs, person)
    }

    private fun isGroupConversation(metaInfo: PushMetaInfo): Boolean {
        return XMPushUtils.getConfiguration(metaInfo).conversationTitle(null) != null
    }

    private fun getConversationFor(
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String
    ): PersonWithAvatar {
        val custom = XMPushUtils.getConfiguration(metaInfo)
        val conversation = custom.conversationTitle(null)
        val conversationId = custom.conversationId(null)
        val conversationIcon = selectConversationIconUri(
            primary = custom.conversationIcon(null),
            fallback = custom.notificationLargeIconUri(null),
        )
        val groupConversation = isGroupConversation(metaInfo)

        if (!groupConversation) {
            return getPerson(context, metaInfo, packageName)
        }

        val personBuilder = Person.Builder()
        if (conversation != null) {
            personBuilder.setName(conversation)
        } else if (personBuilder.build().name == null) {
            personBuilder.setName(metaInfo.title)
        }
        if (conversationId != null) {
            personBuilder.setKey(conversationId)
        }
        val largeIcon = getLargeIcon(context, metaInfo, conversationIcon)
            ?: if (shouldUseApplicationIconFallback(conversationIcon)) {
                getAppLogo(context, metaInfo, packageName)
            } else {
                null
            }
        if (largeIcon != null) {
            personBuilder.setIcon(IconCompat.createWithBitmap(largeIcon))
        }
        return PersonWithAvatar(personBuilder.build(), largeIcon)
    }

    private fun getPerson(
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String
    ): PersonWithAvatar {
        val custom = XMPushUtils.getConfiguration(metaInfo)
        val sender = custom.conversationSender(null)
        val senderId = custom.conversationSenderId(null)
        val senderIcon = selectConversationIconUri(
            primary = custom.conversationSenderIcon(null),
            fallback = custom.notificationLargeIconUri(null),
        )

        val personBuilder = Person.Builder().setName(sender)
        personBuilder.setImportant(custom.conversationImportant(false))
        if (senderId != null) {
            personBuilder.setKey(senderId)
        }
        val largeIcon = getLargeIcon(context, metaInfo, senderIcon)
            ?: if (shouldUseApplicationIconFallback(senderIcon)) {
                getAppLogo(context, metaInfo, packageName)
            } else {
                null
            }
        if (largeIcon != null) {
            personBuilder.setIcon(IconCompat.createWithBitmap(largeIcon))
        }
        return PersonWithAvatar(personBuilder.build(), largeIcon)
    }

    private fun getAppLogo(context: Context, metaInfo: PushMetaInfo, packageName: String): Bitmap? {
        return runCatching {
            val logo = Global.iconCache().getRawIconBitmap(context, packageName)
                ?.takeUnless { it.isRecycled }
                ?: return@runCatching null
            roundLargeIconIfConfigured(metaInfo, logo)
        }.getOrNull()
    }

    /** A configured URI remains authoritative even when its payload cannot be decoded. */
    internal fun selectConversationIconUri(primary: String?, fallback: String?): String? =
        primary?.takeIf { it.isNotBlank() } ?: fallback?.takeIf { it.isNotBlank() }

    internal fun shouldUseApplicationIconFallback(iconUri: String?): Boolean = iconUri.isNullOrBlank()

    private fun getMessagingUser(pkgCtx: Context, packageName: String): Person {
        val label = runCatching {
            pkgCtx.packageManager.getApplicationLabel(pkgCtx.applicationInfo).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: packageName
        return Person.Builder().setName(label).build()
    }

    private fun determineTitleAndDespByDIP(
        context: Context,
        pushMetaInfo: PushMetaInfo
    ): Array<String> {
        return try {
            MIPushNotificationViewSupport.determineTitleAndDespByDIP(context, pushMetaInfo).let { values ->
                arrayOf(values.getOrNull(0).orEmpty(), values.getOrNull(1).orEmpty())
            }
        } catch (e: Exception) {
            logE("Error in determineTitleAndDespByDIP", e)
            arrayOf(pushMetaInfo.title.orEmpty(), pushMetaInfo.description.orEmpty())
        }
    }
}
