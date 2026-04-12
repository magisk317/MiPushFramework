package com.xiaomi.clientreport.processor;

import android.content.Context;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.clientreport.data.BaseClientReport;
import com.xiaomi.clientreport.data.ClientReportConstants;
import com.xiaomi.clientreport.data.PerfClientReport;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/clientreport/processor/PerfKVFileHelper.class */
public class PerfKVFileHelper {
    private static PerfClientReport buildPerfClientReport(PerfClientReport perfClientReport, String str) {
        long[] valueStr;
        if (perfClientReport == null || (valueStr = parseValueStr(str)) == null) {
            return null;
        }
        perfClientReport.perfCounts = valueStr[0];
        perfClientReport.perfLatencies = valueStr[1];
        return perfClientReport;
    }

    public static List<String> extractToDatas(Context context, String str) {
        List<String> arrayList = new ArrayList<>();
        if (TextUtils.isEmpty(str) || !new File(str).exists()) {
            return arrayList;
        }
        BufferedReader bufferedReader = null;
        RandomAccessFile randomAccessFile = null;
        FileLock fileLock = null;
        File file = null;
        BufferedReader bufferedReader2 = null;
        RandomAccessFile randomAccessFile2 = null;
        FileLock fileLock2 = null;
        File file2 = null;
        try {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(str);
                sb.append(".lock");
                File file3 = new File(sb.toString());
                IOUtils.createFileQuietly(file3);
                RandomAccessFile randomAccessFile3 = new RandomAccessFile(file3, "rw");
                FileLock fileLockLock = randomAccessFile3.getChannel().lock();
                BufferedReader bufferedReader3 = new BufferedReader(new FileReader(str));
                while (true) {
                    bufferedReader = bufferedReader3;
                    randomAccessFile = randomAccessFile3;
                    fileLock = fileLockLock;
                    file = file3;
                    bufferedReader2 = bufferedReader3;
                    randomAccessFile2 = randomAccessFile3;
                    fileLock2 = fileLockLock;
                    file2 = file3;
                    String line = bufferedReader3.readLine();
                    if (line == null) {
                        break;
                    }
                    String[] strArrSplit = line.split(ClientReportConstants.SEPARATOR);
                    if (strArrSplit.length >= 2 && !TextUtils.isEmpty(strArrSplit[0]) && !TextUtils.isEmpty(strArrSplit[1])) {
                        PerfClientReport perfClientReportBuildPerfClientReport = buildPerfClientReport(spiltKeyForModel(strArrSplit[0]), strArrSplit[1]);
                        if (perfClientReportBuildPerfClientReport != null) {
                            arrayList.add(perfClientReportBuildPerfClientReport.toJsonString());
                        }
                    }
                }
                if (fileLockLock != null && fileLockLock.isValid()) {
                    try {
                        fileLockLock.release();
                    } catch (IOException e) {
                        MyLog.e(e);
                    }
                }
                IOUtils.closeQuietly(randomAccessFile3);
                IOUtils.closeQuietly(bufferedReader3);
                file2 = file3;
            } catch (Exception e2) {
                bufferedReader = bufferedReader2;
                randomAccessFile = randomAccessFile2;
                fileLock = fileLock2;
                file = file2;
                MyLog.e(e2);
                if (fileLock2 != null && fileLock2.isValid()) {
                    try {
                        fileLock2.release();
                    } catch (IOException e3) {
                        MyLog.e(e3);
                    }
                }
                IOUtils.closeQuietly(randomAccessFile2);
                IOUtils.closeQuietly(bufferedReader2);
                if (file2 != null) {
                }
                return arrayList;
            }
            file2.delete();
            return arrayList;
        } catch (Throwable th) {
            if (fileLock != null && fileLock.isValid()) {
                try {
                    fileLock.release();
                } catch (IOException e4) {
                    MyLog.e(e4);
                }
            }
            IOUtils.closeQuietly(randomAccessFile);
            IOUtils.closeQuietly(bufferedReader);
            if (file != null) {
                file.delete();
            }
            throw th;
        }
    }

    public static String generateKey(PerfClientReport perfClientReport) {
        return perfClientReport.production + "#" + perfClientReport.clientInterfaceId + "#" + perfClientReport.reportType + "#" + perfClientReport.code;
    }

    protected static long[] parseValueStr(String str) {
        long[] jArr = new long[2];
        try {
            String[] strArrSplit = str.split("#");
            if (strArrSplit.length >= 2) {
                jArr[0] = Long.parseLong(strArrSplit[0].trim());
                jArr[1] = Long.parseLong(strArrSplit[1].trim());
            }
            return jArr;
        } catch (Exception e) {
            MyLog.e(e);
            return null;
        }
    }

    /* JADX WARN: Finally extract failed */
    public static void put(String str, BaseClientReport[] baseClientReportArr) {
        RandomAccessFile randomAccessFile;
        if (baseClientReportArr == null || baseClientReportArr.length <= 0 || TextUtils.isEmpty(str)) {
            return;
        }
        RandomAccessFile randomAccessFile2 = null;
        FileLock fileLock = null;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(str);
            sb.append(".lock");
            File file = new File(sb.toString());
            IOUtils.createFileQuietly(file);
            RandomAccessFile randomAccessFile3 = new RandomAccessFile(file, "rw");
            FileLock fileLockLock = randomAccessFile3.getChannel().lock();
            HashMap<String, String> fromFile = readFromFile(str);
            for (BaseClientReport baseClientReport : baseClientReportArr) {
                if (baseClientReport != null) {
                    String strGenerateKey = generateKey((PerfClientReport) baseClientReport);
                    long j = ((PerfClientReport) baseClientReport).perfCounts;
                    long j2 = ((PerfClientReport) baseClientReport).perfLatencies;
                    if (!TextUtils.isEmpty(strGenerateKey) && j > 0 && j2 >= 0) {
                        putInMemeory(fromFile, strGenerateKey, j, j2);
                    }
                }
            }
            randomAccessFile2 = randomAccessFile3;
            fileLock = fileLockLock;
            writeToFile(str, fromFile);
            randomAccessFile = randomAccessFile3;
            if (fileLockLock != null) {
                randomAccessFile = randomAccessFile3;
                if (fileLockLock.isValid()) {
                    try {
                        fileLockLock.release();
                        randomAccessFile = randomAccessFile3;
                    } catch (IOException e) {
                        e = e;
                        randomAccessFile = randomAccessFile3;
                        MyLog.e(e);
                    }
                }
            }
        } catch (Throwable th) {
            try {
                MyLog.v("failed to write perf to file ");
                randomAccessFile = randomAccessFile2;
                if (fileLock != null) {
                    randomAccessFile = randomAccessFile2;
                    if (fileLock.isValid()) {
                        try {
                            fileLock.release();
                            randomAccessFile = randomAccessFile2;
                        } catch (IOException e2) {
                            randomAccessFile = randomAccessFile2;
                            MyLog.e(e2);
                        }
                    }
                }
            } catch (Throwable th2) {
                if (fileLock != null && fileLock.isValid()) {
                    try {
                        fileLock.release();
                    } catch (IOException e3) {
                        MyLog.e(e3);
                    }
                }
                IOUtils.closeQuietly(randomAccessFile2);
                throw th2;
            }
        }
        IOUtils.closeQuietly(randomAccessFile);
    }

    private static void putInMemeory(HashMap<String, String> map, String str, long j, long j2) {
        String str2;
        String str3 = map.get(str);
        if (TextUtils.isEmpty(str3)) {
            map.put(str, j + "#" + j2);
            return;
        }
        long[] valueStr = parseValueStr(str3);
        if (valueStr == null || valueStr[0] <= 0 || valueStr[1] < 0) {
            str2 = j + "#" + j2;
        } else {
            str2 = (valueStr[0] + j) + "#" + (valueStr[1] + j2);
        }
        map.put(str, str2);
    }

    private static HashMap<String, String> readFromFile(String str) {
        BufferedReader bufferedReader;
        HashMap<String, String> map = new HashMap<>();
        if (TextUtils.isEmpty(str) || !new File(str).exists()) {
            return map;
        }
        BufferedReader bufferedReader2 = null;
        BufferedReader bufferedReader3 = null;
        try {
            try {
                bufferedReader = new BufferedReader(new FileReader(str));
                while (true) {
                    bufferedReader2 = bufferedReader;
                    bufferedReader3 = bufferedReader;
                    String line = bufferedReader.readLine();
                    if (line == null) {
                        break;
                    }
                    String[] strArrSplit = line.split(ClientReportConstants.SEPARATOR);
                    if (strArrSplit.length >= 2 && !TextUtils.isEmpty(strArrSplit[0]) && !TextUtils.isEmpty(strArrSplit[1])) {
                        map.put(strArrSplit[0], strArrSplit[1]);
                    }
                }
            } catch (Exception e) {
                MyLog.e(e);
                bufferedReader = bufferedReader3;
            }
            return map;
        } finally {
            IOUtils.closeQuietly(bufferedReader2);
        }
    }

    private static String[] spiltKey(String str) {
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        return str.split("#");
    }

    private static PerfClientReport spiltKeyForModel(String str) {
        PerfClientReport blankInstance;
        PerfClientReport perfClientReport = null;
        try {
            String[] strArrSpiltKey = spiltKey(str);
            blankInstance = null;
            if (strArrSpiltKey != null) {
                blankInstance = null;
                if (strArrSpiltKey.length >= 4) {
                    blankInstance = null;
                    if (!TextUtils.isEmpty(strArrSpiltKey[0])) {
                        blankInstance = null;
                        if (!TextUtils.isEmpty(strArrSpiltKey[1])) {
                            blankInstance = null;
                            if (!TextUtils.isEmpty(strArrSpiltKey[2])) {
                                blankInstance = null;
                                if (!TextUtils.isEmpty(strArrSpiltKey[3])) {
                                    blankInstance = PerfClientReport.getBlankInstance();
                                    blankInstance.production = Integer.parseInt(strArrSpiltKey[0]);
                                    blankInstance.clientInterfaceId = strArrSpiltKey[1];
                                    blankInstance.reportType = Integer.parseInt(strArrSpiltKey[2]);
                                    perfClientReport = blankInstance;
                                    blankInstance.code = Integer.parseInt(strArrSpiltKey[3]);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            MyLog.v("parse per key error");
            blankInstance = perfClientReport;
        }
        return blankInstance;
    }

    private static void writeToFile(String str, HashMap<String, String> map) {
        if (TextUtils.isEmpty(str) || map == null || map.size() == 0) {
            return;
        }
        File file = new File(str);
        if (file.exists()) {
            file.delete();
        }
        BufferedWriter bufferedWriter = null;
        BufferedWriter bufferedWriter2 = null;
        try {
            try {
                BufferedWriter bufferedWriter3 = new BufferedWriter(new FileWriter(file));
                Iterator<String> it = map.keySet().iterator();
                while (true) {
                    bufferedWriter = bufferedWriter3;
                    bufferedWriter2 = bufferedWriter3;
                    if (!it.hasNext()) {
                        break;
                    }
                    String next = it.next();
                    String str2 = map.get(next);
                    StringBuilder sb = new StringBuilder();
                    sb.append(next);
                    sb.append(ClientReportConstants.SEPARATOR);
                    sb.append(str2);
                    bufferedWriter3.write(sb.toString());
                    bufferedWriter3.newLine();
                }
                bufferedWriter2 = bufferedWriter3;
            } catch (Exception e) {
                MyLog.e(e);
            }
            IOUtils.closeQuietly(bufferedWriter2);
        } catch (Throwable th) {
            IOUtils.closeQuietly(bufferedWriter);
            throw th;
        }
    }
}
