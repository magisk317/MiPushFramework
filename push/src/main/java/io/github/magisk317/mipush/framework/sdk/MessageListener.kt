package io.github.magisk317.mipush.framework.sdk

import android.content.Intent

fun interface MessageListener {
    fun onReceive(intent: Intent)
}
