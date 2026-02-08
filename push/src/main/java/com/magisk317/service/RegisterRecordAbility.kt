package com.magisk317.service

import android.content.Intent

class RegisterRecordAbility(
    private val registerRecorder: RegisterRecorder
) : XMPushServiceListener {
    override fun start(intent: Intent) {
        registerRecorder.recordRegisterRequest(intent)
    }
}
