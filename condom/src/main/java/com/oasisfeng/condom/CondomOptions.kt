package com.oasisfeng.condom

class CondomOptions {
    fun preventBroadcastToBackgroundPackages(preventOrNot: Boolean): CondomOptions = this

    fun setOutboundJudge(judge: OutboundJudge?): CondomOptions = this

    fun addKit(kit: CondomKit): CondomOptions = this
}
