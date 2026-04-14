package com.xiaomi.smack

import com.xiaomi.channel.commonutils.network.NameValuePair

interface HttpRequestProxy {
    fun doHttpGet(url: String, params: List<NameValuePair>?): String?

    fun doHttpPost(url: String, params: List<NameValuePair>?): String?

    fun isWapNetwork(): Boolean
}
