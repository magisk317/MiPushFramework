package com.magisk317.service

import android.content.Context
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.xiaomi.mipush.sdk.PushContainerHelper
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import top.trumeet.common.utils.Utils

class RegistrationRecorder {
    private val logger: Logger = XLog.tag(TAG).build()
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
                XLog.tag(TAG).build().e("cannot save RegSec", e)
                null
            }
        }
    }
}
