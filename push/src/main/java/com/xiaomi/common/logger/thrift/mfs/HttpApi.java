package com.xiaomi.common.logger.thrift.mfs;

import java.util.LinkedHashSet;
import java.util.Set;

public class HttpApi {
    private String category;
    private String clientIp;
    private String network;
    private String uuid;
    private String version;
    private String versionType;
    private String appName;
    private String appVersion;
    private Location location;
    private final Set<HostInfo> hostInfo = new LinkedHashSet<>();

    public void setCategory(String category) {
        this.category = category;
    }

    public void setClient_ip(String clientIp) {
        this.clientIp = clientIp;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setVersion_type(String versionType) {
        this.versionType = versionType;
    }

    public void setApp_name(String appName) {
        this.appName = appName;
    }

    public void setApp_version(String appVersion) {
        this.appVersion = appVersion;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public void addToHost_info(HostInfo host) {
        hostInfo.add(host);
    }

    public int getHost_infoSize() {
        return hostInfo.size();
    }
}
