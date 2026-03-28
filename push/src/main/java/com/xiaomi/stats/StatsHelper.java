package com.xiaomi.stats;

import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.string.Base64Coder;
import com.xiaomi.push.service.PushClientsManager;
import com.xiaomi.push.service.XMPushService;
import com.xiaomi.push.thrift.ChannelStatsType;
import com.xiaomi.push.thrift.StatsEvent;
import com.xiaomi.push.thrift.StatsEvents;
import com.xiaomi.stats.StatsAnalyser;
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils;
import java.util.Hashtable;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/stats/StatsHelper.class */
public class StatsHelper {
    private static final int DEFAULT_CHID = 0;
    private static final int MAX_KEY_VALUE = 16777215;
    private static final int PING_RTT = ChannelStatsType.PING_RTT.getValue();

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/stats/StatsHelper$Holder.class */
    static class Holder {
        static Hashtable<Integer, Long> sTimeTracker = new Hashtable<>();

        Holder() {
        }
    }

    public static void connectFail(String str, Exception exc) {
        try {
            StatsAnalyser.TypeWraper typeWraperFromConnectionException = StatsAnalyser.fromConnectionException(exc);
            StatsEvent statsEventCreateStatsEvent = StatsHandler.getInstance().createStatsEvent();
            statsEventCreateStatsEvent.setType(typeWraperFromConnectionException.type.getValue());
            statsEventCreateStatsEvent.setAnnotation(typeWraperFromConnectionException.annotation);
            statsEventCreateStatsEvent.setHost(str);
            StatsHandler.getInstance().add(statsEventCreateStatsEvent);
        } catch (NullPointerException e) {
        }
    }

    public static void connectionDown(String str, Exception exc) {
        try {
            StatsAnalyser.TypeWraper typeWraperFromDisconnectEx = StatsAnalyser.fromDisconnectEx(exc);
            StatsEvent statsEventCreateStatsEvent = StatsHandler.getInstance().createStatsEvent();
            statsEventCreateStatsEvent.setType(typeWraperFromDisconnectEx.type.getValue());
            statsEventCreateStatsEvent.setAnnotation(typeWraperFromDisconnectEx.annotation);
            statsEventCreateStatsEvent.setHost(str);
            StatsHandler.getInstance().add(statsEventCreateStatsEvent);
        } catch (NullPointerException e) {
        }
    }

    public static void count(int i) {
        StatsEvent statsEventCreateStatsEvent = StatsHandler.getInstance().createStatsEvent();
        statsEventCreateStatsEvent.setType(ChannelStatsType.CHANNEL_STATS_COUNTER.getValue());
        statsEventCreateStatsEvent.setSubvalue(i);
        StatsHandler.getInstance().add(statsEventCreateStatsEvent);
    }

    public static void pingEnded() {
        trackEnd(0, PING_RTT, null, -1);
    }

    public static void pingStarted() {
        trackStart(0, PING_RTT);
    }

    public static byte[] retriveStatsAsByte() {
        byte[] bArrConvertThriftObjectToBytes = null;
        StatsEvents statsEventsRetriveStatsEvents = StatsHandler.getInstance().retriveStatsEvents();
        if (statsEventsRetriveStatsEvents != null) {
            bArrConvertThriftObjectToBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(statsEventsRetriveStatsEvents);
        }
        return bArrConvertThriftObjectToBytes;
    }

    public static String retriveStatsAsString() {
        StatsEvents statsEventsRetriveStatsEvents = StatsHandler.getInstance().retriveStatsEvents();
        String str = null;
        if (statsEventsRetriveStatsEvents != null) {
            byte[] bArrConvertThriftObjectToBytes = XmPushThriftSerializeUtils.convertThriftObjectToBytes(statsEventsRetriveStatsEvents);
            str = null;
            if (bArrConvertThriftObjectToBytes != null) {
                str = new String(Base64Coder.encode(bArrConvertThriftObjectToBytes));
            }
        }
        return str;
    }

    public static void stats(int i, int i2, int i3, String str, int i4) {
        StatsEvent statsEventCreateStatsEvent = StatsHandler.getInstance().createStatsEvent();
        statsEventCreateStatsEvent.setChid((byte) i);
        statsEventCreateStatsEvent.setType(i2);
        statsEventCreateStatsEvent.setValue(i3);
        statsEventCreateStatsEvent.setHost(str);
        statsEventCreateStatsEvent.setSubvalue(i4);
        StatsHandler.getInstance().add(statsEventCreateStatsEvent);
    }

    public static void statsBind(XMPushService xMPushService, PushClientsManager.ClientLoginInfo clientLoginInfo) {
        new BindTracker(xMPushService, clientLoginInfo).track();
    }

    public static void statsGslb(String str, int i, Exception exc) {
        StatsEvent statsEventCreateStatsEvent = StatsHandler.getInstance().createStatsEvent();
        if (i > 0) {
            statsEventCreateStatsEvent.setType(ChannelStatsType.GSLB_REQUEST_SUCCESS.getValue());
            statsEventCreateStatsEvent.setHost(str);
            statsEventCreateStatsEvent.setValue(i);
            StatsHandler.getInstance().add(statsEventCreateStatsEvent);
            return;
        }
        try {
            StatsAnalyser.TypeWraper typeWraperFromGslbException = StatsAnalyser.fromGslbException(exc);
            statsEventCreateStatsEvent.setType(typeWraperFromGslbException.type.getValue());
            statsEventCreateStatsEvent.setAnnotation(typeWraperFromGslbException.annotation);
            statsEventCreateStatsEvent.setHost(str);
            StatsHandler.getInstance().add(statsEventCreateStatsEvent);
        } catch (NullPointerException e) {
        }
    }

    public static void trackEnd(int i, int i2, String str, int i3) {
        synchronized (StatsHelper.class) {
            try {
                long jCurrentTimeMillis = System.currentTimeMillis();
                int i4 = (i << 24) | i2;
                if (Holder.sTimeTracker.containsKey(Integer.valueOf(i4))) {
                    StatsEvent statsEventCreateStatsEvent = StatsHandler.getInstance().createStatsEvent();
                    statsEventCreateStatsEvent.setType(i2);
                    statsEventCreateStatsEvent.setValue((int) (jCurrentTimeMillis - Holder.sTimeTracker.get(Integer.valueOf(i4)).longValue()));
                    statsEventCreateStatsEvent.setHost(str);
                    if (i3 > -1) {
                        statsEventCreateStatsEvent.setSubvalue(i3);
                    }
                    StatsHandler.getInstance().add(statsEventCreateStatsEvent);
                    Holder.sTimeTracker.remove(Integer.valueOf(i2));
                } else {
                    MyLog.e("stats key not found");
                }
            } finally {
            }
        }
    }

    public static void trackStart(int i, int i2) {
        synchronized (StatsHelper.class) {
            try {
                if (i2 < MAX_KEY_VALUE) {
                    Holder.sTimeTracker.put(Integer.valueOf((i << 24) | i2), Long.valueOf(System.currentTimeMillis()));
                } else {
                    MyLog.e("stats key should less than 16777215");
                }
            } finally {
            }
        }
    }
}
