package com.xiaomi.mipush.sdk

import com.xiaomi.channel.commonutils.logger.MyLog

import android.content.Context
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.xmpush.thrift.ConfigKey

class AssemblePushCollectionsManager private constructor(context: Context) : AbstractPushManager {
    companion object {
        @Volatile
        private var sInstance: AssemblePushCollectionsManager? = null

        @JvmStatic
        fun getInstance(context: Context): AssemblePushCollectionsManager {
            return sInstance ?: synchronized(this) {
                sInstance ?: AssemblePushCollectionsManager(context).also { sInstance = it }
            }
        }
    }

    private val mContext: Context = context.applicationContext
    private var mConfiguration: PushConfiguration? = null
    private val mManagers: MutableMap<AssemblePush, AbstractPushManager> = HashMap()
    private var oldOCValue = false

    private fun initAssemblePushManager() {
        val pushConfiguration = mConfiguration ?: return
        if (pushConfiguration.openHmsPush) {
            MyLog.w("ASSEMBLE_PUSH :  HW user switch : ${pushConfiguration.openHmsPush} HW online switch : ${AssemblePushHelper.isOpenAssemblePushOnlineSwitch(mContext, AssemblePush.ASSEMBLE_PUSH_HUAWEI)} HW isSupport : ${PhoneBrand.HUAWEI == AssemblePushUtils.getPhoneBrand(mContext)}")
        }
        if (pushConfiguration.openHmsPush && AssemblePushHelper.isOpenAssemblePushOnlineSwitch(mContext, AssemblePush.ASSEMBLE_PUSH_HUAWEI) && PhoneBrand.HUAWEI == AssemblePushUtils.getPhoneBrand(mContext)) {
            if (!contain(AssemblePush.ASSEMBLE_PUSH_HUAWEI)) {
                PushManagerFactory.getManager(mContext, AssemblePush.ASSEMBLE_PUSH_HUAWEI)?.let { addManager(AssemblePush.ASSEMBLE_PUSH_HUAWEI, it) }
            }
            MyLog.v("hw manager add to list")
        } else if (contain(AssemblePush.ASSEMBLE_PUSH_HUAWEI)) {
            getManager(AssemblePush.ASSEMBLE_PUSH_HUAWEI)?.also { manager ->
                removeManager(AssemblePush.ASSEMBLE_PUSH_HUAWEI)
                manager.unregister()
            }
        }
        if (pushConfiguration.openFCMPush) {
            MyLog.w("ASSEMBLE_PUSH :  FCM user switch : ${pushConfiguration.openFCMPush} FCM online switch : ${AssemblePushHelper.isOpenAssemblePushOnlineSwitch(mContext, AssemblePush.ASSEMBLE_PUSH_FCM)} FCM isSupport : ${AssemblePushUtils.isGoogleServiceSatisfied(mContext)}")
        }
        if (pushConfiguration.openFCMPush && AssemblePushHelper.isOpenAssemblePushOnlineSwitch(mContext, AssemblePush.ASSEMBLE_PUSH_FCM) && AssemblePushUtils.isGoogleServiceSatisfied(mContext)) {
            if (!contain(AssemblePush.ASSEMBLE_PUSH_FCM)) {
                PushManagerFactory.getManager(mContext, AssemblePush.ASSEMBLE_PUSH_FCM)?.let { addManager(AssemblePush.ASSEMBLE_PUSH_FCM, it) }
            }
            MyLog.v("fcm manager add to list")
        } else if (contain(AssemblePush.ASSEMBLE_PUSH_FCM)) {
            getManager(AssemblePush.ASSEMBLE_PUSH_FCM)?.also { manager ->
                removeManager(AssemblePush.ASSEMBLE_PUSH_FCM)
                manager.unregister()
            }
        }
        if (pushConfiguration.openCOSPush) {
            MyLog.w("ASSEMBLE_PUSH :  COS user switch : ${pushConfiguration.openCOSPush} COS online switch : ${AssemblePushHelper.isOpenAssemblePushOnlineSwitch(mContext, AssemblePush.ASSEMBLE_PUSH_COS)} COS isSupport : ${AssemblePushUtils.isColorOSPushSupport(mContext)}")
        }
        if (pushConfiguration.openCOSPush && AssemblePushHelper.isOpenAssemblePushOnlineSwitch(mContext, AssemblePush.ASSEMBLE_PUSH_COS) && AssemblePushUtils.isColorOSPushSupport(mContext)) {
            PushManagerFactory.getManager(mContext, AssemblePush.ASSEMBLE_PUSH_COS)?.let { addManager(AssemblePush.ASSEMBLE_PUSH_COS, it) }
        } else if (contain(AssemblePush.ASSEMBLE_PUSH_COS)) {
            getManager(AssemblePush.ASSEMBLE_PUSH_COS)?.also { manager ->
                removeManager(AssemblePush.ASSEMBLE_PUSH_COS)
                manager.unregister()
            }
        }
        if (pushConfiguration.openFTOSPush && AssemblePushHelper.isOpenAssemblePushOnlineSwitch(mContext, AssemblePush.ASSEMBLE_PUSH_FTOS) && AssemblePushUtils.isFunTouchOSPushSupport(mContext)) {
            PushManagerFactory.getManager(mContext, AssemblePush.ASSEMBLE_PUSH_FTOS)?.let { addManager(AssemblePush.ASSEMBLE_PUSH_FTOS, it) }
        } else if (contain(AssemblePush.ASSEMBLE_PUSH_FTOS)) {
            getManager(AssemblePush.ASSEMBLE_PUSH_FTOS)?.also { manager ->
                removeManager(AssemblePush.ASSEMBLE_PUSH_FTOS)
                manager.unregister()
            }
        }
    }

    fun addManager(assemblePush: AssemblePush, abstractPushManager: AbstractPushManager) {
        if (abstractPushManager != null) {
            mManagers.remove(assemblePush)
            mManagers[assemblePush] = abstractPushManager
        }
    }

    fun contain(assemblePush: AssemblePush): Boolean = mManagers.containsKey(assemblePush)

    fun getManager(assemblePush: AssemblePush): AbstractPushManager? = mManagers[assemblePush]

    fun getUserSwitch(assemblePush: AssemblePush): Boolean {
        return when (assemblePush) {
            AssemblePush.ASSEMBLE_PUSH_HUAWEI -> mConfiguration?.openHmsPush ?: false
            AssemblePush.ASSEMBLE_PUSH_FCM -> mConfiguration?.openFCMPush ?: false
            AssemblePush.ASSEMBLE_PUSH_COS -> mConfiguration?.openCOSPush ?: false
            AssemblePush.ASSEMBLE_PUSH_FTOS -> mConfiguration?.openFTOSPush ?: false
        }
    }

    override fun register() {
        MyLog.w("ASSEMBLE_PUSH : assemble push register")
        if (mManagers.isEmpty()) {
            initAssemblePushManager()
        }
        if (mManagers.isNotEmpty()) {
            for (abstractPushManager in mManagers.values) {
                abstractPushManager?.register()
            }
            AssemblePushHelper.checkAssemblePushStatus(mContext)
        }
    }

    fun removeManager(assemblePush: AssemblePush) {
        mManagers.remove(assemblePush)
    }

    fun setConfiguration(pushConfiguration: PushConfiguration) {
        mConfiguration = pushConfiguration
        oldOCValue = OnlineConfig.getInstance(mContext).getBooleanValue(ConfigKey.AggregatePushSwitch.value, true)
        if (pushConfiguration.openHmsPush || pushConfiguration.openFCMPush || pushConfiguration.openCOSPush) {
            OnlineConfig.getInstance(mContext).addOCUpdateCallbacks(object : OnlineConfig.OCUpdateCallback(101, "assemblePush") {
                override fun onCallback() {
                    val booleanValue = OnlineConfig.getInstance(mContext).getBooleanValue(ConfigKey.AggregatePushSwitch.value, true)
                    if (this@AssemblePushCollectionsManager.oldOCValue != booleanValue) {
                        this@AssemblePushCollectionsManager.oldOCValue = booleanValue
                        AssemblePushHelper.registerAssemblePush(this@AssemblePushCollectionsManager.mContext)
                    }
                }
            })
        }
    }

    override fun unregister() {
        MyLog.w("ASSEMBLE_PUSH : assemble push unregister")
        for (abstractPushManager in mManagers.values) {
            abstractPushManager?.unregister()
        }
        mManagers.clear()
    }
}
