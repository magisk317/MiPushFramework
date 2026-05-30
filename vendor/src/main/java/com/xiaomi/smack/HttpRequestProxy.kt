package com.xiaomi.smack

import com.xiaomi.channel.commonutils.network.NameValuePair

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/smack/HttpRequestProxy.java
 * Stock 7.4.67-C has no separate qa.* counterpart; this file keeps the deobfuscated compatibility API.
 */
interface HttpRequestProxy {
    fun doHttpGet(url: String, params: List<NameValuePair>?): String?

    fun doHttpPost(url: String, params: List<NameValuePair>?): String?

    fun isWapNetwork(): Boolean
}
