package com.xiaomi.push.service

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
}
