package com.xiaomi.channel.commonutils.network

data class BasicNameValuePair(
    override val name: String,
    override val value: String
) : NameValuePair {
    init {
        require(name != null) { "Name may not be null" }
    }
}
