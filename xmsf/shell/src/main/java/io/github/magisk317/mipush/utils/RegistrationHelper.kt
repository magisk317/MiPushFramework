package io.github.magisk317.mipush.utils

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.content.Intent
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.common.compat.PackageManagerCompatBridge
import com.xiaomi.push.sdk.MyPushMessageHandler
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.common.Constants
import android.content.pm.PackageManager
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import io.github.magisk317.mipush.platform.support.AppRootAccessFacade
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.mipush.runtime.android.AndroidPushRuntime
import io.github.magisk317.mipush.runtime.core.registration.ForceRegisterPlan
import io.github.magisk317.mipush.runtime.core.registration.RegistrationComponentInfo
import io.github.magisk317.mipush.runtime.core.registration.RegistrationComponentPolicy
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.kmp.EventRowResultType
import io.github.magisk317.mipush.runtime.store.event.type.RegistrationType
import io.github.magisk317.mipush.service.runtime.RegistrationRecordDeduper
import kotlinx.coroutines.runBlocking

class RegistrationHelper(
    private val context: Context,
    private val packageName: String
) {
    fun removeMiPushData(): Boolean {
        val result = AppRootAccessFacade.runRootCommand(
            String.format(
                "rm -rf $(ls -1" +
                    " /data/user/0/%s/shared_prefs/mipush*.xml" +
                    " /data_mirror/data_ce/null/0/%s/shared_prefs/mipush*.xml" +
                    " /data/user/0/%s/files/keva/repo/mipush" +
                    " /data/user/0/%s/files/keva/repo/mipush*" +
                    " /data_mirror/data_ce/null/0/%s/files/keva/repo/mipush" +
                    " /data_mirror/data_ce/null/0/%s/files/keva/repo/mipush*" +
                    " 2> /dev/null)",
                packageName,
                packageName,
                packageName,
                packageName,
                packageName,
                packageName
            )
        )
        logI("remove mipush data for $packageName success=${result.isSuccess}")
        return result.isSuccess
    }

    fun deleteRegistrationInfoAndRetryForceRegister() {
        removeMiPushData()
        MyPushMessageHandler.launchApp(context, createForceRegisterMessage(packageName))
        tryForceRegister(packageName)
    }

    companion object {
        @JvmStatic
        fun classifyForceRegisterPlan(
            packageName: String,
            serviceNames: Set<String>,
            receiverNames: Set<String>
        ): ForceRegisterPlan = RegistrationComponentPolicy.classifyForceRegisterPlan(
            packageName = packageName,
            serviceNames = serviceNames,
            receiverNames = receiverNames
        )

        @JvmStatic
        fun classifyDisplayTypeReason(
            serviceNames: Set<String>,
            receiverNames: Set<String>
        ): String = RegistrationComponentPolicy.classifyDisplayTypeReason(
            serviceNames = serviceNames,
            receiverNames = receiverNames
        )

        internal fun resolveForceRegisterPlan(
            packageName: String,
            serviceInfos: Set<RegistrationComponentInfo>,
            receiverInfos: Set<RegistrationComponentInfo>,
            sourcePackageName: String
        ): ForceRegisterPlan = RegistrationComponentPolicy.resolveForceRegisterPlan(
            packageName = packageName,
            serviceInfos = serviceInfos,
            receiverInfos = receiverInfos,
            sourcePackageName = sourcePackageName
        )

        @JvmStatic
        fun inspectForceRegisterPlan(packageName: String): ForceRegisterPlan {
            val app = Utils.getApplication()
                ?: return ForceRegisterPlan(packageName, false, "application_unavailable", emptySet(), emptySet(), emptySet())
            val packageInfo = try {
                PackageManagerCompatBridge.getPackageInfo(
                    app.packageManager,
                    packageName,
                    PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
                )
            } catch (_: PackageManager.NameNotFoundException) {
                null
            } ?: return ForceRegisterPlan(packageName, false, "package_not_found", emptySet(), emptySet(), emptySet())
            val serviceInfos = packageInfo.services
                ?.mapNotNull { info ->
                    info.name?.let {
                        RegistrationComponentInfo(
                            name = it,
                            enabled = info.enabled,
                            exported = info.exported
                        )
                    }
                }
                ?.toSet()
                ?: emptySet()
            val receiverInfos = packageInfo.receivers
                ?.mapNotNull { info ->
                    info.name?.let {
                        RegistrationComponentInfo(
                            name = it,
                            enabled = info.enabled,
                            exported = info.exported
                        )
                    }
                }
                ?.toSet()
                ?: emptySet()
            return resolveForceRegisterPlan(
                packageName = packageName,
                serviceInfos = serviceInfos,
                receiverInfos = receiverInfos,
                sourcePackageName = app.packageName
            )
        }

        @JvmStatic
        fun tryForceRegisterFallback(packageName: String): Boolean {
            val plan = inspectForceRegisterPlan(packageName)
            if (!plan.supportsReceiverFallback && !plan.supportsServiceDispatch && plan.bridgeCandidates.isEmpty()) {
                logW("skip force register fallback for $packageName: ${plan.summary()}")
                emitHelperRegister(
                    result = "skip",
                    reason = "unsupported_fallback",
                    packageName = packageName,
                    stage = "force_helper_fallback",
                )
                return false
            }
            val msgBytes = runCatching {
                XMPushUtils.packToBytes(createForceRegisterMessage(packageName))
            }.getOrNull() ?: run {
                emitHelperRegister(
                    result = "error",
                    reason = "pack_failed",
                    packageName = packageName,
                    stage = "force_helper_fallback",
                    statusOk = false,
                )
                return false
            }
            
            val dispatched = XMPushUtils.dispatchToApplication(Utils.getApplication() ?: return false, packageName, msgBytes)
            if (dispatched) {
                AndroidPushRuntime.observeRegistrationRequest(
                    packageName,
                    "RegistrationHelper.tryForceRegisterFallback",
                    "force_trigger_fallback",
                    androidUserId = Utils.requireValidUserId(Utils.myUserId()),
                )
                RegistrationRecordDeduper.markRecorded(packageName)
                runBlocking {
                    EventDb.insertEventAsync(EventRowResultType.OK, RegistrationType("force_trigger_fallback", packageName, null))
                }
                logI("force register fallback for $packageName dispatched")
                emitHelperRegister(
                    result = "ok",
                    reason = "force_trigger_fallback",
                    packageName = packageName,
                    stage = "force_helper_fallback",
                )
            } else {
                logW("force register fallback for $packageName failed")
                emitHelperRegister(
                    result = "error",
                    reason = "dispatch_failed_fallback",
                    packageName = packageName,
                    stage = "force_helper_fallback",
                    statusOk = false,
                )
            }
            return dispatched
        }

        @JvmStatic
        fun tryForceRegister(packageName: String): Boolean {
            val app = Utils.getApplication() ?: return false
            val plan = inspectForceRegisterPlan(packageName)
            if (!plan.supportsServiceDispatch) {
                emitHelperRegister(
                    result = "skip",
                    reason = "unsupported_service",
                    packageName = packageName,
                    stage = "force_helper",
                )
                throw UnsupportedOperationException("force register unsupported for $packageName: ${plan.summary()}")
            }

            val container = createForceRegisterMessage(packageName)
            val msgBytes = XMPushUtils.packToBytes(container)
            
            val dispatched = XMPushUtils.dispatchToApplication(app, packageName, msgBytes)
            if (dispatched) {
                AndroidPushRuntime.observeRegistrationRequest(
                    packageName,
                    "RegistrationHelper.tryForceRegister",
                    "force_trigger",
                    androidUserId = Utils.requireValidUserId(Utils.myUserId()),
                )
                RegistrationRecordDeduper.markRecorded(packageName)
                runBlocking {
                    EventDb.insertEventAsync(EventRowResultType.OK, RegistrationType("force_trigger", packageName, null))
                }
                logI("force register for $packageName dispatched")
                emitHelperRegister(
                    result = "ok",
                    reason = "force_trigger",
                    packageName = packageName,
                    stage = "force_helper",
                )
            } else {
                logW("force register for $packageName failed to dispatch")
                emitHelperRegister(
                    result = "error",
                    reason = "dispatch_failed",
                    packageName = packageName,
                    stage = "force_helper",
                    statusOk = false,
                )
            }
            return dispatched
        }


        private fun emitHelperRegister(
            result: String,
            reason: String,
            packageName: String,
            stage: String,
            statusOk: Boolean = true,
        ) {
            MagiskOtel.event(
                name = "push.register",
                attributes = mapOf(
                    "result" to result,
                    "duration_ms" to "0",
                    "process" to "xmsf",
                    "stage" to stage,
                    "reason" to reason,
                    "target_package" to packageName,
                ),
                statusOk = statusOk,
            )
        }

        @JvmStatic
        fun createForceRegisterMessage(packageName: String): XmPushActionContainer {
            val id = "fake_expired_${packageName}_${System.currentTimeMillis()}"
            val regIdExpiredNotification = XmPushActionNotification().apply {
                type = NotificationType.RegIdExpired.value
                setId(id)
            }
            val metaInfo = PushMetaInfo().apply { setId(id) }
            val regIdExpiredContainer = XMPushUtils.packToContainer(regIdExpiredNotification, packageName)
            regIdExpiredContainer.metaInfo = metaInfo
            return regIdExpiredContainer
        }
    }
}
