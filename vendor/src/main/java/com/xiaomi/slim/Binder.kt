package com.xiaomi.slim

import co.touchlab.kermit.Logger
import com.xiaomi.channel.commonutils.string.CloudCoder
import com.xiaomi.push.protobuf.ChannelMessage
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.smack.Connection
import com.xiaomi.smack.XMPPException

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/pa/a.java
 * Stock class name is obfuscated as pa.a; this file keeps the deobfuscated com.xiaomi.slim.Binder API.
 */
internal object Binder {
    @JvmStatic
    @Throws(XMPPException::class)
    fun bind(
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        challenge: String?,
        connection: Connection,
    ) {
        if (challenge.isNullOrEmpty()) {
             Logger.w { "[Slim] Challenge is empty, skip bind sig" }
             return
        }
        var strGenerateSignature: String? = null
        val xMMsgBind = ChannelMessage.XMMsgBind().apply {
            if (!clientLoginInfo.token.isNullOrEmpty()) {
                token = clientLoginInfo.token
            }
            if (!clientLoginInfo.clientExtra.isNullOrEmpty()) {
                clientAttrs = clientLoginInfo.clientExtra
            }
            if (!clientLoginInfo.cloudExtra.isNullOrEmpty()) {
                cloudAttrs = clientLoginInfo.cloudExtra
            }
            kick = if (clientLoginInfo.kick) "1" else Blob.CLIENT_PING_ID
            method = clientLoginInfo.authMethod.ifEmpty { "XIAOMI-SASL" }
        }

        val blob = Blob().apply {
            from = clientLoginInfo.userId
            setChannelId(clientLoginInfo.chid.toInt())
            mPackageName = clientLoginInfo.pkgName
            setCmd(Blob.CMD_BIND, null)
            packetID = Blob.nextID()
            Logger.w { "[Slim]: bind id=$packetID" }
        }

        val map: MutableMap<String, String> = mutableMapOf(
            "challenge" to challenge,
            "token" to clientLoginInfo.token,
            "chid" to clientLoginInfo.chid,
            "from" to clientLoginInfo.userId,
            "id" to blob.packetID!!,
            "to" to "xiaomi.com",
        ).apply {
            put("kick", if (clientLoginInfo.kick) "1" else Blob.CLIENT_PING_ID)
            put("client_attrs", clientLoginInfo.clientExtra)
            put("cloud_attrs", clientLoginInfo.cloudExtra)
        }

        strGenerateSignature = if (clientLoginInfo.authMethod == "XIAOMI-PASS" || clientLoginInfo.authMethod == "XMPUSH-PASS") {
            CloudCoder.generateSignature(clientLoginInfo.authMethod, null, map, clientLoginInfo.security)
        } else {
            null
        }

        xMMsgBind.sig = strGenerateSignature
        blob.setPayload(xMMsgBind.toByteArray(), null)
        connection.send(blob)
    }

    @JvmStatic
    @Throws(XMPPException::class)
    fun unbind(
        chid: String,
        userId: String,
        connection: Connection,
    ) {
        val blob = Blob().apply {
            from = userId
            setChannelId(chid.toInt())
            setCmd(Blob.CMD_UNBIND, null)
        }
        connection.send(blob)
    }
}
