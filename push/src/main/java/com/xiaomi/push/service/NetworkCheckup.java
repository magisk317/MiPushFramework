package com.xiaomi.push.service;

import android.text.TextUtils;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.network.Host;
import com.xiaomi.push.protobuf.ChannelConfig;
import com.xiaomi.stats.StatsHandler;
import com.xiaomi.stats.StatsHelper;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/NetworkCheckup.class */
public class NetworkCheckup {
    private static final int CONNECTIVITY_CHECK_INTERVAL = 1800000;
    private static final int CONNECTIVITY_CON_PORT = 5222;
    private static final int CONNECTIVITY_CON_TIMEOUT = 5000;
    private static final String GET_GATEWAY = "ip route";
    private static final String PING_TEMPLATE = "ping -W 500 -i 0.2 -c 3 %s";
    private static final Pattern IP_PATTERN = Pattern.compile("([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})");
    private static long lastCheckTime = 0;
    private static ThreadPoolExecutor sExecutor = new ThreadPoolExecutor(1, 1, 20, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    public static void connectivityTest() {
        ChannelConfig.PushServiceConfig config;
        long jCurrentTimeMillis = System.currentTimeMillis();
        if ((sExecutor.getActiveCount() <= 0 || jCurrentTimeMillis - lastCheckTime >= 1800000) && StatsHandler.getInstance().isAllowStats() && (config = ServiceConfig.getInstance().getConfig()) != null && config.getTestHostsCount() > 0) {
            lastCheckTime = jCurrentTimeMillis;
            connectivityTest(config.getTestHostsList(), true);
        }
    }

    public static void connectivityTest(final List<String> list, final boolean z) {
        sExecutor.execute(new Runnable() { // from class: com.xiaomi.push.service.NetworkCheckup.2
            @Override // java.lang.Runnable
            public void run() {
                int i;
                boolean z2;
                boolean zDoConnectTest = NetworkCheckup.doConnectTest("www.baidu.com:80");
                Iterator<String> it = list.iterator();
                while (true) {
                    i = 1;
                    z2 = zDoConnectTest;
                    if (!it.hasNext()) {
                        break;
                    }
                    zDoConnectTest = zDoConnectTest || NetworkCheckup.doConnectTest(it.next());
                    if (zDoConnectTest && !z) {
                        z2 = zDoConnectTest;
                        break;
                    }
                }
                if (!z2) {
                    i = 2;
                }
                StatsHelper.count(i);
            }
        });
    }

    public static void doCheckup() {
        if (sExecutor.getActiveCount() > 0) {
            return;
        }
        sExecutor.execute(new Runnable() { // from class: com.xiaomi.push.service.NetworkCheckup.1
            @Override // java.lang.Runnable
            public void run() {
                String gateway = NetworkCheckup.getGateway();
                if (TextUtils.isEmpty(gateway)) {
                    MyLog.w("Network Checkup: cannot get gateway");
                } else {
                    MyLog.w("Network Checkup: get gateway:" + gateway);
                    NetworkCheckup.doPing(gateway);
                }
                try {
                    InetAddress byName = InetAddress.getByName("www.baidu.com");
                    MyLog.w("Network Checkup: get address for www.baidu.com:" + byName.getAddress());
                    NetworkCheckup.doPing(byName.getHostAddress());
                } catch (UnknownHostException e) {
                    MyLog.w("Network Checkup: cannot resolve the host www.baidu.com");
                } catch (Throwable th) {
                    MyLog.w("the checkup failure." + th);
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean doConnectTest(String str) {
        long jCurrentTimeMillis = System.currentTimeMillis();
        try {
            MyLog.w("ConnectivityTest: begin to connect to " + str);
            Socket socket = new Socket();
            socket.connect(Host.from(str, 5222), 5000);
            socket.setTcpNoDelay(true);
            MyLog.w("ConnectivityTest: connect to " + str + " in " + (System.currentTimeMillis() - jCurrentTimeMillis));
            socket.close();
            return true;
        } catch (Throwable th) {
            MyLog.e("ConnectivityTest: could not connect to:" + str + " exception: " + th.getClass().getSimpleName() + " description: " + th.getMessage());
            return false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void doPing(String str) {
        Process processExec;
        MyLog.w("Network Checkup: Begin to ping " + str);
        BufferedReader bufferedReader = null;
        Process process = null;
        BufferedReader bufferedReader2 = null;
        Process process2 = null;
        BufferedReader bufferedReader3 = null;
        Process process3 = null;
        try {
            try {
                processExec = Runtime.getRuntime().exec(String.format(PING_TEMPLATE, str));
                BufferedReader bufferedReader4 = new BufferedReader(new InputStreamReader(processExec.getInputStream()));
                for (String line = bufferedReader4.readLine(); line != null; line = bufferedReader4.readLine()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Network Checkup:");
                    sb.append(line);
                    MyLog.w(sb.toString());
                }
                bufferedReader = bufferedReader4;
                process = processExec;
                bufferedReader2 = bufferedReader4;
                process2 = processExec;
                bufferedReader3 = bufferedReader4;
                process3 = processExec;
                processExec.waitFor();
                IOUtils.closeQuietly(bufferedReader4);
                if (processExec == null) {
                    return;
                }
            } catch (IOException e) {
                MyLog.e(e);
                IOUtils.closeQuietly(bufferedReader3);
                if (process3 == null) {
                    return;
                } else {
                    processExec = process3;
                }
            } catch (Exception e2) {
                MyLog.e(e2);
                IOUtils.closeQuietly(bufferedReader2);
                if (process2 == null) {
                    return;
                } else {
                    processExec = process2;
                }
            }
            processExec.destroy();
        } catch (Throwable th) {
            IOUtils.closeQuietly(bufferedReader);
            if (process != null) {
                process.destroy();
            }
            throw th;
        }
    }

    public static void dumpNativeNetInfo() {
        String file = readFile("/proc/self/net/tcp");
        if (!TextUtils.isEmpty(file)) {
            MyLog.w("dump tcp for uid = " + android.os.Process.myUid());
            MyLog.w(file);
        }
        String file2 = readFile("/proc/self/net/tcp6");
        if (TextUtils.isEmpty(file2)) {
            return;
        }
        MyLog.w("dump tcp6 for uid = " + android.os.Process.myUid());
        MyLog.w(file2);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String getGateway() {
        Process process = null;
        BufferedReader bufferedReader = null;
        try {
            process = Runtime.getRuntime().exec(GET_GATEWAY);
            bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = bufferedReader.readLine();
            String str = null;
            if (!TextUtils.isEmpty(line) && line.startsWith("default via")) {
                String[] strArrSplit = line.split(" ");
                int length = strArrSplit.length;
                for (int i = 0; i < length; i++) {
                    String str2 = strArrSplit[i];
                    if (IP_PATTERN.matcher(str2).matches()) {
                        str = str2;
                        break;
                    }
                }
            }
            if (process != null) {
                process.waitFor();
            }
            return str;
        } catch (IOException e) {
            MyLog.e(e);
            return null;
        } catch (Exception e2) {
            MyLog.e(e2);
            return null;
        } catch (Throwable th) {
            IOUtils.closeQuietly(bufferedReader);
            if (process != null) {
                process.destroy();
            }
            throw th;
        } finally {
            IOUtils.closeQuietly(bufferedReader);
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static String readFile(String str) {
        BufferedReader bufferedReader = null;
        BufferedReader bufferedReader2 = null;
        try {
            BufferedReader bufferedReader3 = new BufferedReader(new FileReader(new File(str)));
            StringBuilder sb = new StringBuilder();
            while (true) {
                String line = bufferedReader3.readLine();
                if (line == null) {
                    bufferedReader = bufferedReader3;
                    bufferedReader2 = bufferedReader3;
                    String string = sb.toString();
                    IOUtils.closeQuietly(bufferedReader3);
                    return string;
                }
                sb.append("\n");
                sb.append(line);
            }
        } catch (Exception e) {
            IOUtils.closeQuietly(bufferedReader2);
            return null;
        } catch (Throwable th) {
            IOUtils.closeQuietly(bufferedReader);
            throw th;
        }
    }
}
