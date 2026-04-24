package io.github.magisk317.mipush.service

class ForegroundAbility(val foregroundHelper: ForegroundHelper) : XMPushServiceListener {
    override fun created() {
        foregroundHelper.startForeground()
    }

    override fun destroy() {
        foregroundHelper.stopForegroundNotification()
    }
}
