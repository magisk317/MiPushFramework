package com.xiaomi.mipush.sdk.stat.db

object MyLog {
    private const val TAG = "PUSH_STAT"

    fun i(msg: String) {
        android.util.Log.i(TAG, msg)
    }

    fun v(msg: String) {
        android.util.Log.v(TAG, msg)
    }

    fun e(msg: String) {
        android.util.Log.e(TAG, msg)
    }

    fun e(e: Throwable) {
        android.util.Log.e(TAG, e.message ?: "Unknown error", e)
    }

    fun e(msg: String, e: Throwable) {
        android.util.Log.e(TAG, msg, e)
    }

    fun w(msg: String) {
        android.util.Log.w(TAG, msg)
    }
}
