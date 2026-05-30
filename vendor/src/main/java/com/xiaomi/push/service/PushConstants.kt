package com.xiaomi.push.service

class PushConstants private constructor() {
    companion object {
        @JvmField var PUSH_DESCRIPTION = "description"
        const val ACTION_AWAKE_APP_LOGIC = "action_aw_app_logic"
        const val ACTION_AWAKE_APP_PING = "action_help_ping"
        const val ACTION_CHANNEL_CLOSED = "com.xiaomi.push.channel_closed"
        const val ACTION_CHANNEL_OPENED = "com.xiaomi.push.channel_opened"
        const val ACTION_CLIENT_REPORT_CONFIG = "action_cr_config"
        const val ACTION_KICKED_BY_SERVER = "com.xiaomi.push.kicked"
        const val ACTION_RECEIVE_NEW_IQ = "com.xiaomi.push.new_iq"
        const val ACTION_RECEIVE_NEW_MESSAGE = "com.xiaomi.push.new_msg"
        const val ACTION_RECEIVE_NEW_PRESENCE = "com.xiaomi.push.new_pres"
        const val ACTION_SERVICE_STARTED = "com.xiaomi.push.service_started"
        const val ACTION_WAKER_PKGNAME = "waker_pkgname"
        const val ACTION_WAKEUP = "com.xiaomi.mipush.sdk.WAKEUP"
        const val CATEGORY_AWAKE = "category_awake_app"
        const val CLEAR_NOTIFICATION = "clear_notification"
        const val COLON_SEPARATOR = ":"
        const val COMMA_SEPARATOR = ","
        const val DOT_CATEGORY_CLEAR_NOTIFICATION = "category_clear_notification"
        const val DOT_CATEGORY_REGION_IO_READ = "category_region_read"
        const val DOT_CATEGORY_REGION_IO_WRITE = "category_region_write"
        const val DOT_CATEGORY_RUNNING_LOG = "system_running_log"
        const val ERROR_OK = 0
        const val ERROR_SERVICE_NOT_INSTALLED = 1
        const val ERROR_NETWORK_NOT_AVAILABLE = 2
        const val ERROR_NETWORK_FAILED = 3
        const val ERROR_ACCESS_DENIED = 4
        const val ERROR_AUTH_FAILED = 5
        const val ERROR_MULTI_LOGIN = 6
        const val ERROR_SERVER_ERROR = 7
        const val ERROR_RECEIVE_TIMEOUT = 8
        const val ERROR_READ_ERROR = 9
        const val ERROR_SEND_ERROR = 10
        const val ERROR_RESET = 11
        const val ERROR_NO_CLIENT = 12
        const val ERROR_SERVER_STREAM = 13
        const val ERROR_THREAD_BLOCK = 14
        const val ERROR_SERVICE_DESTROY = 15
        const val ERROR_SESSION_CHANGED = 16
        const val ERROR_READ_TIMEOUT = 17
        const val ERROR_CONNECTIING_TIMEOUT = 18
        const val ERROR_USER_BLOCKED = 19
        const val ERROR_REDIRECT = 20
        const val ERROR_BIND_TIMEOUT = 21
        const val ERROR_PING_TIMEOUT = 22
        const val ERROR_IN_EXTREME_POWER_MODE = 23
        const val ERROR_IN_SUPER_POWER_MODE = 24
        const val EXTRA_AWAKE_APP_AWAKE_INFO = "extra_help_aw_info"
        const val EXTRA_AWAKE_APP_ONLINE_CMD = "extra_aw_app_online_cmd"
        const val EXTRA_AWAKE_APP_PING_FREQUENCY = "extra_help_ping_frequency"
        const val EXTRA_AWAKE_APP_PING_SWITCH = "extra_help_ping_switch"
        const val EXTRA_BODY_ENCODE = "ext_body_encode"
        const val EXTRA_CR_EVENT_ENCRYPTED = "action_cr_event_en"
        const val EXTRA_CR_EVENT_FREQUENCY = "action_cr_event_frequency"
        const val EXTRA_CR_EVENT_SWITCH = "action_cr_event_switch"
        const val EXTRA_CR_MAX_FILE_SIZE = "action_cr_max_file_size"
        const val EXTRA_CR_PREF_FREQUENCY = "action_cr_perf_frequency"
        const val EXTRA_CR_PREF_SWITCH = "action_cr_perf_switch"
        const val EXTRA_ENCYPT = "ext_encrypt"
        const val EXTRA_ERROR = "ext_ERROR"
        const val EXTRA_ERROR_CODE = "ext_err_code"
        const val EXTRA_ERROR_CONDITION = "ext_err_cond"
        const val EXTRA_ERROR_MESSAGE = "ext_err_msg"
        const val EXTRA_ERROR_REASON = "ext_err_reason"
        const val EXTRA_ERROR_TYPE = "ext_err_type"
        const val EXTRA_EXTENSIONS = "ext_exts"
        const val EXTRA_EXTENSION_ELEMENT_NAME = "ext_ele_name"
        const val EXTRA_EXTENSION_NAMESPACE = "ext_ns"
        const val EXTRA_EXTENSION_TEXT = "ext_text"
        const val EXTRA_FROM = "ext_from"
        const val EXTRA_IQ_TYPE = "ext_iq_type"
        const val EXTRA_JOB_KEY = "jobkey"
        const val EXTRA_KICK_REASON = "ext_kick_reason"
        const val EXTRA_KICK_TYPE = "ext_kick_type"
        const val EXTRA_MESSAGE_APPID = "ext_msg_appid"
        const val EXTRA_MESSAGE_BODY = "ext_msg_body"
        const val EXTRA_MESSAGE_ENCRYPT = "ext_msg_encrypt"
        const val EXTRA_MESSAGE_FSEQ = "ext_msg_fseq"
        const val EXTRA_MESSAGE_LANGUAGE = "ext_msg_lang"
        const val EXTRA_MESSAGE_MSEQ = "ext_msg_mseq"
        const val EXTRA_MESSAGE_SEQ = "ext_msg_seq"
        const val EXTRA_MESSAGE_STATUS = "ext_msg_status"
        const val EXTRA_MESSAGE_SUBJECT = "ext_msg_sub"
        const val EXTRA_MESSAGE_THREAD = "ext_msg_thread"
        const val EXTRA_MESSAGE_TRANSIENT = "ext_msg_trans"
        const val EXTRA_MESSAGE_TYPE = "ext_msg_type"
        const val EXTRA_PACKET = "ext_packet"
        const val EXTRA_PACKETS = "ext_packets"
        const val EXTRA_PACKET_ID = "ext_pkt_id"
        const val EXTRA_PARAM_APP_RUNNING = "app_running"
        const val EXTRA_PARAM_AWAKE = "__awake"
        const val EXTRA_PARAM_AWAKED = "awaked"
        const val EXTRA_PARAM_CHECK_ALIVE = "__check_alive"
        const val EXTRA_PARAM_CLASS_NAME = "class_name"
        const val EXTRA_PARAM_INTENT_FLAG = "intent_flag"
        const val EXTRA_PARAM_INTENT_URI = "intent_uri"
        const val EXTRA_PARAM_MIID = "__miid"
        const val EXTRA_PARAM_NOTIFY_EFFECT = "notify_effect"
        const val EXTRA_PARAM_WEB_URI = "web_uri"
        const val EXTRA_PAYLOAD = "payload"
        const val EXTRA_PRES_MODE = "ext_pres_mode"
        const val EXTRA_PRES_PRIORITY = "ext_pres_prio"
        const val EXTRA_PRES_STATUS = "ext_pres_status"
        const val EXTRA_PRES_TYPE = "ext_pres_type"
        const val EXTRA_RAW_PACKET = "ext_raw_packet"
        const val EXTRA_REASON = "ext_reason"
        const val EXTRA_REASON_MSG = "ext_reason_msg"
        const val EXTRA_STATS_HOST = "ext_stats_host"
        const val EXTRA_STATS_MAGIC = "ext_stats_magic"
        const val EXTRA_STATS_TYPE = "ext_stats_key"
        const val EXTRA_STATS_VAL = "ext_stats_val"
        const val EXTRA_SUCCEEDED = "ext_succeeded"
        const val EXTRA_THIRDPARTY_HINT_DESC = "com.xiaomi.mipush.thirdparty_DESC"
        const val EXTRA_THIRDPARTY_HINT_LEVEL = "com.xiaomi.mipush.thirdparty_LEVEL"
        const val EXTRA_TO = "ext_to"
        const val EXTRA_TRAFFIC_SOURCE_PKG = "ext_traffic_source_pkg"
        const val FCM_PUSH_INSTANCE_ID_SERVICE_NAME = "com.xiaomi.assemble.control.MiFireBaseInstanceIdService"
        const val FCM_PUSH_MESSAGE_SERVICE_NAME = "com.xiaomi.assemble.control.MiFirebaseMessagingService"
        const val FILE_PATH_SEPARATOR_LEFT_SLASH = "/"
        const val HMS_OLD_PUSH_ACTION_NEW_MESSAGE = "com.huawei.intent.action.PUSH"
        const val HMS_PUSH_ACTION_NEW_MESSAGE = "com.huawei.android.push.intent.RECEIVE"
        const val HMS_PUSH_OLD_RECEIVER_CLASS_NAME = "com.huawei.hms.support.api.push.PushEventReceiver"
        const val HMS_PUSH_RECEIVER_CLASS_NAME = "com.xiaomi.assemble.control.HmsPushReceiver"
        const val HYBRID_PACKAGE_NAME = "com.miui.hybrid"
        const val INTENT_CODE = 17
        const val INTENT_CODE_SERVICE_HANDLESHAKE = 16
        const val INTENT_CODE_XMSF_REGION = 18
        const val INTENT_CODE_RETRY_POLICY = 19
        const val KEY_CHANNEL_PUSH_VERSION_CODE = "cpvc"
        const val KEY_CHANNEL_PUSH_VERSION_NAME = "cpvn"
        const val KEY_COUNTRY_CODE = "country_code"
        const val KEY_PUSH_SDK_VERSION_CODE = "push_sdk_vc"
        const val KEY_PUSH_SDK_VERSION_NAME = "push_sdk_vn"
        const val KEY_REGION = "region"
        const val MESSAGE_ACK_TIME = "mat"
        const val MESSAGE_KEY_XMSF_REGION = "xmsf_region"
        const val MESSAGE_RECEIVE_TIME = "mrt"
        const val METHOD_PARAM_KEY_MESSAGE_ID = "messageId"
        const val MIMC_CHANNEL = "9"
        const val MIN_AW_PING_FREQUENCY = 30
        const val MIPUSH_ACTION_CLEAR_NOTIFICATION = "com.xiaomi.mipush.CLEAR_NOTIFICATION"
        const val MIPUSH_ACTION_DISABLE_PUSH = "com.xiaomi.mipush.DISABLE_PUSH"
        const val MIPUSH_ACTION_DISABLE_PUSH_MESSAGE = "com.xiaomi.mipush.DISABLE_PUSH_MESSAGE"
        const val MIPUSH_ACTION_ENABLE_PUSH_MESSAGE = "com.xiaomi.mipush.ENABLE_PUSH_MESSAGE"
        const val MIPUSH_ACTION_ERROR = "com.xiaomi.mipush.ERROR"
        const val MIPUSH_ACTION_MESSAGE_ARRIVED = "com.xiaomi.mipush.MESSAGE_ARRIVED"
        const val MIPUSH_ACTION_NEW_MESSAGE = "com.xiaomi.mipush.RECEIVE_MESSAGE"
        const val MIPUSH_ACTION_REGISTER_APP = "com.xiaomi.mipush.REGISTER_APP"
        const val MIPUSH_ACTION_SEND_MESSAGE = "com.xiaomi.mipush.SEND_MESSAGE"
        const val MIPUSH_ACTION_SEND_TINYDATA = "com.xiaomi.mipush.SEND_TINYDATA"
        const val MIPUSH_ACTION_SET_NOTIFICATION_TYPE = "com.xiaomi.mipush.SET_NOTIFICATION_TYPE"
        const val MIPUSH_ACTION_THIRDPARTY_HINT = "com.xiaomi.mipush.thirdparty"
        const val MIPUSH_ACTION_UNREGISTER_APP = "com.xiaomi.mipush.UNREGISTER_APP"
        const val MIPUSH_CHANNEL = "5"
        const val MIPUSH_ERROR_SERVICE_UNAVAILABLE = 70000001
        const val MIPUSH_ERROR_AUTHERICATION_ERROR = 70000002
        const val MIPUSH_ERROR_INVALID_PAYLOAD = 70000003
        const val MIPUSH_ERROR_INTERNAL_ERROR = 70000004
        const val MIPUSH_EXTRA_APP_ID = "mipush_app_id"
        const val MIPUSH_EXTRA_APP_PACKAGE = "mipush_app_package"
        const val MIPUSH_EXTRA_APP_TOKEN = "mipush_app_token"
        const val MIPUSH_EXTRA_ENV_CHANAGE = "mipush_env_chanage"
        const val MIPUSH_EXTRA_ENV_TYPE = "mipush_env_type"
        const val MIPUSH_EXTRA_ERROR_CODE = "mipush_error_code"
        const val MIPUSH_EXTRA_ERROR_MSG = "mipush_error_msg"
        const val MIPUSH_EXTRA_HYBRID_APP_PACKAGE = "mipush_hybrid_app_pkg"
        const val MIPUSH_EXTRA_INTENT_PAYLOAD = "mipush_serviceIntent"
        const val MIPUSH_EXTRA_MESSAGE_CACHE = "com.xiaomi.mipush.MESSAGE_CACHE"
        const val MIPUSH_EXTRA_PAYLOAD = "mipush_payload"
        const val MIPUSH_EXTRA_SESSION = "mipush_session"
        const val OC_CLIENT_REPORT_ID = 100
        const val OC_ASSEMBLE_PUSH_ID = 101
        const val OC_AWAKE_ID = 102
        const val OC_STAT_ID = 103
        const val OMS_CHANNEL = "6"
        const val OPPO_PUSH_MESSAGE_SERVICE_NAME = "com.xiaomi.assemble.control.COSPushMessageService"
        const val PING_RECEIVER_CLASS_NAME_JAR = "com.xiaomi.push.service.receivers.PingReceiver"
        const val PUSH_SERVICE_CLASS_NAME = "com.xiaomi.xmsf.push.service.XMPushService"
        const val PUSH_SERVICE_CLASS_NAME_JAR = "com.xiaomi.push.service.XMPushService"
        const val PUSH_SERVICE_PACKAGE_NAME = "com.xiaomi.xmsf"
        @JvmField val PUSH_VERSION_CODE = PushVersionInfo.PUSH_SDK_VERSION_CODE
        @JvmField val PUSH_VERSION_NAME = PushVersionInfo.PUSH_SDK_VERSION_NAME
        @JvmField val FRAMEWORK_APP_VERSION_CODE = PushVersionInfo.STOCK_XMSF_APP_VERSION_CODE
        @JvmField val FRAMEWORK_APP_VERSION_NAME = PushVersionInfo.STOCK_XMSF_APP_VERSION_NAME
        const val REGION_IO = "region_io"
        const val REGION_WRTIE_LATE = "region_write_late"
        const val RUNNING_APP_PACKAGE_NAMES = "aapn"
        const val SP_KEY_APP_ACTIVE_END_TS = "app_end_ts"
        const val SP_KEY_APP_ACTIVE_START_TS = "app_start_ts"
        const val SP_KEY_MIPUSH_REGISTED = "mipush_registed"
        const val SP_KEY_TINY_DATA_KEY = "td_key"
        const val SP_NAME_MIPUSH = "mipush"
        const val SP_NAME_MIPUSH_EXTRA = "mipush_extra"
        const val SP_NAME_MIPUSH_OC = "mipush_oc"
        const val UPLOAD_FILE_POST_KEY = "file"
        const val UPLOAD_FILE_ZIP_POSTFIX = ".zip"
        const val VIVO_PUSH_MESSAGE_RECEIVER_ACTION_NAME = "com.vivo.pushclient.action.RECEIVE"
        const val VIVO_PUSH_MESSAGE_RECEIVER_CLASS_NAME = "com.xiaomi.assemble.control.FTOSPushMessageReceiver"
        const val WAKE_UP_APP = "wake_up_app"

        @Deprecated("Legacy browser component name")
        const val XIAOMI_BROWSER_ACTIVITY_NAME = "com.android.browser.BrowserActivity"
        const val XIAOMI_BROWSER_PACKAGE_NAME = "com.android.browser"
        const val XIAOMI_GLOBALBROWSER_PACKAGE_NAME = "com.mi.globalbrowser"
        const val XM_SERVICE_CLASS_NAME_JAR = "com.xiaomi.push.service.XMJobService"

        @JvmField var NOTIFICATION_CLICK_DEFAULT = "1"
        @JvmField var NOTIFICATION_CLICK_INTENT = "2"
        @JvmField var NOTIFICATION_CLICK_WEB_PAGE = "3"
        @JvmField var ACTION_OPEN_CHANNEL = "com.xiaomi.push.OPEN_CHANNEL"
        @JvmField var ACTION_SEND_MESSAGE = "com.xiaomi.push.SEND_MESSAGE"
        @JvmField var ACTION_SEND_IQ = "com.xiaomi.push.SEND_IQ"
        @JvmField var ACTION_BATCH_SEND_MESSAGE = "com.xiaomi.push.BATCH_SEND_MESSAGE"
        @JvmField var ACTION_SEND_PRESENCE = "com.xiaomi.push.SEND_PRES"
        @JvmField var ACTION_CLOSE_CHANNEL = "com.xiaomi.push.CLOSE_CHANNEL"
        @JvmField var ACTION_FORCE_RECONNECT = "com.xiaomi.push.FORCE_RECONN"
        @JvmField var ACTION_RESET_CONNECTION = "com.xiaomi.push.RESET_CONN"
        @JvmField var ACTION_UPDATE_CHANNEL_INFO = "com.xiaomi.push.UPDATE_CHANNEL_INFO"
        @JvmField var ACTION_SEND_STATS = "com.xiaomi.push.SEND_STATS"
        @JvmField var ACTION_CHANGE_HOST = "com.xiaomi.push.CHANGE_HOST"
        @JvmField var ACTION_PING_TIMER = "com.xiaomi.push.PING_TIMER"
        @JvmField var EXTRA_USER_ID = "ext_user_id"
        @JvmField var EXTRA_USER_RES = "ext_user_res"
        const val EXTRA_CHID = "ext_chid"
        @JvmField var EXTRA_CHANNEL_ID = EXTRA_CHID
        @JvmField var EXTRA_SID = "ext_sid"
        @JvmField var EXTRA_TOKEN = "ext_token"
        @JvmField var EXTRA_AUTH_METHOD = "ext_auth_method"
        @JvmField var EXTRA_SECURITY = "ext_security"
        @JvmField var EXTRA_KICK = "ext_kick"
        @JvmField var EXTRA_CLIENT_ATTR = "ext_client_attr"
        @JvmField var EXTRA_CLOUD_ATTR = "ext_cloud_attr"
        @JvmField var EXTRA_PACKAGE_NAME = "ext_pkg_name"
        @JvmField var EXTRA_NOTIFY_ID = "ext_notify_id"
        @JvmField var EXTRA_NOTIFY_TYPE = "ext_notify_type"
        @JvmField var EXTRA_SESSION = "ext_session"
        @JvmField var EXTRA_SIG = "sig"
        @JvmField var EXTRA_NOTIFY_TITLE = "ext_notify_title"
        @JvmField var EXTRA_NOTIFY_DESCRIPTION = "ext_notify_description"
        @JvmField var EXTRA_MESSENGER = "ext_messenger"
        @JvmField var PUSH_TITLE = "title"
        @JvmField var PUSH_NOTIFY_ID = "notifyId"

        @JvmStatic
        fun getErrorDesc(error: Int): String {
            return when (error) {
                ERROR_OK -> "ERROR_OK"
                ERROR_SERVICE_NOT_INSTALLED -> "ERROR_SERVICE_NOT_INSTALLED"
                ERROR_NETWORK_NOT_AVAILABLE -> "ERROR_NETWORK_NOT_AVAILABLE"
                ERROR_NETWORK_FAILED -> "ERROR_NETWORK_FAILED"
                ERROR_ACCESS_DENIED -> "ERROR_ACCESS_DENIED"
                ERROR_AUTH_FAILED -> "ERROR_AUTH_FAILED"
                ERROR_MULTI_LOGIN -> "ERROR_MULTI_LOGIN"
                ERROR_SERVER_ERROR -> "ERROR_SERVER_ERROR"
                ERROR_RECEIVE_TIMEOUT -> "ERROR_RECEIVE_TIMEOUT"
                ERROR_READ_ERROR -> "ERROR_READ_ERROR"
                ERROR_SEND_ERROR -> "ERROR_SEND_ERROR"
                ERROR_RESET -> "ERROR_RESET"
                ERROR_NO_CLIENT -> "ERROR_NO_CLIENT"
                ERROR_SERVER_STREAM -> "ERROR_SERVER_STREAM"
                ERROR_THREAD_BLOCK -> "ERROR_THREAD_BLOCK"
                ERROR_SERVICE_DESTROY -> "ERROR_SERVICE_DESTROY"
                ERROR_SESSION_CHANGED -> "ERROR_SESSION_CHANGED"
                ERROR_READ_TIMEOUT -> "ERROR_READ_TIMEOUT"
                ERROR_CONNECTIING_TIMEOUT -> "ERROR_CONNECTIING_TIMEOUT"
                ERROR_USER_BLOCKED -> "ERROR_USER_BLOCKED"
                ERROR_REDIRECT -> "ERROR_REDIRECT"
                ERROR_BIND_TIMEOUT -> "ERROR_BIND_TIMEOUT"
                ERROR_PING_TIMEOUT -> "ERROR_PING_TIMEOUT"
                else -> error.toString()
            }
        }
    }
}
