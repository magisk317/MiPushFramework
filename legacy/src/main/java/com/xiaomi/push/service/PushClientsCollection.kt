package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import java.util.concurrent.ConcurrentHashMap

class PushClientsCollection {
    private val activeClients =
        ConcurrentHashMap<String, HashMap<String?, PushClientsManager.ClientLoginInfo>>()
    private val clientChangeListeners = ArrayList<PushClientsManager.ClientChangeListener>()

    fun addActiveClient(clientLoginInfo: PushClientsManager.ClientLoginInfo) {
        val clientsByChannel = activeClients.getOrPut(clientLoginInfo.chid) { HashMap() }
        clientsByChannel[smtpLocalPart(clientLoginInfo.userId)] = clientLoginInfo
        notifyListeners()
    }

    fun addClientChangeListener(clientChangeListener: PushClientsManager.ClientChangeListener) {
        clientChangeListeners += clientChangeListener
    }

    fun deactivateAllClientByChid(chid: String) {
        activeClients[chid]?.let { clientsByChannel ->
            clientsByChannel.values.forEach { it.unwatch() }
            clientsByChannel.clear()
            activeClients.remove(chid)
        }
        notifyListeners()
    }

    fun deactivateClient(chid: String, userId: String) {
        activeClients[chid]?.let { clientsByChannel ->
            clientsByChannel[smtpLocalPart(userId)]?.unwatch()
            clientsByChannel.remove(smtpLocalPart(userId))
            if (clientsByChannel.isEmpty()) {
                activeClients.remove(chid)
            }
        }
        notifyListeners()
    }

    fun getActiveClientCount(): Int = activeClients.size

    fun getAllClientLoginInfoByChid(chid: String): Collection<PushClientsManager.ClientLoginInfo> {
        return activeClients[chid]?.values?.toList() ?: ArrayList()
    }

    fun getAllClients(): ArrayList<PushClientsManager.ClientLoginInfo> {
        return ArrayList<PushClientsManager.ClientLoginInfo>().apply {
            activeClients.values.forEach { addAll(it.values) }
        }
    }

    fun getClientLoginInfoByChidAndUserId(
        chid: String,
        userId: String,
    ): PushClientsManager.ClientLoginInfo? {
        return activeClients[chid]?.get(smtpLocalPart(userId))
    }

    fun getActiveClientMaps(): Iterable<HashMap<String?, PushClientsManager.ClientLoginInfo>> {
        return activeClients.values
    }

    fun removeActiveClients() {
        getAllClients().forEach { it.unwatch() }
        activeClients.clear()
    }

    fun removeAllClientChangeListeners() {
        clientChangeListeners.clear()
    }

    fun removeClientChangeListener(clientChangeListener: PushClientsManager.ClientChangeListener) {
        clientChangeListeners.remove(clientChangeListener)
    }

    private fun notifyListeners() {
        clientChangeListeners.forEach { it.onChange() }
    }

    private fun smtpLocalPart(userId: String?): String? {
        if (userId.isNullOrEmpty()) return null
        val atIndex = userId.indexOf('@')
        return if (atIndex > 0) userId.substring(0, atIndex) else userId
    }
}
