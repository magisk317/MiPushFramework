package com.nihility.service

class ForegroundAbility(val foregroundHelper: ForegroundHelper) : XMPushServiceListener {
    override fun created() {
        foregroundHelper.startForeground()
    }

    override fun destroy() {
        foregroundHelper.stopForegroundNotification()
    }
}
