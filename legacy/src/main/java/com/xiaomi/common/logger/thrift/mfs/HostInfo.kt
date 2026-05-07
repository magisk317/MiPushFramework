package com.xiaomi.common.logger.thrift.mfs

class HostInfo {
    private var host: String? = null
    private var landNodeInfo: List<LandNodeInfo>? = null

    fun setHost(host: String?) {
        this.host = host
    }

    fun setLand_node_info(landNodeInfo: List<LandNodeInfo>?) {
        this.landNodeInfo = landNodeInfo
    }
}
