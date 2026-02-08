package com.magisk317

import android.content.Intent

fun interface MessageListener {
    fun onReceive(intent: Intent)
}
