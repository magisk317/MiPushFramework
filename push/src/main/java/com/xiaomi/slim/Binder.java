package com.xiaomi.slim;

import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.string.CloudCoder;
import com.xiaomi.push.protobuf.ChannelMessage;
import com.xiaomi.push.service.PushClientsManager;
import com.xiaomi.smack.Connection;
import com.xiaomi.smack.XMPPException;
import java.util.HashMap;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/slim/Binder.class */
class Binder {
    Binder() {
    }

    public static void bind(PushClientsManager.ClientLoginInfo clientLoginInfo, String str, Connection connection) throws XMPPException {
        String strGenerateSignature;
        ChannelMessage.XMMsgBind xMMsgBind = new ChannelMessage.XMMsgBind();
        if (!TextUtils.isEmpty(clientLoginInfo.token)) {
            xMMsgBind.setToken(clientLoginInfo.token);
        }
        if (!TextUtils.isEmpty(clientLoginInfo.clientExtra)) {
            xMMsgBind.setClientAttrs(clientLoginInfo.clientExtra);
        }
        if (!TextUtils.isEmpty(clientLoginInfo.cloudExtra)) {
            xMMsgBind.setCloudAttrs(clientLoginInfo.cloudExtra);
        }
        xMMsgBind.setKick(clientLoginInfo.kick ? "1" : Blob.CLIENT_PING_ID);
        if (TextUtils.isEmpty(clientLoginInfo.authMethod)) {
            xMMsgBind.setMethod("XIAOMI-SASL");
        } else {
            xMMsgBind.setMethod(clientLoginInfo.authMethod);
        }
        Blob blob = new Blob();
        blob.setFrom(clientLoginInfo.userId);
        blob.setChannelId(Integer.parseInt(clientLoginInfo.chid));
        blob.setPackageName(clientLoginInfo.pkgName);
        blob.setCmd(Blob.CMD_BIND, null);
        blob.setPacketID(blob.getPacketID());
        MyLog.w("[Slim]: bind id=" + blob.getPacketID());
        HashMap map = new HashMap();
        map.put("challenge", str);
        map.put("token", clientLoginInfo.token);
        map.put("chid", clientLoginInfo.chid);
        map.put("from", clientLoginInfo.userId);
        map.put("id", blob.getPacketID());
        map.put("to", "xiaomi.com");
        if (clientLoginInfo.kick) {
            map.put("kick", "1");
        } else {
            map.put("kick", Blob.CLIENT_PING_ID);
        }
        if (TextUtils.isEmpty(clientLoginInfo.clientExtra)) {
            map.put("client_attrs", "");
        } else {
            map.put("client_attrs", clientLoginInfo.clientExtra);
        }
        if (TextUtils.isEmpty(clientLoginInfo.cloudExtra)) {
            map.put("cloud_attrs", "");
        } else {
            map.put("cloud_attrs", clientLoginInfo.cloudExtra);
        }
        if (clientLoginInfo.authMethod.equals("XIAOMI-PASS") || clientLoginInfo.authMethod.equals("XMPUSH-PASS")) {
            strGenerateSignature = CloudCoder.generateSignature(clientLoginInfo.authMethod, null, map, clientLoginInfo.security);
        } else {
            clientLoginInfo.authMethod.equals("XIAOMI-SASL");
            strGenerateSignature = null;
        }
        xMMsgBind.setSig(strGenerateSignature);
        blob.setPayload(xMMsgBind.toByteArray(), null);
        connection.send(blob);
    }

    public static void unbind(String str, String str2, Connection connection) throws XMPPException {
        Blob blob = new Blob();
        blob.setFrom(str2);
        blob.setChannelId(Integer.parseInt(str));
        blob.setCmd(Blob.CMD_UNBIND, null);
        connection.send(blob);
    }
}
