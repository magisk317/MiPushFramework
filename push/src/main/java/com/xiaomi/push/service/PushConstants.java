package com.xiaomi.push.service;

import com.xiaomi.push.service.awake.AwakeUploadHelper;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/PushConstants.class */
public abstract class PushConstants {
    public static final String ACTION_AWAKE_APP_LOGIC = "action_aw_app_logic";
    public static final String ACTION_AWAKE_APP_PING = "action_help_ping";
    public static final String ACTION_CHANNEL_CLOSED = "com.xiaomi.push.channel_closed";
    public static final String ACTION_CHANNEL_OPENED = "com.xiaomi.push.channel_opened";
    public static final String ACTION_CLIENT_REPORT_CONFIG = "action_cr_config";
    public static final String ACTION_KICKED_BY_SERVER = "com.xiaomi.push.kicked";
    public static final String ACTION_RECEIVE_NEW_IQ = "com.xiaomi.push.new_iq";
    public static final String ACTION_RECEIVE_NEW_MESSAGE = "com.xiaomi.push.new_msg";
    public static final String ACTION_RECEIVE_NEW_PRESENCE = "com.xiaomi.push.new_pres";
    public static final String ACTION_SERVICE_STARTED = "com.xiaomi.push.service_started";
    public static final String ACTION_WAKER_PKGNAME = "waker_pkgname";
    public static final String ACTION_WAKEUP = "com.xiaomi.mipush.sdk.WAKEUP";
    public static final String CATEGORY_AWAKE = "category_awake_app";
    public static final String CLEAR_NOTIFICATION = "clear_notification";
    public static final String COLON_SEPARATOR = ":";
    public static final String COMMA_SEPARATOR = ",";
    public static final String DOT_CATEGORY_CLEAR_NOTIFICATION = "category_clear_notification";
    public static final String DOT_CATEGORY_REGION_IO_READ = "category_region_read";
    public static final String DOT_CATEGORY_REGION_IO_WRITE = "category_region_write";
    public static final String DOT_CATEGORY_RUNNING_LOG = "system_running_log";
    public static final int ERROR_ACCESS_DENIED = 4;
    public static final int ERROR_AUTH_FAILED = 5;
    public static final int ERROR_BIND_TIMEOUT = 21;
    public static final int ERROR_CONNECTIING_TIMEOUT = 18;
    public static final int ERROR_IN_EXTREME_POWER_MODE = 23;
    public static final int ERROR_IN_SUPER_POWER_MODE = 24;
    public static final int ERROR_MULTI_LOGIN = 6;
    public static final int ERROR_NETWORK_FAILED = 3;
    public static final int ERROR_NETWORK_NOT_AVAILABLE = 2;
    public static final int ERROR_NO_CLIENT = 12;
    public static final int ERROR_OK = 0;
    public static final int ERROR_PING_TIMEOUT = 22;
    public static final int ERROR_READ_ERROR = 9;
    public static final int ERROR_READ_TIMEOUT = 17;
    public static final int ERROR_RECEIVE_TIMEOUT = 8;
    public static final int ERROR_REDIRECT = 20;
    public static final int ERROR_RESET = 11;
    public static final int ERROR_SEND_ERROR = 10;
    public static final int ERROR_SERVER_ERROR = 7;
    public static final int ERROR_SERVER_STREAM = 13;
    public static final int ERROR_SERVICE_DESTROY = 15;
    public static final int ERROR_SERVICE_NOT_INSTALLED = 1;
    public static final int ERROR_SESSION_CHANGED = 16;
    public static final int ERROR_THREAD_BLOCK = 14;
    public static final int ERROR_USER_BLOCKED = 19;
    public static final String EXTRA_AWAKE_APP_AWAKE_INFO = "extra_help_aw_info";
    public static final String EXTRA_AWAKE_APP_ONLINE_CMD = "extra_aw_app_online_cmd";
    public static final String EXTRA_AWAKE_APP_PING_FREQUENCY = "extra_help_ping_frequency";
    public static final String EXTRA_AWAKE_APP_PING_SWITCH = "extra_help_ping_switch";
    public static final String EXTRA_BODY_ENCODE = "ext_body_encode";
    public static final String EXTRA_CR_EVENT_ENCRYPTED = "action_cr_event_en";
    public static final String EXTRA_CR_EVENT_FREQUENCY = "action_cr_event_frequency";
    public static final String EXTRA_CR_EVENT_SWITCH = "action_cr_event_switch";
    public static final String EXTRA_CR_MAX_FILE_SIZE = "action_cr_max_file_size";
    public static final String EXTRA_CR_PREF_FREQUENCY = "action_cr_perf_frequency";
    public static final String EXTRA_CR_PREF_SWITCH = "action_cr_perf_switch";
    public static final String EXTRA_ENCYPT = "ext_encrypt";
    public static final String EXTRA_ERROR = "ext_ERROR";
    public static final String EXTRA_ERROR_CODE = "ext_err_code";
    public static final String EXTRA_ERROR_CONDITION = "ext_err_cond";
    public static final String EXTRA_ERROR_MESSAGE = "ext_err_msg";
    public static final String EXTRA_ERROR_REASON = "ext_err_reason";
    public static final String EXTRA_ERROR_TYPE = "ext_err_type";
    public static final String EXTRA_EXTENSIONS = "ext_exts";
    public static final String EXTRA_EXTENSION_ELEMENT_NAME = "ext_ele_name";
    public static final String EXTRA_EXTENSION_NAMESPACE = "ext_ns";
    public static final String EXTRA_EXTENSION_TEXT = "ext_text";
    public static final String EXTRA_FROM = "ext_from";
    public static final String EXTRA_IQ_TYPE = "ext_iq_type";
    public static final String EXTRA_JOB_KEY = "jobkey";
    public static final String EXTRA_KICK_REASON = "ext_kick_reason";
    public static final String EXTRA_KICK_TYPE = "ext_kick_type";
    public static final String EXTRA_MESSAGE_APPID = "ext_msg_appid";
    public static final String EXTRA_MESSAGE_BODY = "ext_msg_body";
    public static final String EXTRA_MESSAGE_ENCRYPT = "ext_msg_encrypt";
    public static final String EXTRA_MESSAGE_FSEQ = "ext_msg_fseq";
    public static final String EXTRA_MESSAGE_LANGUAGE = "ext_msg_lang";
    public static final String EXTRA_MESSAGE_MSEQ = "ext_msg_mseq";
    public static final String EXTRA_MESSAGE_SEQ = "ext_msg_seq";
    public static final String EXTRA_MESSAGE_STATUS = "ext_msg_status";
    public static final String EXTRA_MESSAGE_SUBJECT = "ext_msg_sub";
    public static final String EXTRA_MESSAGE_THREAD = "ext_msg_thread";
    public static final String EXTRA_MESSAGE_TRANSIENT = "ext_msg_trans";
    public static final String EXTRA_MESSAGE_TYPE = "ext_msg_type";
    public static final String EXTRA_PACKET = "ext_packet";
    public static final String EXTRA_PACKETS = "ext_packets";
    public static final String EXTRA_PACKET_ID = "ext_pkt_id";
    public static final String EXTRA_PARAM_APP_RUNNING = "app_running";
    public static final String EXTRA_PARAM_AWAKE = "__awake";
    public static final String EXTRA_PARAM_AWAKED = "awaked";
    public static final String EXTRA_PARAM_CHECK_ALIVE = "__check_alive";
    public static final String EXTRA_PARAM_CLASS_NAME = "class_name";
    public static final String EXTRA_PARAM_INTENT_FLAG = "intent_flag";
    public static final String EXTRA_PARAM_INTENT_URI = "intent_uri";
    public static final String EXTRA_PARAM_MIID = "__miid";
    public static final String EXTRA_PARAM_NOTIFY_EFFECT = "notify_effect";
    public static final String EXTRA_PARAM_WEB_URI = "web_uri";
    public static final String EXTRA_PAYLOAD = "payload";
    public static final String EXTRA_PRES_MODE = "ext_pres_mode";
    public static final String EXTRA_PRES_PRIORITY = "ext_pres_prio";
    public static final String EXTRA_PRES_STATUS = "ext_pres_status";
    public static final String EXTRA_PRES_TYPE = "ext_pres_type";
    public static final String EXTRA_RAW_PACKET = "ext_raw_packet";
    public static final String EXTRA_REASON = "ext_reason";
    public static final String EXTRA_REASON_MSG = "ext_reason_msg";
    public static final String EXTRA_STATS_HOST = "ext_stats_host";
    public static final String EXTRA_STATS_MAGIC = "ext_stats_magic";
    public static final String EXTRA_STATS_TYPE = "ext_stats_key";
    public static final String EXTRA_STATS_VAL = "ext_stats_val";
    public static final String EXTRA_SUCCEEDED = "ext_succeeded";
    public static final String EXTRA_THIRDPARTY_HINT_DESC = "com.xiaomi.mipush.thirdparty_DESC";
    public static final String EXTRA_THIRDPARTY_HINT_LEVEL = "com.xiaomi.mipush.thirdparty_LEVEL";
    public static final String EXTRA_TO = "ext_to";
    public static final String EXTRA_TRAFFIC_SOURCE_PKG = "ext_traffic_source_pkg";
    public static final String FCM_PUSH_INSTANCE_ID_SERVICE_NAME = "com.xiaomi.assemble.control.MiFireBaseInstanceIdService";
    public static final String FCM_PUSH_MESSAGE_SERVICE_NAME = "com.xiaomi.assemble.control.MiFirebaseMessagingService";
    public static final String FILE_PATH_SEPARATOR_LEFT_SLASH = "/";
    public static final String HMS_OLD_PUSH_ACTION_NEW_MESSAGE = "com.huawei.intent.action.PUSH";
    public static final String HMS_PUSH_ACTION_NEW_MESSAGE = "com.huawei.android.push.intent.RECEIVE";
    public static final String HMS_PUSH_OLD_RECEIVER_CLASS_NAME = "com.huawei.hms.support.api.push.PushEventReceiver";
    public static final String HMS_PUSH_RECEIVER_CLASS_NAME = "com.xiaomi.assemble.control.HmsPushReceiver";
    public static final String HYBRID_PACKAGE_NAME = "com.miui.hybrid";
    public static final int INTENT_CODE = 17;
    public static final int INTENT_CODE_RETRY_POLICY = 19;
    public static final int INTENT_CODE_SERVICE_HANDLESHAKE = 16;
    public static final int INTENT_CODE_XMSF_REGION = 18;
    public static final String KEY_CHANNEL_PUSH_VERSION_CODE = "cpvc";
    public static final String KEY_CHANNEL_PUSH_VERSION_NAME = "cpvn";
    public static final String KEY_COUNTRY_CODE = "country_code";
    public static final String KEY_PUSH_SDK_VERSION_CODE = "push_sdk_vc";
    public static final String KEY_PUSH_SDK_VERSION_NAME = "push_sdk_vn";
    public static final String KEY_REGION = "region";
    public static final String MESSAGE_ACK_TIME = "mat";
    public static final String MESSAGE_KEY_XMSF_REGION = "xmsf_region";
    public static final String MESSAGE_RECEIVE_TIME = "mrt";
    public static final String METHOD_PARAM_KEY_MESSAGE_ID = "messageId";
    public static final String MIMC_CHANNEL = "9";
    public static final int MIN_AW_PING_FREQUENCY = 30;
    public static final String MIPUSH_ACTION_CLEAR_NOTIFICATION = "com.xiaomi.mipush.CLEAR_NOTIFICATION";
    public static final String MIPUSH_ACTION_DISABLE_PUSH = "com.xiaomi.mipush.DISABLE_PUSH";
    public static final String MIPUSH_ACTION_DISABLE_PUSH_MESSAGE = "com.xiaomi.mipush.DISABLE_PUSH_MESSAGE";
    public static final String MIPUSH_ACTION_ENABLE_PUSH_MESSAGE = "com.xiaomi.mipush.ENABLE_PUSH_MESSAGE";
    public static final String MIPUSH_ACTION_ERROR = "com.xiaomi.mipush.ERROR";
    public static final String MIPUSH_ACTION_MESSAGE_ARRIVED = "com.xiaomi.mipush.MESSAGE_ARRIVED";
    public static final String MIPUSH_ACTION_NEW_MESSAGE = "com.xiaomi.mipush.RECEIVE_MESSAGE";
    public static final String MIPUSH_ACTION_REGISTER_APP = "com.xiaomi.mipush.REGISTER_APP";
    public static final String MIPUSH_ACTION_SEND_MESSAGE = "com.xiaomi.mipush.SEND_MESSAGE";
    public static final String MIPUSH_ACTION_SEND_TINYDATA = "com.xiaomi.mipush.SEND_TINYDATA";
    public static final String MIPUSH_ACTION_SET_NOTIFICATION_TYPE = "com.xiaomi.mipush.SET_NOTIFICATION_TYPE";
    public static final String MIPUSH_ACTION_THIRDPARTY_HINT = "com.xiaomi.mipush.thirdparty";
    public static final String MIPUSH_ACTION_UNREGISTER_APP = "com.xiaomi.mipush.UNREGISTER_APP";
    public static final String MIPUSH_CHANNEL = "5";
    public static final int MIPUSH_ERROR_AUTHERICATION_ERROR = 70000002;
    public static final int MIPUSH_ERROR_INTERNAL_ERROR = 70000004;
    public static final int MIPUSH_ERROR_INVALID_PAYLOAD = 70000003;
    public static final int MIPUSH_ERROR_SERVICE_UNAVAILABLE = 70000001;
    public static final String MIPUSH_EXTRA_APP_ID = "mipush_app_id";
    public static final String MIPUSH_EXTRA_APP_PACKAGE = "mipush_app_package";
    public static final String MIPUSH_EXTRA_APP_TOKEN = "mipush_app_token";
    public static final String MIPUSH_EXTRA_ENV_CHANAGE = "mipush_env_chanage";
    public static final String MIPUSH_EXTRA_ENV_TYPE = "mipush_env_type";
    public static final String MIPUSH_EXTRA_ERROR_CODE = "mipush_error_code";
    public static final String MIPUSH_EXTRA_ERROR_MSG = "mipush_error_msg";
    public static final String MIPUSH_EXTRA_HYBRID_APP_PACKAGE = "mipush_hybrid_app_pkg";
    public static final String MIPUSH_EXTRA_INTENT_PAYLOAD = "mipush_serviceIntent";
    public static final String MIPUSH_EXTRA_MESSAGE_CACHE = "com.xiaomi.mipush.MESSAGE_CACHE";
    public static final String MIPUSH_EXTRA_PAYLOAD = "mipush_payload";
    public static final String MIPUSH_EXTRA_SESSION = "mipush_session";
    public static final int OC_ASSEMBLE_PUSH_ID = 101;
    public static final int OC_AWAKE_ID = 102;
    public static final int OC_CLIENT_REPORT_ID = 100;
    public static final int OC_STAT_ID = 103;
    public static final String OMS_CHANNEL = "6";
    public static final String OPPO_PUSH_MESSAGE_SERVICE_NAME = "com.xiaomi.assemble.control.COSPushMessageService";
    public static final String PING_RECEIVER_CLASS_NAME_JAR = "com.xiaomi.push.service.receivers.PingReceiver";
    public static final String PUSH_SERVICE_CLASS_NAME = "com.xiaomi.xmsf.push.service.XMPushService";
    public static final String PUSH_SERVICE_CLASS_NAME_JAR = "com.xiaomi.push.service.XMPushService";
    public static final String PUSH_SERVICE_PACKAGE_NAME = "com.xiaomi.xmsf";
    public static final int PUSH_VERSION_CODE = 30709;
    public static final String PUSH_VERSION_NAME = "3_7_9";
    public static final String REGION_IO = "region_io";
    public static final String REGION_WRTIE_LATE = "region_write_late";
    public static final String RUNNING_APP_PACKAGE_NAMES = "aapn";
    public static final String SP_KEY_APP_ACTIVE_END_TS = "app_end_ts";
    public static final String SP_KEY_APP_ACTIVE_START_TS = "app_start_ts";
    public static final String SP_KEY_MIPUSH_REGISTED = "mipush_registed";
    public static final String SP_KEY_TINY_DATA_KEY = "td_key";
    public static final String SP_NAME_MIPUSH = "mipush";
    public static final String SP_NAME_MIPUSH_EXTRA = "mipush_extra";
    public static final String SP_NAME_MIPUSH_OC = "mipush_oc";
    public static final String UPLOAD_FILE_POST_KEY = "file";
    public static final String UPLOAD_FILE_ZIP_POSTFIX = ".zip";
    public static final String VIVO_PUSH_MESSAGE_RECEIVER_ACTION_NAME = "com.vivo.pushclient.action.RECEIVE";
    public static final String VIVO_PUSH_MESSAGE_RECEIVER_CLASS_NAME = "com.xiaomi.assemble.control.FTOSPushMessageReceiver";
    public static final String WAKE_UP_APP = "wake_up_app";

    @Deprecated
    public static final String XIAOMI_BROWSER_ACTIVITY_NAME = "com.android.browser.BrowserActivity";
    public static final String XIAOMI_BROWSER_PACKAGE_NAME = "com.android.browser";
    public static final String XIAOMI_GLOBALBROWSER_PACKAGE_NAME = "com.mi.globalbrowser";
    public static final String XM_SERVICE_CLASS_NAME_JAR = "com.xiaomi.push.service.XMJobService";
    public static String NOTIFICATION_CLICK_DEFAULT = "1";
    public static String NOTIFICATION_CLICK_INTENT = "2";
    public static String NOTIFICATION_CLICK_WEB_PAGE = "3";
    public static String ACTION_OPEN_CHANNEL = "com.xiaomi.push.OPEN_CHANNEL";
    public static String ACTION_SEND_MESSAGE = "com.xiaomi.push.SEND_MESSAGE";
    public static String ACTION_SEND_IQ = "com.xiaomi.push.SEND_IQ";
    public static String ACTION_BATCH_SEND_MESSAGE = "com.xiaomi.push.BATCH_SEND_MESSAGE";
    public static String ACTION_SEND_PRESENCE = "com.xiaomi.push.SEND_PRES";
    public static String ACTION_CLOSE_CHANNEL = "com.xiaomi.push.CLOSE_CHANNEL";
    public static String ACTION_FORCE_RECONNECT = "com.xiaomi.push.FORCE_RECONN";
    public static String ACTION_RESET_CONNECTION = "com.xiaomi.push.RESET_CONN";
    public static String ACTION_UPDATE_CHANNEL_INFO = "com.xiaomi.push.UPDATE_CHANNEL_INFO";
    public static String ACTION_SEND_STATS = "com.xiaomi.push.SEND_STATS";
    public static String ACTION_CHANGE_HOST = "com.xiaomi.push.CHANGE_HOST";
    public static String ACTION_PING_TIMER = "com.xiaomi.push.PING_TIMER";
    public static String EXTRA_USER_ID = "ext_user_id";
    public static String EXTRA_USER_RES = "ext_user_res";
    public static final String EXTRA_CHID = "ext_chid";
    public static String EXTRA_CHANNEL_ID = EXTRA_CHID;
    public static String EXTRA_SID = "ext_sid";
    public static String EXTRA_TOKEN = "ext_token";
    public static String EXTRA_AUTH_METHOD = "ext_auth_method";
    public static String EXTRA_SECURITY = "ext_security";
    public static String EXTRA_KICK = "ext_kick";
    public static String EXTRA_CLIENT_ATTR = "ext_client_attr";
    public static String EXTRA_CLOUD_ATTR = "ext_cloud_attr";
    public static String EXTRA_PACKAGE_NAME = "ext_pkg_name";
    public static String EXTRA_NOTIFY_ID = "ext_notify_id";
    public static String EXTRA_NOTIFY_TYPE = "ext_notify_type";
    public static String EXTRA_SESSION = "ext_session";
    public static String EXTRA_SIG = "sig";
    public static String EXTRA_NOTIFY_TITLE = "ext_notify_title";
    public static String EXTRA_NOTIFY_DESCRIPTION = "ext_notify_description";
    public static String EXTRA_MESSENGER = "ext_messenger";
    public static String PUSH_TITLE = "title";
    public static String PUSH_DESCRIPTION = AwakeUploadHelper.KEY_DESCRIPTION;
    public static String PUSH_NOTIFY_ID = "notifyId";

    public static String getErrorDesc(int i) {
        switch (i) {
            case 0:
                return "ERROR_OK";
            case 1:
                return "ERROR_SERVICE_NOT_INSTALLED";
            case 2:
                return "ERROR_NETWORK_NOT_AVAILABLE";
            case 3:
                return "ERROR_NETWORK_FAILED";
            case 4:
                return "ERROR_ACCESS_DENIED";
            case 5:
                return "ERROR_AUTH_FAILED";
            case 6:
                return "ERROR_MULTI_LOGIN";
            case 7:
                return "ERROR_SERVER_ERROR";
            case 8:
                return "ERROR_RECEIVE_TIMEOUT";
            case 9:
                return "ERROR_READ_ERROR";
            case 10:
                return "ERROR_SEND_ERROR";
            case 11:
                return "ERROR_RESET";
            case 12:
                return "ERROR_NO_CLIENT";
            case 13:
                return "ERROR_SERVER_STREAM";
            case 14:
                return "ERROR_THREAD_BLOCK";
            case 15:
                return "ERROR_SERVICE_DESTROY";
            case 16:
                return "ERROR_SESSION_CHANGED";
            case 17:
                return "ERROR_READ_TIMEOUT";
            case 18:
                return "ERROR_CONNECTIING_TIMEOUT";
            case 19:
                return "ERROR_USER_BLOCKED";
            case ERROR_REDIRECT /* 20 */:
                return "ERROR_REDIRECT";
            case ERROR_BIND_TIMEOUT /* 21 */:
                return "ERROR_BIND_TIMEOUT";
            case ERROR_PING_TIMEOUT /* 22 */:
                return "ERROR_PING_TIMEOUT";
            default:
                return String.valueOf(i);
        }
    }
}
