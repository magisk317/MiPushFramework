package com.xiaomi.common.logger.thrift.mfs

class Location {
    private var city: String? = null
    private var contry: String? = null
    private var province: String? = null
    private var isp: String? = null

    fun setCity(city: String?) {
        this.city = city
    }

    fun setContry(contry: String?) {
        this.contry = contry
    }

    fun setProvince(province: String?) {
        this.province = province
    }

    fun setIsp(isp: String?) {
        this.isp = isp
    }
}
