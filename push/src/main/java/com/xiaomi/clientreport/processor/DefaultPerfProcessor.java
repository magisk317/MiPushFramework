package com.xiaomi.clientreport.processor;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.clientreport.data.BaseClientReport;
import com.xiaomi.clientreport.data.PerfClientReport;
import com.xiaomi.clientreport.util.ClientReportUtil;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/clientreport/processor/DefaultPerfProcessor.class */
public class DefaultPerfProcessor implements IPerfProcessor {
    private static final String FOLDER = "perf";
    private static final int MAX_SAME_PRODUCTION_FILE_NUM = 20;
    protected static final String SEPARATOR = "#";
    private static final int SLEEP_NUM = 25;
    private static final String UPLOAD_FOLDER = "perfUploading";
    protected Context mContext;
    private HashMap<String, HashMap<String, BaseClientReport>> mPerfMap;

    public DefaultPerfProcessor(Context context) {
        this.mContext = context;
    }

    public static String getFirstPerfFileName(BaseClientReport baseClientReport) {
        return String.valueOf(baseClientReport.production) + "#" + baseClientReport.clientInterfaceId;
    }

    private String getOriginalFilePath(BaseClientReport baseClientReport) {
        String absolutePath;
        int i = baseClientReport.production;
        String str = baseClientReport.clientInterfaceId;
        String str2 = "";
        if (i > 0) {
            str2 = "";
            if (!TextUtils.isEmpty(str)) {
                str2 = String.valueOf(i) + "#" + str;
            }
        }
        File externalFilesDir = this.mContext.getExternalFilesDir(FOLDER);
        if (externalFilesDir == null) {
            MyLog.e("cannot get folder when to write perf");
            absolutePath = null;
        } else {
            if (!externalFilesDir.exists()) {
                externalFilesDir.mkdirs();
            }
            absolutePath = new File(externalFilesDir, str2).getAbsolutePath();
        }
        return absolutePath;
    }

    private String getWriteFileName(BaseClientReport baseClientReport) {
        String str;
        String originalFilePath = getOriginalFilePath(baseClientReport);
        if (TextUtils.isEmpty(originalFilePath)) {
            return null;
        }
        int i = 0;
        while (true) {
            str = null;
            if (i >= 20) {
                break;
            }
            str = originalFilePath + i;
            if (ClientReportUtil.isFileCanBeUse(this.mContext, str)) {
                break;
            }
            i++;
        }
        return str;
    }

    @Override // com.xiaomi.clientreport.processor.IWrite
    public void preProcess(BaseClientReport baseClientReport) {
        if ((baseClientReport instanceof PerfClientReport) && this.mPerfMap != null) {
            PerfClientReport perfClientReport = (PerfClientReport) baseClientReport;
            String firstPerfFileName = getFirstPerfFileName(perfClientReport);
            String strGenerateKey = PerfKVFileHelper.generateKey(perfClientReport);
            HashMap<String, BaseClientReport> map = this.mPerfMap.get(firstPerfFileName);
            HashMap<String, BaseClientReport> map2 = map;
            if (map == null) {
                map2 = new HashMap<>();
            }
            PerfClientReport perfClientReport2 = (PerfClientReport) map2.get(strGenerateKey);
            if (perfClientReport2 != null) {
                perfClientReport.perfCounts += perfClientReport2.perfCounts;
                perfClientReport.perfLatencies += perfClientReport2.perfLatencies;
            }
            map2.put(strGenerateKey, perfClientReport);
            this.mPerfMap.put(firstPerfFileName, map2);
        }
    }

    @Override // com.xiaomi.clientreport.processor.IWrite
    public void process() {
        HashMap<String, HashMap<String, BaseClientReport>> map = this.mPerfMap;
        if (map == null) {
            return;
        }
        if (map.size() > 0) {
            Iterator<String> it = this.mPerfMap.keySet().iterator();
            while (it.hasNext()) {
                HashMap<String, BaseClientReport> map2 = this.mPerfMap.get(it.next());
                if (map2 != null && map2.size() > 0) {
                    BaseClientReport[] baseClientReportArr = new BaseClientReport[map2.size()];
                    map2.values().toArray(baseClientReportArr);
                    write(baseClientReportArr);
                }
            }
        }
        this.mPerfMap.clear();
    }

    @Override // com.xiaomi.clientreport.processor.IDataSend
    public void readAndSend() {
        ClientReportUtil.moveFiles(this.mContext, FOLDER, UPLOAD_FOLDER);
        File[] readFileName = ClientReportUtil.getReadFileName(this.mContext, UPLOAD_FOLDER);
        if (readFileName == null || readFileName.length <= 0) {
            return;
        }
        for (File file : readFileName) {
            if (file != null) {
                List<String> listExtractToDatas = PerfKVFileHelper.extractToDatas(this.mContext, file.getAbsolutePath());
                file.delete();
                send(listExtractToDatas);
            }
        }
    }

    @Override // com.xiaomi.clientreport.processor.IDataSend
    public void send(List<String> list) {
        ClientReportUtil.sendFile(this.mContext, list);
    }

    @Override // com.xiaomi.clientreport.processor.IPerfProcessor
    public void setPerfMap(HashMap<String, HashMap<String, BaseClientReport>> map) {
        this.mPerfMap = map;
    }

    @Override // com.xiaomi.clientreport.processor.IWrite
    public void write(BaseClientReport[] baseClientReportArr) {
        String writeFileName = getWriteFileName(baseClientReportArr[0]);
        if (TextUtils.isEmpty(writeFileName)) {
            return;
        }
        PerfKVFileHelper.put(writeFileName, baseClientReportArr);
    }
}
