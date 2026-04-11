package io.github.magisk317.mipush.common

import top.trumeet.common.BuildConfig

/**
 * Created by Trumeet on 2017/8/24.
 * Constants
 */

object Constants {
    /**
     * Default app log tag
     */
    const val TAG = "Xiaomi"

    const val TAG_CONDOM = "$TAG-Condom"

    const val WIZARD_SP_NAME = "wizard"
    const val KEY_SHOW_WIZARD = "show_wizard"

    const val ACTION_RECEIVE_MESSAGE = "com.xiaomi.mipush.RECEIVE_MESSAGE"
    const val ACTION_MESSAGE_ARRIVED = "com.xiaomi.mipush.MESSAGE_ARRIVED"
    const val ACTION_ERROR = "com.xiaomi.mipush.ERROR"

    /**
     * Enable push.
     */
    const val KEY_ENABLE_PUSH = "enable_push"

    /**
     * XMPush APP id
     */
    @JvmField
    var APP_ID = "1000271"

    /**
     * XMPush APP key
     */
    @JvmField
    var APP_KEY = "420100086271"

    /**
     * Every page item count
     */
    const val PAGE_SIZE = 20

    /**
     * Package name extra when register push
     */
    const val EXTRA_MI_PUSH_PACKAGE = "mipush_app_package"

    /**
     * Message type extra when receive push
     */
    const val EXTRA_MESSAGE_TYPE = "message_type"

    /**
     * Register push result type.
     */
    const val MESSAGE_TYPE_REGISTER_RESULT = 3

    /**
     * Push result type.
     */
    const val MESSAGE_TYPE_PUSH = 1

    /**
     * Use in wizard, finish activity when user click NEXT,
     * not go next page.
     */
    const val EXTRA_FINISH_ON_NEXT = "top.trumeet.xmsf.EXTRA_FINISH_ON_NEXT"

    /**
     * Application log file
     */
    const val LOG_FILE = "/file.log"

    const val AUTHORITY_FILE_PROVIDER = "top.trumeet.mipushframework.fileprovider"

    const val SERVICE_APP_NAME = "com.xiaomi.xmsf"

    const val MANAGER_APP_NAME = "top.trumeet.mipush"

    @JvmField
    val PUSH_SERVICE_VERSION_CODE = BuildConfig.PUSH_VERSION_CODE.toInt()

    const val SHARE_LOG_COMPONENT_NAME = "$SERVICE_APP_NAME.ShareLogActivity"

    const val REMOVE_DOZE_COMPONENT_NAME = "$SERVICE_APP_NAME.RemoveDozeActivity"

    const val INTENT_NOTIFICATION_ID = "mipush_notification_id"
    const val INTENT_NOTIFICATION_GROUP = "mipush_notification_group"

    const val CONFIGURATIONS_FILE_NAME = "configs.json"
    const val CONFIGURATIONS_UPDATE_ACTION = "__MIPUSH_CONFIGURATIONS_UPDATE__"
}
