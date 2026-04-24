package com.xiaomi.mipush.sdk

import com.xiaomi.channel.commonutils.misc.BuildSettings

object Constants {
    const val ACCEPT_TIME_SEPARATOR_SERVER = "-"
    const val ACCEPT_TIME_SEPARATOR_SP = ","
    const val APP_ID = "app_id"
    const val ASSEMBLE_PUSH_NETWORK_INTERVAL = 300000L
    const val ASSEMBLE_PUSH_REG_INFO = "RegInfo"
    val ASSEMBLE_PUSH_RETRY_DELAY = intArrayOf(2000, 4000, 8000)
    const val ASSEMBLE_PUSH_RETRY_INTERVAL = 2000
    const val ASSEMBLE_PUSH_TOKEN = "token"
    const val COLON_SEPARATOR = ":"
    const val EXTRA_KEY_ACCEPT_TIME = "accept_time"
    const val EXTRA_KEY_ACCOUNTS = "user_accounts"
    const val EXTRA_KEY_ACCOUNTS_MD5 = "accounts_md5"
    const val EXTRA_KEY_ALIASES = "aliases"
    const val EXTRA_KEY_ALIASES_MD5 = "aliases_md5"
    const val EXTRA_KEY_APP_VERSION = "app_version"
    const val EXTRA_KEY_APP_VERSION_CODE = "app_version_code"
    const val EXTRA_KEY_BOOT_SERVICE_MODE = "service_boot_mode"
    const val EXTRA_KEY_HYBRID_DEVICE_STATUS = "__hybrid_device_status"
    const val EXTRA_KEY_HYBRID_MESSAGE_TS = "__hybrid_message_ts"
    const val EXTRA_KEY_HYBRID_PASS_THROUGH = "hybrid_pt"
    const val EXTRA_KEY_HYBRID_PKGNAME = "hybrid_pkg"
    const val EXTRA_KEY_IMEI_MD5 = "imei_md5"
    const val EXTRA_KEY_INITIAL_WIFI_UPLOAD = "initial_wifi_upload"
    const val EXTRA_KEY_MIID = "miid"
    const val EXTRA_KEY_PUSH_SERVER_ACTION = "push_server_action"
    const val EXTRA_KEY_REG_ID = "reg_id"
    const val EXTRA_KEY_REG_SECRET = "reg_secret"
    const val EXTRA_KEY_TOKEN = "token"
    const val EXTRA_KEY_TOPICS = "topics"
    const val EXTRA_KEY_TOPICS_MD5 = "topics_md5"
    const val EXTRA_VALUE_HYBRID_MESSAGE = "hybrid_message"
    const val EXTRA_VALUE_PLATFORM_MESSAGE = "platform_message"
    const val HUAWEI_HMS_CLIENT_APPID = "com.huawei.hms.client.appid"
    const val HYBRID_DEBUG_PACKAGE_NAME = "com.miui.hybrid.loader"
    const val HYBRID_PACKAGE_NAME = "com.miui.hybrid"
    const val PACKAGE_NAME = "package_name"
    const val PHONE_BRAND = "brand"
    const val PREF_EXTRA = "mipush_extra"
    const val SP_KEY_LAST_REINITIALIZE = "last_reinitialize"
    const val WAVE_SEPARATOR = "~"

    @JvmStatic
    fun getEnvType(): Int = BuildSettings.getEnvType()

    @JvmStatic
    private fun useOfficial() {
        BuildSettings.setEnvType(1)
    }

    @JvmStatic
    private fun useOnebox() {
        BuildSettings.setEnvType(3)
    }

    @JvmStatic
    private fun useSandbox() {
        BuildSettings.setEnvType(2)
    }
}
