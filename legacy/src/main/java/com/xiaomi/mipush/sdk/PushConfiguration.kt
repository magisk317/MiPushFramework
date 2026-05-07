package com.xiaomi.mipush.sdk

import com.xiaomi.push.service.module.PushChannelRegion

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/PushConfiguration.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
class PushConfiguration {
    private var mGeoEnable = false
    var openCOSPush: Boolean = false
    var openFCMPush: Boolean = false
    var openFTOSPush: Boolean = false
    var openHmsPush: Boolean = false
    var region: PushChannelRegion? = PushChannelRegion.China

    class PushConfigurationBuilder {
        private var mGeoEnable = false
        internal var mOpenCOSPush = false
        internal var mOpenFCMPush = false
        internal var mOpenFTOSPush = false
        internal var mOpenHmsPush = false
        internal var mRegion: PushChannelRegion? = null

        fun build(): PushConfiguration = PushConfiguration(this)

        fun openCOSPush(value: Boolean): PushConfigurationBuilder {
            mOpenCOSPush = value
            return this
        }

        fun openFCMPush(value: Boolean): PushConfigurationBuilder {
            mOpenFCMPush = value
            return this
        }

        fun openFTOSPush(value: Boolean): PushConfigurationBuilder {
            mOpenFTOSPush = value
            return this
        }

        fun openHmsPush(value: Boolean): PushConfigurationBuilder {
            mOpenHmsPush = value
            return this
        }

        fun region(pushChannelRegion: PushChannelRegion?): PushConfigurationBuilder {
            mRegion = pushChannelRegion
            return this
        }
    }

    constructor()

    private constructor(builder: PushConfigurationBuilder) {
        region = builder.mRegion ?: PushChannelRegion.China
        openHmsPush = builder.mOpenHmsPush
        openFCMPush = builder.mOpenFCMPush
        openCOSPush = builder.mOpenCOSPush
        openFTOSPush = builder.mOpenFTOSPush
    }

    override fun toString(): String {
        val stringBuffer = StringBuffer("PushConfiguration{")
        stringBuffer.append("Region:")
        val pushChannelRegion = region
        if (pushChannelRegion == null) {
            stringBuffer.append("null")
        } else {
            stringBuffer.append(pushChannelRegion.name)
        }
        stringBuffer.append(",mOpenHmsPush:$openHmsPush")
        stringBuffer.append(",mOpenFCMPush:$openFCMPush")
        stringBuffer.append(",mOpenCOSPush:$openCOSPush")
        stringBuffer.append(",mOpenFTOSPush:$openFTOSPush")
        stringBuffer.append('}')
        return stringBuffer.toString()
    }
}
