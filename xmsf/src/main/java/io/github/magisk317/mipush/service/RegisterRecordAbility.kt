package io.github.magisk317.mipush.service

import android.content.Intent

class RegisterRecordAbility(
    private val registerRecorder: RegisterRecorder
) : XMPushServiceListener {
    override fun start(intent: Intent) {
        registerRecorder.recordRegisterRequest(intent)
    }
}
