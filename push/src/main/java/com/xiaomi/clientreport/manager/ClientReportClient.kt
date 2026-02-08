package com.xiaomi.clientreport.manager

import android.content.Context
import com.magisk317.push.hook.ExplicitHookBridge
import com.xiaomi.clientreport.data.Config
import com.xiaomi.clientreport.data.EventClientReport
import com.xiaomi.clientreport.data.PerfClientReport
import com.xiaomi.clientreport.processor.IEventProcessor
import com.xiaomi.clientreport.processor.IPerfProcessor

class ClientReportClient private constructor() {
    companion object {
        @JvmStatic
        fun init(context: Context) {
            ExplicitHookBridge.onClientReportClientInit()
        }

        @JvmStatic
        fun init(context: Context, config: Config) {
            ExplicitHookBridge.onClientReportClientInit()
        }

        @JvmStatic
        fun init(
            context: Context,
            config: Config,
            eventProcessor: IEventProcessor,
            perfProcessor: IPerfProcessor
        ) {
            ExplicitHookBridge.onClientReportClientInit()
        }

        @JvmStatic
        fun reportEvent(context: Context, report: EventClientReport) {
            // no-op: disable Xiaomi client-report telemetry in de-AOP mode
        }

        @JvmStatic
        fun reportPerf(context: Context, report: PerfClientReport) {
            // no-op: disable Xiaomi client-report telemetry in de-AOP mode
        }

        @JvmStatic
        fun updateConfig(context: Context, config: Config) {
            // no-op: keep API compatibility without telemetry side effects
        }
    }
}
