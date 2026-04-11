package com.xiaomi.common.logger.thrift.mfs;

import java.util.List;

public class HostInfo {
    private String host;
    private List<LandNodeInfo> landNodeInfo;

    public void setHost(String host) {
        this.host = host;
    }

    public void setLand_node_info(List<LandNodeInfo> landNodeInfo) {
        this.landNodeInfo = landNodeInfo;
    }
}
