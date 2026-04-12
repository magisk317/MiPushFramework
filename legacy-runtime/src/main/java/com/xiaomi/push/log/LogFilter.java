package com.xiaomi.push.log;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/log/LogFilter.class */
class LogFilter {
    private static String MIPUSH_LOG_PATH = "/MiPushLog";
    private int mCurrentLen;
    private String mEndTime;
    private String mFromTime;
    private boolean mStartFound;
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private int mMaxLen = 2097152;
    private ArrayList<File> mFiles = new ArrayList<>();

    LogFilter() {
    }

    private void doFilter(BufferedReader bufferedReader, BufferedWriter bufferedWriter, Pattern pattern) throws IOException {
        boolean z;
        int i;
        int i2;
        int i3;
        int length;
        char[] cArr = new char[4096];
        int i4 = bufferedReader.read(cArr);
        for (boolean z2 = false; i4 != -1 && !z2; z2 = z) {
            String str = new String(cArr, 0, i4);
            Matcher matcher = pattern.matcher(str);
            int i5 = 0;
            int i6 = 0;
            int i7 = i4;
            while (true) {
                z = z2;
                i = i7;
                if (i5 >= i4) {
                    break;
                }
                z = z2;
                i = i7;
                if (!matcher.find(i5)) {
                    break;
                }
                int iStart = matcher.start();
                String strSubstring = str.substring(iStart, this.mFromTime.length() + iStart);
                if (this.mStartFound) {
                    i2 = i6;
                    if (strSubstring.compareTo(this.mEndTime) > 0) {
                        i = iStart;
                        z = true;
                        break;
                    }
                } else {
                    i2 = i6;
                    if (strSubstring.compareTo(this.mFromTime) >= 0) {
                        i2 = iStart;
                        this.mStartFound = true;
                    }
                }
                int iIndexOf = str.indexOf(10, iStart);
                if (iIndexOf != -1) {
                    i3 = iStart;
                    length = iIndexOf;
                } else {
                    i3 = iStart;
                    length = this.mFromTime.length();
                }
                i6 = i2;
                i5 = i3 + length;
            }
            if (this.mStartFound) {
                int i8 = i - i6;
                this.mCurrentLen += i8;
                if (z) {
                    bufferedWriter.write(cArr, i6, i8);
                    return;
                } else {
                    bufferedWriter.write(cArr, i6, i8);
                    if (this.mCurrentLen > this.mMaxLen) {
                        return;
                    }
                }
            }
            i4 = bufferedReader.read(cArr);
        }
    }

    private void filter2File(File file) {
        if (file == null) {
            return;
        }
        if (this.mFiles.isEmpty()) {
            return;
        }
        Pattern pattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
            for (File logFile : this.mFiles) {
                if (logFile == null || !logFile.exists()) {
                    continue;
                }
                BufferedReader reader = null;
                try {
                    reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(logFile)));
                    doFilter(reader, writer, pattern);
                    if (this.mCurrentLen > this.mMaxLen) {
                        break;
                    }
                } finally {
                    IOUtils.closeQuietly(reader);
                }
            }
        } catch (IOException e) {
            MyLog.e(e);
        } finally {
            IOUtils.closeQuietly(writer);
        }
    }

    private void filterXmsfLog2File(Context context, File file) {
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
            int i = 0;
            Pattern patternCompile = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
            BufferedReader bufferedReader = null;
            while (true) {
                BufferedReader bufferedReader2 = bufferedReader;
                BufferedReader bufferedReader3 = bufferedReader;
                BufferedReader bufferedReader4 = bufferedReader;
                try {
                    ContentResolver contentResolver = context.getContentResolver();
                    StringBuilder sb = new StringBuilder();
                    sb.append("content://com.xiaomi.xmsf/log/");
                    sb.append(i);
                    InputStream inputStreamOpenInputStream = contentResolver.openInputStream(Uri.parse(sb.toString()));
                    InputStreamReader inputStreamReader = new InputStreamReader(inputStreamOpenInputStream);
                    bufferedReader = new BufferedReader(inputStreamReader);
                    doFilter(bufferedReader, bufferedWriter, patternCompile);
                    bufferedReader.close();
                    i++;
                } catch (FileNotFoundException e) {
                    bufferedReader3 = bufferedReader4;
                    IOUtils.closeQuietly(bufferedReader3);
                    IOUtils.closeQuietly(bufferedWriter);
                    return;
                } catch (Exception e2) {
                    IOUtils.closeQuietly(bufferedReader3);
                    IOUtils.closeQuietly(bufferedWriter);
                    return;
                } catch (Throwable th) {
                    IOUtils.closeQuietly(bufferedReader2);
                    IOUtils.closeQuietly(bufferedWriter);
                    throw th;
                }
            }
        } catch (FileNotFoundException e3) {
        }
    }

    LogFilter addFile(File file) {
        if (file.exists()) {
            this.mFiles.add(file);
        }
        return this;
    }

    File filter(Context context, Date date, Date date2, File file) {
        File file2;
        if ("com.xiaomi.xmsf".equalsIgnoreCase(context.getPackageName())) {
            File file3 = new File(context.getExternalFilesDir(null), "dump");
            file2 = file3;
            if (!file3.exists()) {
                file2 = context.getFilesDir();
            }
            addFile(new File(file2, "xmsf.log.1"));
            addFile(new File(file2, "xmsf.log"));
        } else {
            file2 = new File(context.getExternalFilesDir(null) + MIPUSH_LOG_PATH);
            addFile(new File(file2, "log0.txt"));
            addFile(new File(file2, "log1.txt"));
        }
        if (!file2.isDirectory()) {
            return null;
        }
        File file4 = new File(file, date.getTime() + "-" + date2.getTime() + ".zip");
        if (file4.exists()) {
            return null;
        }
        setRange(date, date2);
        long jCurrentTimeMillis = System.currentTimeMillis();
        File file5 = new File(file, "log.txt");
        filter2File(file5);
        MyLog.v("LOG: filter cost = " + (System.currentTimeMillis() - jCurrentTimeMillis));
        if (!file5.exists()) {
            return null;
        }
        long jCurrentTimeMillis2 = System.currentTimeMillis();
        try {
            IOUtils.zip(file4, file5);
        } catch (IOException e) {
            MyLog.e(e);
            return null;
        }
        MyLog.v("LOG: zip cost = " + (System.currentTimeMillis() - jCurrentTimeMillis2));
        file5.delete();
        if (file4.exists()) {
            return file4;
        }
        return null;
    }

    void setMaxLen(int i) {
        if (i != 0) {
            this.mMaxLen = i;
        }
    }

    LogFilter setRange(Date date, Date date2) {
        if (date.after(date2)) {
            this.mFromTime = this.dateFormatter.format(date2);
            this.mEndTime = this.dateFormatter.format(date);
        } else {
            this.mFromTime = this.dateFormatter.format(date);
            this.mEndTime = this.dateFormatter.format(date2);
        }
        return this;
    }
}
