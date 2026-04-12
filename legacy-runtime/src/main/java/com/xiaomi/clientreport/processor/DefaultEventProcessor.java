package com.xiaomi.clientreport.processor;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Base64;
import com.xiaomi.channel.commonutils.android.DataCryptUtils;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.ByteUtils;
import com.xiaomi.channel.commonutils.string.XMStringUtils;
import com.xiaomi.clientreport.data.BaseClientReport;
import com.xiaomi.clientreport.data.EventClientReport;
import com.xiaomi.clientreport.manager.ClientReportLogicManager;
import com.xiaomi.clientreport.util.ClientReportUtil;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/clientreport/processor/DefaultEventProcessor.class */
public class DefaultEventProcessor implements IEventProcessor {
    private static final int DATA_FILE_MAX_SIZE = 5242880;
    private static final int DATA_MAX_SIZE = 4096;
    private static final String FOLDER = "event";
    private static final int MAGIC_NUMBER = -573785174;
    private static final int MAX_SAME_PRODUCTION_FILE_NUM = 100;
    private static final String UPLOAD_FOLDER = "eventUploading";
    protected Context mContext;
    private HashMap<String, ArrayList<BaseClientReport>> mEventMap;

    public DefaultEventProcessor(Context context) {
        setContext(context);
    }

    public static String getFirstEventFileName(BaseClientReport baseClientReport) {
        return String.valueOf(baseClientReport.production);
    }

    private String getWriteFileName(BaseClientReport baseClientReport) {
        String str;
        File externalFilesDir = this.mContext.getExternalFilesDir("event");
        String firstEventFileName = getFirstEventFileName(baseClientReport);
        if (externalFilesDir == null) {
            return null;
        }
        String str2 = externalFilesDir.getAbsolutePath() + File.separator + firstEventFileName;
        int i = 0;
        while (true) {
            str = null;
            if (i >= 100) {
                break;
            }
            str = str2 + i;
            if (ClientReportUtil.isFileCanBeUse(this.mContext, str)) {
                break;
            }
            i++;
        }
        return str;
    }

    private List<String> readFile(String str) {
        List<String> arrayList = new ArrayList<>();
        BufferedInputStream bufferedInputStream = null;
        try {
            bufferedInputStream = new BufferedInputStream(new FileInputStream(new File(str)));
            byte[] bArr = new byte[4];
            while (true) {
                int read = bufferedInputStream.read();
                if (read == -1) {
                    break;
                }
                bArr[0] = (byte) read;
                if (bufferedInputStream.read(bArr, 1, 3) != 3) {
                    MyLog.e("eventData read from cache file failed cause magicNumber error");
                    break;
                }
                if (ByteUtils.toInt(bArr) != MAGIC_NUMBER) {
                    MyLog.e("eventData read from cache file failed cause magicNumber error");
                    break;
                }
                if (bufferedInputStream.read(bArr) != 4) {
                    MyLog.e("eventData read from cache file failed cause lengthBuffer error");
                    break;
                }
                int i = ByteUtils.toInt(bArr);
                if (i < 1 || i > 4096) {
                    MyLog.e("eventData read from cache file failed cause lengthBuffer < 1 || lengthBuffer > 4K");
                    break;
                }
                byte[] bArr2 = new byte[i];
                int i2 = 0;
                while (i2 < i) {
                    int read2 = bufferedInputStream.read(bArr2, i2, i - i2);
                    if (read2 == -1) {
                        break;
                    }
                    i2 += read2;
                }
                if (i2 != i) {
                    MyLog.e("eventData read from cache file failed cause buffer size not equal length");
                    break;
                }
                String strBytesToString = bytesToString(bArr2);
                if (!TextUtils.isEmpty(strBytesToString)) {
                    arrayList.add(strBytesToString);
                }
            }
        } catch (Exception e) {
            MyLog.e(e);
        } finally {
            IOUtils.closeQuietly(bufferedInputStream);
        }
        return arrayList;
    }

    private void releaseLock(RandomAccessFile randomAccessFile, FileLock fileLock) {
        if (fileLock != null && fileLock.isValid()) {
            try {
                fileLock.release();
            } catch (IOException e) {
                MyLog.e(e);
            }
        }
        IOUtils.closeQuietly(randomAccessFile);
    }

    private void reportDropFile(String str, String str2) {
        EventClientReport eventClientReportNewEvent = ClientReportLogicManager.getInstance(this.mContext).newEvent(5001, "24:" + str + "," + str2);
        List<String> arrayList = new ArrayList<>(1);
        arrayList.add(eventClientReportNewEvent.toJsonString());
        send(arrayList);
    }

    private BaseClientReport[] write2FileLocked(BaseClientReport[] baseClientReportArr) {
        String writeFileName = getWriteFileName(baseClientReportArr[0]);
        if (TextUtils.isEmpty(writeFileName)) {
            return null;
        }
        RandomAccessFile randomAccessFile = null;
        FileLock fileLock = null;
        BufferedOutputStream bufferedOutputStream = null;
        try {
            File file = new File(writeFileName + ".lock");
            IOUtils.createFileQuietly(file);
            randomAccessFile = new RandomAccessFile(file, "rw");
            fileLock = randomAccessFile.getChannel().lock();
            bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(new File(writeFileName), true));
            for (int i = 0; i < baseClientReportArr.length; i++) {
                BaseClientReport baseClientReport = baseClientReportArr[i];
                if (baseClientReport != null) {
                    byte[] bArrStringToBytes = stringToBytes(baseClientReport.toJsonString());
                    if (bArrStringToBytes != null && bArrStringToBytes.length >= 1 && bArrStringToBytes.length <= 4096) {
                        if (!ClientReportUtil.isFileCanBeUse(this.mContext, writeFileName)) {
                            int length = baseClientReportArr.length - i;
                            BaseClientReport[] baseClientReportArr2 = new BaseClientReport[length];
                            System.arraycopy(baseClientReportArr, i, baseClientReportArr2, 0, length);
                            return baseClientReportArr2;
                        }
                        bufferedOutputStream.write(ByteUtils.parseInt(MAGIC_NUMBER));
                        bufferedOutputStream.write(ByteUtils.parseInt(bArrStringToBytes.length));
                        bufferedOutputStream.write(bArrStringToBytes);
                        bufferedOutputStream.flush();
                    } else {
                        MyLog.e("event data throw a invalid item ");
                    }
                }
            }
            return null;
        } catch (Exception e) {
            MyLog.e("event data write to cache file failed cause exception", e);
            return null;
        } catch (Throwable th) {
            IOUtils.closeQuietly(bufferedOutputStream);
            releaseLock(randomAccessFile, fileLock);
            if (th instanceof RuntimeException) {
                throw (RuntimeException) th;
            }
            throw new RuntimeException(th);
        } finally {
            IOUtils.closeQuietly(bufferedOutputStream);
            releaseLock(randomAccessFile, fileLock);
        }
    }

    @Override // com.xiaomi.clientreport.processor.IEventProcessor
    public String bytesToString(byte[] bArr) {
        byte[] key;
        if (bArr == null || bArr.length < 1) {
            return null;
        }
        if (!ClientReportLogicManager.getInstance(this.mContext).getConfig().isEventEncrypted()) {
            return XMStringUtils.bytesToString(bArr);
        }
        String eventKeyWithDefault = ClientReportUtil.getEventKeyWithDefault(this.mContext);
        if (TextUtils.isEmpty(eventKeyWithDefault) || (key = ClientReportUtil.parseKey(eventKeyWithDefault)) == null || key.length <= 0) {
            return null;
        }
        try {
            return XMStringUtils.bytesToString(Base64.decode(DataCryptUtils.mipushDecrypt(key, bArr), 2));
        } catch (InvalidAlgorithmParameterException e) {
            MyLog.e(e);
            return null;
        } catch (InvalidKeyException e2) {
            MyLog.e(e2);
            return null;
        } catch (NoSuchAlgorithmException e3) {
            MyLog.e(e3);
            return null;
        } catch (BadPaddingException e4) {
            MyLog.e(e4);
            return null;
        } catch (IllegalBlockSizeException e5) {
            MyLog.e(e5);
            return null;
        } catch (NoSuchPaddingException e6) {
            MyLog.e(e6);
            return null;
        }
    }

    @Override // com.xiaomi.clientreport.processor.IWrite
    public void preProcess(BaseClientReport baseClientReport) {
        if ((baseClientReport instanceof EventClientReport) && this.mEventMap != null) {
            EventClientReport eventClientReport = (EventClientReport) baseClientReport;
            String firstEventFileName = getFirstEventFileName(eventClientReport);
            ArrayList<BaseClientReport> arrayList = this.mEventMap.get(firstEventFileName);
            ArrayList<BaseClientReport> arrayList2 = arrayList;
            if (arrayList == null) {
                arrayList2 = new ArrayList<>();
            }
            arrayList2.add(eventClientReport);
            this.mEventMap.put(firstEventFileName, arrayList2);
        }
    }

    @Override // com.xiaomi.clientreport.processor.IWrite
    public void process() {
        HashMap<String, ArrayList<BaseClientReport>> map = this.mEventMap;
        if (map == null) {
            return;
        }
        if (map.size() > 0) {
            Iterator<String> it = this.mEventMap.keySet().iterator();
            while (it.hasNext()) {
                ArrayList<BaseClientReport> arrayList = this.mEventMap.get(it.next());
                if (arrayList != null && arrayList.size() > 0) {
                    BaseClientReport[] baseClientReportArr = new BaseClientReport[arrayList.size()];
                    arrayList.toArray(baseClientReportArr);
                    write(baseClientReportArr);
                }
            }
        }
        this.mEventMap.clear();
    }

    @Override // com.xiaomi.clientreport.processor.IDataSend
    public void readAndSend() {
        File file;
        RandomAccessFile randomAccessFile;
        FileLock fileLock;
        File file2;
        ClientReportUtil.moveFiles(this.mContext, "event", UPLOAD_FOLDER);
        File[] readFileName = ClientReportUtil.getReadFileName(this.mContext, UPLOAD_FOLDER);
        if (readFileName == null || readFileName.length <= 0) {
            return;
        }
        RandomAccessFile randomAccessFile2 = null;
        FileLock fileLockLock = null;
        File file3 = null;
        int length = readFileName.length;
        int i = 0;
        while (i < length) {
            File file4 = readFileName[i];
            if (file4 == null) {
                if (fileLockLock != null && fileLockLock.isValid()) {
                    try {
                        fileLockLock.release();
                    } catch (IOException e) {
                        MyLog.e(e);
                    }
                }
                IOUtils.closeQuietly(randomAccessFile2);
                randomAccessFile = randomAccessFile2;
                fileLock = fileLockLock;
                file2 = file3;
                if (file3 != null) {
                    file = file3;
                    file.delete();
                    randomAccessFile = randomAccessFile2;
                    fileLock = fileLockLock;
                    file2 = file;
                }
            } else {
                RandomAccessFile randomAccessFile3 = randomAccessFile2;
                FileLock fileLock2 = fileLockLock;
                File file5 = file3;
                RandomAccessFile randomAccessFile4 = randomAccessFile2;
                FileLock fileLock3 = fileLockLock;
                file = file3;
                try {
                    try {
                        if (file4.length() > 5242880) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("eventData read from cache file failed because ");
                            sb.append(file4.getName());
                            sb.append(" is too big, length ");
                            sb.append(file4.length());
                            MyLog.e(sb.toString());
                            reportDropFile(file4.getName(), Formatter.formatFileSize(this.mContext, file4.length()));
                            file4.delete();
                            if (fileLockLock != null && fileLockLock.isValid()) {
                                try {
                                    fileLockLock.release();
                                } catch (IOException e2) {
                                    MyLog.e(e2);
                                }
                            }
                            IOUtils.closeQuietly(randomAccessFile2);
                            randomAccessFile = randomAccessFile2;
                            fileLock = fileLockLock;
                            file2 = file3;
                            if (file3 != null) {
                                file = file3;
                            }
                        } else {
                            String absolutePath = file4.getAbsolutePath();
                            StringBuilder sb2 = new StringBuilder();
                            sb2.append(absolutePath);
                            sb2.append(".lock");
                            File file6 = new File(sb2.toString());
                            IOUtils.createFileQuietly(file6);
                            randomAccessFile2 = new RandomAccessFile(file6, "rw");
                            fileLockLock = randomAccessFile2.getChannel().lock();
                            send(readFile(absolutePath));
                            file4.delete();
                            if (fileLockLock != null && fileLockLock.isValid()) {
                                try {
                                    fileLockLock.release();
                                } catch (IOException e3) {
                                    MyLog.e(e3);
                                }
                            }
                            IOUtils.closeQuietly(randomAccessFile2);
                            file = file6;
                        }
                    } catch (Exception e4) {
                        MyLog.e(e4);
                        if (fileLock3 != null && fileLock3.isValid()) {
                            try {
                                fileLock3.release();
                            } catch (IOException e5) {
                                MyLog.e(e5);
                            }
                        }
                        IOUtils.closeQuietly(randomAccessFile4);
                        randomAccessFile = randomAccessFile4;
                        fileLock = fileLock3;
                        file2 = file;
                        if (file != null) {
                            randomAccessFile2 = randomAccessFile4;
                            fileLockLock = fileLock3;
                        }
                    }
                    file.delete();
                    randomAccessFile = randomAccessFile2;
                    fileLock = fileLockLock;
                    file2 = file;
                } catch (Throwable th) {
                    if (fileLock2 != null && fileLock2.isValid()) {
                        try {
                            fileLock2.release();
                        } catch (IOException e6) {
                            MyLog.e(e6);
                        }
                    }
                    IOUtils.closeQuietly(randomAccessFile3);
                    if (file5 != null) {
                        file5.delete();
                    }
                    throw th;
                }
            }
            i++;
            randomAccessFile2 = randomAccessFile;
            fileLockLock = fileLock;
            file3 = file2;
        }
    }

    @Override // com.xiaomi.clientreport.processor.IDataSend
    public void send(List<String> list) {
        ClientReportUtil.sendFile(this.mContext, list);
    }

    public void setContext(Context context) {
        this.mContext = context;
    }

    @Override // com.xiaomi.clientreport.processor.IEventProcessor
    public void setEventMap(HashMap<String, ArrayList<BaseClientReport>> map) {
        this.mEventMap = map;
    }

    @Override // com.xiaomi.clientreport.processor.IEventProcessor
    public byte[] stringToBytes(String str) {
        byte[] key;
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        if (!ClientReportLogicManager.getInstance(this.mContext).getConfig().isEventEncrypted()) {
            return XMStringUtils.getBytes(str);
        }
        String eventKeyWithDefault = ClientReportUtil.getEventKeyWithDefault(this.mContext);
        byte[] bytes = XMStringUtils.getBytes(str);
        if (TextUtils.isEmpty(eventKeyWithDefault) || bytes == null || bytes.length <= 1 || (key = ClientReportUtil.parseKey(eventKeyWithDefault)) == null) {
            return null;
        }
        try {
            if (key.length > 1) {
                return DataCryptUtils.mipushEncrypt(key, Base64.encode(bytes, 2));
            }
            return null;
        } catch (Exception e) {
            MyLog.e(e);
            return null;
        }
    }

    @Override // com.xiaomi.clientreport.processor.IWrite
    public void write(BaseClientReport[] baseClientReportArr) {
        BaseClientReport[] baseClientReportArrWrite2FileLocked;
        if (baseClientReportArr != null && baseClientReportArr.length != 0) {
            BaseClientReport[] baseClientReportArr2 = baseClientReportArr;
            if (baseClientReportArr[0] != null) {
                do {
                    baseClientReportArrWrite2FileLocked = write2FileLocked(baseClientReportArr2);
                    if (baseClientReportArrWrite2FileLocked == null || baseClientReportArrWrite2FileLocked.length <= 0) {
                        return;
                    } else {
                        baseClientReportArr2 = baseClientReportArrWrite2FileLocked;
                    }
                } while (baseClientReportArrWrite2FileLocked[0] != null);
                return;
            }
        }
        MyLog.w("event data write to cache file failed because data null");
    }
}
