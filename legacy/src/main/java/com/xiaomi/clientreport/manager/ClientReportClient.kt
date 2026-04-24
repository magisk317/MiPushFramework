package com.xiaomi.clientreport.manager
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import com.xiaomi.clientreport.data.Config
import com.xiaomi.clientreport.data.EventClientReport
import com.xiaomi.clientreport.data.PerfClientReport
import com.xiaomi.clientreport.processor.IEventProcessor
import com.xiaomi.clientreport.processor.IPerfProcessor

class ClientReportClient private constructor() {
    companion object {
        @JvmStatic
        fun init(context: Context) {
            // no-op: tracing hook removed during legacy extraction
        }

        @JvmStatic
        fun init(context: Context, config: Config) {
            // no-op: tracing hook removed during legacy extraction
        }

        @JvmStatic
        fun init(
            context: Context,
            config: Config,
            eventProcessor: IEventProcessor,
            perfProcessor: IPerfProcessor
        ) {
            // no-op: tracing hook removed during legacy extraction
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
