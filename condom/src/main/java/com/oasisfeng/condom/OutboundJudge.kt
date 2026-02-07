package com.oasisfeng.condom

import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.Keep

/**
 * The callback for outbound request filtering.
 *
 * Created by Oasis on 2017/4/21.
 */
@Keep
interface OutboundJudge {
    /**
     * Judge the outbound request or query by intent and its target package, which may or may not be explicit in intent.
     *
     * For query requests (including [PackageManager.resolveService], [PackageManager.queryIntentServices]
     * and [PackageManager.queryBroadcastReceivers]), this will be called for each candidate,
     * before additional filtering (e.g. [CondomOptions.preventServiceInBackgroundPackages]) is applied.
     *
     * Note: Implicit broadcast will never go through this.
     *
     * @param type the type of outbound request or query being judged
     * @param intent the intent of current request or query, or null if unavailable (e.g. content provider access).
     * @param target_package the target package of current request or candidate package of current query, may or may not be explicit in intent.
     * @return whether this outbound request should be allowed, or whether the query result entry should be included in the returned collection.
     * Disallowed service request will simply fail while disallowed broadcast target will be skipped.
     */
    fun shouldAllow(type: OutboundType, intent: Intent?, target_package: String): Boolean
}
