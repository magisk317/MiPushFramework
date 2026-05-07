package io.github.magisk317.mipush

import android.content.Intent

fun interface MessageListener {
    fun onReceive(intent: Intent)
}
