package com.xiaomi.xmsf.push.service

/**
 * Compat entry for legacy callers that resolve com.xiaomi.push.service.XMPushService.
 * Delegates to MiPushFacadeService to ensure proper Hilt initialization and
 * runtimeObserver setup before handling any push service requests.
 */
class CompatXMPushService : MiPushFacadeService()
