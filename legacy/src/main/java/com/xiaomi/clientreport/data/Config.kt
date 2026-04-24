package com.xiaomi.clientreport.data
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import android.text.TextUtils
import com.xiaomi.clientreport.util.ClientReportUtil

class Config private constructor() {

    companion object {
        const val DEFAULT_EVENT_ENCRYPTED: Boolean = true
        const val DEFAULT_EVENT_UPLOAD_FREQUENCY: Long = 86400
        const val DEFAULT_EVENT_UPLOAD_SWITCH_OPEN: Boolean = false
        const val DEFAULT_MAX_FILE_LENGTH: Long = 1048576
        const val DEFAULT_PERF_UPLOAD_FREQUENCY: Long = 86400
        const val DEFAULT_PERF_UPLOAD_SWITCH_OPEN: Boolean = false

        @JvmStatic
        fun defaultConfig(context: Context): Config {
            return Builder()
                .setEventEncrypted(true)
                .setAESKey(ClientReportUtil.getEventKeyWithDefault(context))
                .setMaxFileLength(1048576L)
                .setEventUploadSwitchOpen(false)
                .setEventUploadFrequency(86400L)
                .setPerfUploadSwitchOpen(false)
                .setPerfUploadFrequency(86400L)
                .build(context)
        }

        @JvmStatic
        fun getBuilder(): Builder = Builder()
    }

    private var mAESKey: String? = null
    private var mEventEncrypted: Boolean = DEFAULT_EVENT_ENCRYPTED
    private var mEventUploadFrequency: Long = DEFAULT_EVENT_UPLOAD_FREQUENCY
    private var mEventUploadSwitchOpen: Boolean = DEFAULT_EVENT_UPLOAD_SWITCH_OPEN
    private var mMaxFileLength: Long = DEFAULT_MAX_FILE_LENGTH
    private var mPerfUploadFrequency: Long = DEFAULT_PERF_UPLOAD_FREQUENCY
    private var mPerfUploadSwitchOpen: Boolean = DEFAULT_PERF_UPLOAD_SWITCH_OPEN

    private constructor(context: Context, builder: Builder) : this() {
        mEventEncrypted = when (builder.mEventEncrypted) {
            0 -> false
            1 -> true
            else -> true
        }
        mAESKey = if (builder.mAESKey.isNullOrEmpty()) {
            ClientReportUtil.getEventKeyWithDefault(context)
        } else {
            builder.mAESKey
        }
        mMaxFileLength = if (builder.mMaxFileLength > -1) builder.mMaxFileLength else DEFAULT_MAX_FILE_LENGTH
        mEventUploadFrequency = if (builder.mEventUploadFrequency > -1) builder.mEventUploadFrequency else DEFAULT_EVENT_UPLOAD_FREQUENCY
        mPerfUploadFrequency = if (builder.mPerfUploadFrequency > -1) builder.mPerfUploadFrequency else DEFAULT_PERF_UPLOAD_FREQUENCY
        mEventUploadSwitchOpen = builder.mEventUploadSwitchOpen == 1
        mPerfUploadSwitchOpen = builder.mPerfUploadSwitchOpen == 1
    }

    val eventUploadFrequency: Long
        get() = mEventUploadFrequency

    val maxFileLength: Long
        get() = mMaxFileLength

    val perfUploadFrequency: Long
        get() = mPerfUploadFrequency

    val isEventEncrypted: Boolean
        get() = mEventEncrypted

    val isEventUploadSwitchOpen: Boolean
        get() = mEventUploadSwitchOpen

    val isPerfUploadSwitchOpen: Boolean
        get() = mPerfUploadSwitchOpen

    override fun toString(): String {
        return "Config{mEventEncrypted=$mEventEncrypted, mAESKey='$mAESKey', mMaxFileLength=$mMaxFileLength, mEventUploadSwitchOpen=$mEventUploadSwitchOpen, mPerfUploadSwitchOpen=$mPerfUploadSwitchOpen, mEventUploadFrequency=$mEventUploadFrequency, mPerfUploadFrequency=$mPerfUploadFrequency}"
    }

    class Builder {
        var mEventEncrypted: Int = -1
        var mEventUploadSwitchOpen: Int = -1
        var mPerfUploadSwitchOpen: Int = -1
        var mAESKey: String? = null
        var mMaxFileLength: Long = -1
        var mEventUploadFrequency: Long = -1
        var mPerfUploadFrequency: Long = -1

        fun build(context: Context): Config = Config(context, this)

        fun setAESKey(str: String): Builder {
            mAESKey = str
            return this
        }

        fun setEventEncrypted(z: Boolean): Builder {
            mEventEncrypted = if (z) 1 else 0
            return this
        }

        fun setEventUploadFrequency(j: Long): Builder {
            mEventUploadFrequency = j
            return this
        }

        fun setEventUploadSwitchOpen(z: Boolean): Builder {
            mEventUploadSwitchOpen = if (z) 1 else 0
            return this
        }

        fun setMaxFileLength(j: Long): Builder {
            mMaxFileLength = j
            return this
        }

        fun setPerfUploadFrequency(j: Long): Builder {
            mPerfUploadFrequency = j
            return this
        }

        fun setPerfUploadSwitchOpen(z: Boolean): Builder {
            mPerfUploadSwitchOpen = if (z) 1 else 0
            return this
        }
    }
}
