package io.github.magisk317.mipush.service

import android.content.Context
import io.github.aakira.napier.Napier
import com.xiaomi.mipush.sdk.PushContainerHelper
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import io.github.magisk317.mipush.common.utils.Utils

class RegistrationRecorder {
    private lateinit var context: Context

    fun initContext(context: Context) {
        this.context = context
    }

    fun recordRegSec(container: XmPushActionContainer?) {
        if (container == null || container.isRequest || container.action != ActionType.Registration) {
            return
        }
        val regSec = getRegSec(context, container)
        if (regSec != null) {
            Utils.setRegSec(container.packageName, regSec)
        }
    }

    companion object {
        private val TAG = RegistrationRecorder::class.java.simpleName

        @JvmStatic
        fun getRegSec(pushService: Context, container: XmPushActionContainer): String? {
            return try {
                val result = PushContainerHelper.getResponseMessageBodyFromContainer(
                    pushService,
                    container
                ) as XmPushActionRegistrationResult
                result.regSecret
            } catch (e: Throwable) {
                Napier.e("cannot save RegSec", e, tag = TAG)
                null
            }
        }
    }
}
