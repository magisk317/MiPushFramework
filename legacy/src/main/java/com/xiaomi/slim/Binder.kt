package com.xiaomi.slim
import io.github.magisk317.mipush.protocol.model.*

import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.string.CloudCoder
import com.xiaomi.push.protobuf.ChannelMessage
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.smack.Connection
import com.xiaomi.smack.XMPPException

internal object Binder {
    @JvmStatic
    @Throws(XMPPException::class)
    fun bind(
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        challenge: String?,
        connection: Connection,
    ) {
        if (challenge.isNullOrEmpty()) {
             MyLog.w("[Slim] Challenge is empty, skip bind sig")
             return
        }
        var strGenerateSignature: String? = null
        val xMMsgBind = ChannelMessage.XMMsgBind().apply {
            if (!TextUtils.isEmpty(clientLoginInfo.token)) {
                token = clientLoginInfo.token
            }
            if (!TextUtils.isEmpty(clientLoginInfo.clientExtra)) {
                clientAttrs = clientLoginInfo.clientExtra
            }
            if (!TextUtils.isEmpty(clientLoginInfo.cloudExtra)) {
                cloudAttrs = clientLoginInfo.cloudExtra
            }
            kick = if (clientLoginInfo.kick) "1" else Blob.CLIENT_PING_ID
            method = if (TextUtils.isEmpty(clientLoginInfo.authMethod)) {
                "XIAOMI-SASL"
            } else {
                clientLoginInfo.authMethod
            }
        }

        val blob = Blob().apply {
            from = clientLoginInfo.userId
            setChannelId(clientLoginInfo.chid.toInt())
            mPackageName = clientLoginInfo.pkgName
            setCmd(Blob.CMD_BIND, null)
            packetID = packetID
            MyLog.w("[Slim]: bind id=$packetID")
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
