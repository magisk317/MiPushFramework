package com.xiaomi.channel.commonutils.network

import java.util.HashMap

class HttpResponse {
    @JvmField
    var headers: MutableMap<String, String> = HashMap()
    @JvmField
    var responseCode: Int = 0
    @JvmField
    var responseString: String? = null

    val isSuccess: Boolean
        get() = responseCode == 200

    override fun toString(): String {
        return "resCode = $responseCode, headers = $headers, response = $responseString"
    }
}
