package com.xiaomi.clientreport.util
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import android.content.SharedPreferences

class SPManager private constructor(private val mContext: Context) {

    companion object {
        @Volatile
        private var sInstance: SPManager? = null

        fun getInstance(context: Context): SPManager {
            return sInstance ?: synchronized(SPManager::class.java) {
                sInstance ?: SPManager(context).also { sInstance = it }
            }
        }
    }

    fun getLongValue(str: String, str2: String, j: Long): Long {
        return try {
            mContext.getSharedPreferences(str, 4).getLong(str2, j)
        } catch (th: Throwable) {
            j
        }
    }

    fun getStringValue(str: String, str2: String, str3: String): String? {
        return try {
            mContext.getSharedPreferences(str, 4).getString(str2, str3)
        } catch (th: Throwable) {
            str3
        }
    }

    fun setLongValue(str: String, str2: String, j: Long) {
        synchronized(this) {
            val editorEdit: SharedPreferences.Editor =
                mContext.getSharedPreferences(str, 4).edit()
            editorEdit.putLong(str2, j)
            editorEdit.commit()
        }
    }

    fun setStringnValue(str: String, str2: String, str3: String) {
        synchronized(this) {
            val editorEdit: SharedPreferences.Editor =
                mContext.getSharedPreferences(str, 4).edit()
            editorEdit.putString(str2, str3)
            editorEdit.commit()
        }
    }
}
