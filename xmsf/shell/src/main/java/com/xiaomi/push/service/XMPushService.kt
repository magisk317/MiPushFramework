package com.xiaomi.push.service

import com.xiaomi.xmsf.push.service.MiPushFacadeService

/**
 * Public MiPush SDK ABI for XMSF versions 106 and newer.
 *
 * The actual connection runtime lives in [XMPushServiceCore]. This component only admits
 * validated app transport requests before forwarding them to that private service.
 */
class XMPushService : MiPushFacadeService() {
    override val isExternalIngress: Boolean = true
}
