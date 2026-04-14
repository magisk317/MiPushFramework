package io.github.magisk317.mipush.framework.lifecycle.support

import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.slim.Blob
import java.util.HashMap

object PushClientsStateSupport {
    @JvmStatic
    fun notifyConnectionFailed(iterable: Iterable<HashMap<String?, PushClientsManager.ClientLoginInfo>>) {
        iterable.forEach { clients ->
            clients.values.forEach { client ->
                client.setStatus(PushClientsManager.ClientStatus.unbind, 1, 3, null, null)
            }
        }
    }

    @JvmStatic
    fun queryChannelIdByPackage(
        iterable: Iterable<HashMap<String?, PushClientsManager.ClientLoginInfo>>,
        pkg: String,
    ): List<String> {
        val result = ArrayList<String>()
        iterable.forEach { clients ->
            clients.values.forEach { client ->
                if (pkg == client.pkgName) {
                    result.add(client.chid)
                }
            }
        }
        return result
    }

    @JvmStatic
    fun resetAllClients(iterable: Iterable<HashMap<String?, PushClientsManager.ClientLoginInfo>>, reason: Int) {
        iterable.forEach { clients ->
            clients.values.forEach { client ->
                client.setStatus(PushClientsManager.ClientStatus.unbind, 2, reason, null, null)
            }
        }
    }

    @JvmStatic
    fun constructBindBlob(client: PushClientsManager.ClientLoginInfo): Blob {
        return Blob().apply {
            setChannelId(client.chid.toInt())
            setCmd(Blob.CMD_BIND, null)
            setPackageName(client.pkgName)
        }
    }

    @JvmStatic
    fun constructUnbindBlob(chid: String, userId: String): Blob {
        return Blob().apply {
            setChannelId(chid.toInt())
            setCmd(Blob.CMD_UNBIND, null)
        }
    }
}
