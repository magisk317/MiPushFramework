package com.xiaomi.mipush.sdk

import com.xiaomi.xmpush.thrift.ConfigKey
import java.util.HashMap

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/AssemblePushInfoHelper.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
abstract class AssemblePushInfoHelper {
    class ManageClassInfo(
        @JvmField var className: String?,
        @JvmField var methodName: String?
    )

    companion object {
        protected const val COS_PUSH_MANAGER_CLASS_NAME = "com.xiaomi.assemble.control.COSPushManager"
        protected const val COS_PUSH_SINGLE_METHOD_NAME = "newInstance"
        protected const val FCM_PUSH_MANAGER_CLASS_NAME = "com.xiaomi.assemble.control.FCMPushManager"
        protected const val FCM_PUSH_SINGLE_METHOD_NAME = "newInstance"
        protected const val FTOS_PUSH_MANAGER_CLASS_NAME = "com.xiaomi.assemble.control.FTOSPushManager"
        protected const val FTOS_PUSH_SINGLE_METHOD_NAME = "newInstance"
        protected const val HMS_PUSH_MANAGER_CLASS_NAME = "com.xiaomi.assemble.control.HmsPushManager"
        protected const val HMS_PUSH_SINGLE_METHOD_NAME = "newInstance"

        private val mHashMaps = HashMap<AssemblePush, ManageClassInfo>()

        init {
            add(AssemblePush.ASSEMBLE_PUSH_HUAWEI, ManageClassInfo(HMS_PUSH_MANAGER_CLASS_NAME, HMS_PUSH_SINGLE_METHOD_NAME))
            add(AssemblePush.ASSEMBLE_PUSH_FCM, ManageClassInfo(FCM_PUSH_MANAGER_CLASS_NAME, FCM_PUSH_SINGLE_METHOD_NAME))
            add(AssemblePush.ASSEMBLE_PUSH_COS, ManageClassInfo(COS_PUSH_MANAGER_CLASS_NAME, COS_PUSH_SINGLE_METHOD_NAME))
            add(AssemblePush.ASSEMBLE_PUSH_FTOS, ManageClassInfo(FTOS_PUSH_MANAGER_CLASS_NAME, FTOS_PUSH_SINGLE_METHOD_NAME))
        }

        private fun add(assemblePush: AssemblePush, manageClassInfo: ManageClassInfo?) {
            if (manageClassInfo != null) {
                mHashMaps[assemblePush] = manageClassInfo
            }
        }

        @JvmStatic
        fun getConfigKeyByType(assemblePush: AssemblePush?): ConfigKey {
            return ConfigKey.AggregatePushSwitch
        }

        @JvmStatic
        fun getManageClassInfoByType(assemblePush: AssemblePush?): ManageClassInfo? {
            return mHashMaps[assemblePush]
        }

        @JvmStatic
        fun getRetryType(assemblePush: AssemblePush?): RetryType? {
            return when (assemblePush) {
                AssemblePush.ASSEMBLE_PUSH_HUAWEI -> RetryType.UPLOAD_HUAWEI_TOKEN
                AssemblePush.ASSEMBLE_PUSH_FCM -> RetryType.UPLOAD_FCM_TOKEN
                AssemblePush.ASSEMBLE_PUSH_COS -> RetryType.UPLOAD_COS_TOKEN
                AssemblePush.ASSEMBLE_PUSH_FTOS -> RetryType.UPLOAD_FTOS_TOKEN
                else -> null
            }
        }
    }
}
