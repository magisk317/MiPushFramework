package com.oasisfeng.condom

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import androidx.annotation.Keep

/**
 * The type of outbound request
 *
 * Created by Oasis on 2017/4/21.
 */
@Keep
enum class OutboundType {
    /** @see Context.startService */
    START_SERVICE,

    /** @see Context.bindService */
    BIND_SERVICE,

    /** Sending broadcast  */
    BROADCAST,

    /** Requesting content provider  */
    CONTENT,

    /** Either [PackageManager.queryIntentServices] or [PackageManager.resolveService]  */
    QUERY_SERVICES,

    /** @see PackageManager.queryBroadcastReceivers  */
    QUERY_RECEIVERS
}
