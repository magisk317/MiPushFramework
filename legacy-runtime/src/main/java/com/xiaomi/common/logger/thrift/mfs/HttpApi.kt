package com.xiaomi.common.logger.thrift.mfs

import java.util.LinkedHashSet

class HttpApi {
    private var category: String? = null
    private var clientIp: String? = null
    private var network: String? = null
    private var uuid: String? = null
    private var version: String? = null
    private var versionType: String? = null
    private var appName: String? = null
    private var appVersion: String? = null
    private var location: Location? = null
    private val hostInfo: MutableSet<HostInfo> = LinkedHashSet()

    fun setCategory(category: String?) {
        this.category = category
    }

    fun setClient_ip(clientIp: String?) {
        this.clientIp = clientIp
    }

    fun setNetwork(network: String?) {
        this.network = network
    }

    fun setUuid(uuid: String?) {
        this.uuid = uuid
    }

    fun setVersion(version: String?) {
        this.version = version
    }

    fun setVersion_type(versionType: String?) {
        this.versionType = versionType
    }

    fun setApp_name(appName: String?) {
        this.appName = appName
    }

    fun setApp_version(appVersion: String?) {
        this.appVersion = appVersion
    }

    fun setLocation(location: Location?) {
        this.location = location
    }

    fun addToHost_info(host: HostInfo?) {
        if (host != null) {
            hostInfo.add(host)
        }
    }

    fun getHost_infoSize(): Int = hostInfo.size
}
