package io.github.magisk317.mipush.push.hook

import android.content.Intent
import io.github.magisk317.mipush.push.pipeline.MockMessageRegistry
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.core.DuplicateMessagePolicy
import com.xiaomi.push.service.XMPushServiceCore
import com.xiaomi.push.service.clientReport.ReportConstants
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer

/**
 * Explicit replacement for historical AspectJ join points.
 *
 * The old project used MethodHooker/Aspect weaving. After de-AOP, call sites
 * forward into this bridge so behavior stays discoverable and testable.
 */
object ExplicitHookBridge {
    private val duplicateMessagePolicy = DuplicateMessagePolicy()

    @JvmStatic
    fun shouldSendBroadcast(
        pushService: XMPushServiceCore,
        packageName: String,
        container: XmPushActionContainer,
        metaInfo: PushMetaInfo
    ): Boolean {
        HookTrace.mark("MIPushEventProcessor.shouldSendBroadcast")
        val decision = BroadcastDecision.shouldSendBroadcast(pushService, packageName, container, metaInfo)
        AspectLogCompat.logShouldSendBroadcast(pushService, packageName, metaInfo, decision)
        return decision
    }

    @JvmStatic
    fun onBuildIntent(intent: Intent?, source: String) {
        HookTrace.mark("MIPushEventProcessor.buildIntent")
        AspectLogCompat.logBuildIntent(intent, source)
        intent?.removeExtra("messageId")
        intent?.removeExtra(ReportConstants.EVENT_MESSAGE_TYPE)
    }

    @JvmStatic
    fun onIntentAvailabilityChecked(intent: Intent?, available: Boolean, source: String) {
        HookTrace.mark("MIPushEventProcessor.isIntentAvailable")
        AspectLogCompat.logIntentAvailability(intent, available, source)
    }

    @JvmStatic
    fun isDuplicateMessage(
        pushService: XMPushServiceCore,
        packageName: String,
        messageId: String
    ): Boolean {
        HookTrace.mark("MiPushMessageDuplicate.isDuplicateMessage")
        val isMockReplay = MockMessageRegistry.isMarked(packageName, messageId)
        if (isMockReplay) {
            // Mock replay should not be treated as duplicate; let message flow continue.
            AspectLogCompat.logDuplicateCheck(packageName, messageId, false)
            return false
        }
        val scope = "${Utils.requireValidUserId(Utils.myUserId())}:$packageName"
        val duplicated = duplicateMessagePolicy.checkAndMark(
            scope = scope,
            messageId = messageId,
            nowMs = System.currentTimeMillis(),
        )
        AspectLogCompat.logDuplicateCheck(packageName, messageId, duplicated)
        return duplicated
    }
}
