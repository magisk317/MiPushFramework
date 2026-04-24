package com.xiaomi.clientreport.manager
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ScheduledJobConstants
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.channel.commonutils.string.XMStringUtils
import com.xiaomi.clientreport.data.BaseClientReport
import com.xiaomi.clientreport.data.Config
import com.xiaomi.clientreport.data.EventClientReport
import com.xiaomi.clientreport.data.PerfClientReport
import com.xiaomi.clientreport.job.EventUploadJob
import com.xiaomi.clientreport.job.PerfUploadJob
import com.xiaomi.clientreport.job.ReadAndSendJob
import com.xiaomi.clientreport.processor.IEventProcessor
import com.xiaomi.clientreport.processor.IPerfProcessor
import com.xiaomi.clientreport.util.ClientReportUtil
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ClientReportLogicManager private constructor(private val mContext: Context) {

    companion object {
        private const val WRITE_THRESHOLD = 10
        private val DELAY_TO_FILE: Int = if (MIUIUtils.isMIUI()) 30 else 10

        @Volatile
        private var sInstance: ClientReportLogicManager? = null

        @JvmStatic
        fun getInstance(context: Context): ClientReportLogicManager {
            return sInstance ?: synchronized(ClientReportLogicManager::class.java) {
                sInstance ?: ClientReportLogicManager(context).also { sInstance = it }
            }
        }
    }

    private var PUSH_VERSION_NAME: String? = null
    private var mConfig: Config? = null
    private var mEventProcessor: IEventProcessor? = null
    private var mPerfProcessor: IPerfProcessor? = null
    private val mExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mPerfMap = HashMap<String, HashMap<String, BaseClientReport>>()
    private val mEventMap = HashMap<String, ArrayList<BaseClientReport>>()

    private fun delayRunJob(job: ScheduledJobManager.Job, i: Int) {
        ScheduledJobManager.getInstance(mContext).addOneShootJob(job, i)
    }

    private fun getEventCacheCountInMemory(): Int {
        var size = 0
        val map = mEventMap
        if (map.isNotEmpty()) {
            for (key in map.keys) {
                val arrayList = mEventMap[key]
                size += arrayList?.size ?: 0
            }
        }
        return size
    }

    private fun getPerfCacheCountInMemory(): Int {
        var i2 = 0
        val map = mPerfMap
        if (map.isNotEmpty()) {
            for (key in map.keys) {
                val map2 = mPerfMap[key]
                if (map2 != null && map2.isNotEmpty()) {
                    for (key2 in map2.keys) {
                        val baseClientReport = map2[key2]
                        if (baseClientReport is PerfClientReport) {
                            i2 = (i2.toLong() + baseClientReport.perfCounts).toInt()
                        }
                    }
                }
            }
        }
        return i2
    }

    private fun processEventData(eventClientReport: EventClientReport) {
        val iEventProcessor = mEventProcessor ?: return
        iEventProcessor.preProcess(eventClientReport)
        if (getEventCacheCountInMemory() < WRITE_THRESHOLD) {
            delayRunJob(
                object : ScheduledJobManager.Job() {
                    override fun getJobId(): String = ScheduledJobConstants.CHECK_WRITE_EVENT_JOB_ID

                    override fun run() {
                        if (getEventCacheCountInMemory() > 0) {
                            mExecutor.execute { writeEvent2File() }
                        }
                    }
                },
                DELAY_TO_FILE,
            )
        } else {
            writeEvent2File()
            ScheduledJobManager.getInstance(mContext).cancelJob(
                ScheduledJobConstants.CHECK_WRITE_EVENT_JOB_ID,
            )
        }
    }

    private fun processPerfData(perfClientReport: PerfClientReport) {
        val iPerfProcessor = mPerfProcessor ?: return
        iPerfProcessor.preProcess(perfClientReport)
        if (getPerfCacheCountInMemory() < WRITE_THRESHOLD) {
            delayRunJob(
                object : ScheduledJobManager.Job() {
                    override fun getJobId(): String = ScheduledJobConstants.CHECK_WRITE_PERF_JOB_ID

                    override fun run() {
                        if (getPerfCacheCountInMemory() > 0) {
                            mExecutor.execute { writePerf2File() }
                        }
                    }
                },
                DELAY_TO_FILE,
            )
        } else {
            writePerf2File()
            ScheduledJobManager.getInstance(mContext).cancelJob(
                ScheduledJobConstants.CHECK_WRITE_PERF_JOB_ID,
            )
        }
    }

    private fun startEventUploadJob() {
        if (getInstance(mContext).config.isEventUploadSwitchOpen) {
            val eventUploadJob = EventUploadJob(mContext)
            var i = getInstance(mContext).config.eventUploadFrequency.toInt()
            if (i < 1800) {
                i = 1800
            }
            if (System.currentTimeMillis() - com.xiaomi.clientreport.util.SPManager.getInstance(mContext)
                    .getLongValue(
                        com.xiaomi.clientreport.data.ClientReportConstants.SP_FILE_STATUS,
                        "event_last_upload_time",
                        0L,
                    ) > i * 1000
            ) {
                ScheduledJobManager.getInstance(mContext).addOneShootJob(
                    { eventUploadJob.run() },
                    10,
                )
            }
            synchronized(ClientReportLogicManager::class.java) {
                if (!ScheduledJobManager.getInstance(mContext)
                        .addRepeatJob(eventUploadJob, i)
                ) {
                    ScheduledJobManager.getInstance(mContext)
                        .cancelJob(ScheduledJobConstants.EVENT_UPLOAD_JOB_ID)
                    ScheduledJobManager.getInstance(mContext)
                        .addRepeatJob(eventUploadJob, i)
                }
            }
        }
    }

    private fun startPerfUploadJob() {
        if (getInstance(mContext).config.isPerfUploadSwitchOpen) {
            val perfUploadJob = PerfUploadJob(mContext)
            var i = getInstance(mContext).config.perfUploadFrequency.toInt()
            if (i < 1800) {
                i = 1800
            }
            if (System.currentTimeMillis() - com.xiaomi.clientreport.util.SPManager.getInstance(mContext)
                    .getLongValue(
                        com.xiaomi.clientreport.data.ClientReportConstants.SP_FILE_STATUS,
                        "perf_last_upload_time",
                        0L,
                    ) > i * 1000
            ) {
                ScheduledJobManager.getInstance(mContext).addOneShootJob(
                    { perfUploadJob.run() },
                    15,
                )
            }
            synchronized(ClientReportLogicManager::class.java) {
                if (!ScheduledJobManager.getInstance(mContext)
                        .addRepeatJob(perfUploadJob, i)
                ) {
                    ScheduledJobManager.getInstance(mContext)
                        .cancelJob(ScheduledJobConstants.PERF_UPLOAD_JOB_ID)
                    ScheduledJobManager.getInstance(mContext)
                        .addRepeatJob(perfUploadJob, i)
                }
            }
        }
    }

    private fun writeEvent2File() {
        try {
            mEventProcessor?.process()
        } catch (e: Exception) {
            MyLog.e("we: ${e.message}")
        }
    }

    private fun writePerf2File() {
        try {
            mPerfProcessor?.process()
        } catch (e: Exception) {
            MyLog.e("wp: ${e.message}")
        }
    }

    val config: Config
        get() {
            synchronized(this) {
                if (mConfig == null) {
                    mConfig = Config.defaultConfig(mContext)
                }
                return mConfig!!
            }
        }

    fun init(
        config: Config,
        iEventProcessor: IEventProcessor,
        iPerfProcessor: IPerfProcessor,
    ) {
        mConfig = config
        mEventProcessor = iEventProcessor
        mPerfProcessor = iPerfProcessor
        iEventProcessor.setEventMap(mEventMap)
        iPerfProcessor.setPerfMap(mPerfMap)
    }

    fun newEvent(i: Int, str: String): EventClientReport {
        return EventClientReport().apply {
            eventContent = str
            eventTime = System.currentTimeMillis()
            eventType = i
            eventId = XMStringUtils.generateRandomString(6)
            production = 1000
            reportType = 1001
            clientInterfaceId = "E100004"
            setAppPackageName(mContext.packageName)
            setSdkVersion(PUSH_VERSION_NAME ?: "")
        }
    }

    fun prepareInit(str: String) {
        PUSH_VERSION_NAME = str
    }

    fun sendEvent() {
        if (config.isEventUploadSwitchOpen) {
            val readAndSendJob = ReadAndSendJob()
            readAndSendJob.setContext(mContext)
            readAndSendJob.setReadAndSender(mEventProcessor!!)
            mExecutor.execute(readAndSendJob)
        }
    }

    fun sendPerf() {
        if (config.isPerfUploadSwitchOpen) {
            val readAndSendJob = ReadAndSendJob()
            readAndSendJob.setReadAndSender(mPerfProcessor!!)
            readAndSendJob.setContext(mContext)
            mExecutor.execute(readAndSendJob)
        }
    }

    fun startScheduleJob() {
        getInstance(mContext).startEventUploadJob()
        getInstance(mContext).startPerfUploadJob()
    }

    fun updateConfig(
        z: Boolean,
        z2: Boolean,
        j: Long,
        j2: Long,
    ) {
        val config = mConfig ?: return
        if (z == config.isEventUploadSwitchOpen &&
            z2 == mConfig!!.isPerfUploadSwitchOpen &&
            j == mConfig!!.eventUploadFrequency &&
            j2 == mConfig!!.perfUploadFrequency
        ) {
            return
        }
        val eventUploadFrequency = mConfig!!.eventUploadFrequency
        val perfUploadFrequency = mConfig!!.perfUploadFrequency
        val configBuild = Config.getBuilder()
            .setAESKey(ClientReportUtil.getEventKeyWithDefault(mContext))
            .setEventEncrypted(mConfig!!.isEventEncrypted)
            .setEventUploadSwitchOpen(z)
            .setEventUploadFrequency(j)
            .setPerfUploadSwitchOpen(z2)
            .setPerfUploadFrequency(j2)
            .build(mContext)
        mConfig = configBuild
        if (!configBuild.isEventUploadSwitchOpen) {
            ScheduledJobManager.getInstance(mContext)
                .cancelJob(ScheduledJobConstants.EVENT_UPLOAD_JOB_ID)
        } else if (eventUploadFrequency != configBuild.eventUploadFrequency) {
            MyLog.v("${mContext.packageName} reset event job ${configBuild.eventUploadFrequency}")
            startEventUploadJob()
        }
        if (!mConfig!!.isPerfUploadSwitchOpen) {
            ScheduledJobManager.getInstance(mContext)
                .cancelJob(ScheduledJobConstants.PERF_UPLOAD_JOB_ID)
            return
        }
        if (perfUploadFrequency != configBuild.perfUploadFrequency) {
            MyLog.v("${mContext.packageName} reset perf job ${configBuild.perfUploadFrequency}")
            startPerfUploadJob()
        }
    }

    fun writeEvent(eventClientReport: EventClientReport) {
        if (config.isEventUploadSwitchOpen) {
            mExecutor.execute { processEventData(eventClientReport) }
        }
    }

    fun writePerf(perfClientReport: PerfClientReport) {
        if (config.isPerfUploadSwitchOpen) {
            mExecutor.execute { processPerfData(perfClientReport) }
        }
    }
}
