package com.xiaomi.common.logger.thrift.mfs;

public class Location {
    private String city;
    private String contry;
    private String province;
    private String isp;

    public void setCity(String city) {
        this.city = city;
    }

    public void setContry(String contry) {
        this.contry = contry;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public void setIsp(String isp) {
        this.isp = isp;
    }
}
