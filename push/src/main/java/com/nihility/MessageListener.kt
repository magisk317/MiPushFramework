package com.nihility

import android.content.Intent

fun interface MessageListener {
    fun onReceive(intent: Intent)
}
