package com.xiaomi.stats;

import android.text.TextUtils;
import com.xiaomi.channel.commonutils.android.DeviceInfo;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.network.Network;
import com.xiaomi.channel.commonutils.stats.Stats;
import com.xiaomi.push.protobuf.ChannelMessage;
import com.xiaomi.push.service.ServiceConfig;
import com.xiaomi.push.service.XMPushService;
import com.xiaomi.push.thrift.ChannelStatsType;
import com.xiaomi.push.thrift.StatsEvent;
import com.xiaomi.push.thrift.StatsEvents;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.XmPushTBinaryProtocol;
import org.apache.thrift.transport.TMemoryBuffer;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/stats/StatsHandler.class */
public class StatsHandler {
    private static final int MAX_DURATION = 604800000;
    private static final int MIN_STATS_PING_SIZE = 750;
    private StatsContext context;
    private int duration;
    private long startTime;
    private String uuid;
    private boolean allowStatsUpload = false;
    private Stats statsContainer = Stats.instance();

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/stats/StatsHandler$Holder.class */
    static class Holder {
        static final StatsHandler sStatsHandler = new StatsHandler();

        Holder() {
        }
    }

    private StatsEvent from(Stats.Item item) {
        StatsEvent statsEventCreateStatsEvent = null;
        if (item.key != 0) {
            statsEventCreateStatsEvent = createStatsEvent();
            statsEventCreateStatsEvent.setType(ChannelStatsType.CHANNEL_STATS_COUNTER.getValue());
            statsEventCreateStatsEvent.setSubvalue(item.key);
            statsEventCreateStatsEvent.setAnnotation(item.annotation);
        } else if (item.obj instanceof StatsEvent) {
            statsEventCreateStatsEvent = (StatsEvent) item.obj;
        }
        return statsEventCreateStatsEvent;
    }

    public static StatsContext getContext() {
        StatsContext statsContext;
        synchronized (Holder.sStatsHandler) {
            statsContext = Holder.sStatsHandler.context;
        }
        return statsContext;
    }

    public static StatsHandler getInstance() {
        return Holder.sStatsHandler;
    }

    private void internalAdd(int i, int i2, int i3, String str, String str2, long j) {
        StatsEvent statsEvent = new StatsEvent();
        statsEvent.chid = (byte) i;
        statsEvent.type = i2;
        statsEvent.value = i3;
        statsEvent.connpt = str2;
        statsEvent.host = str;
        statsEvent.time = ((int) System.currentTimeMillis()) / 1000;
        this.statsContainer.stat(statsEvent);
        MyLog.v(String.format(Locale.US, "add stats: chid = %s, type =%d, value = %d, connpt = %s", Integer.valueOf(i), Integer.valueOf(i2), Integer.valueOf(i3), str2));
    }

    private StatsEvents retriveStatsEvents(int i) {
        ArrayList arrayList = new ArrayList();
        StatsEvents statsEvents = new StatsEvents(this.uuid, arrayList);
        if (!Network.isWIFIConnected(this.context.pushService)) {
            statsEvents.setOperator(DeviceInfo.getSimOperatorName(this.context.pushService));
        }
        TMemoryBuffer tMemoryBuffer = new TMemoryBuffer(i);
        TProtocol protocol = new XmPushTBinaryProtocol.Factory().getProtocol(tMemoryBuffer);
        try {
            statsEvents.write(protocol);
        } catch (TException e) {
        }
        LinkedList<Stats.Item> stats = this.statsContainer.getStats();
        while (stats.size() > 0) {
            try {
                StatsEvent statsEventFrom = from(stats.getLast());
                if (statsEventFrom != null) {
                    statsEventFrom.write(protocol);
                }
                if (tMemoryBuffer.length() > i) {
                    break;
                }
                if (statsEventFrom != null) {
                    arrayList.add(statsEventFrom);
                }
                stats.removeLast();
            } catch (NoSuchElementException e2) {
            } catch (TException e3) {
            }
        }
        return statsEvents;
    }

    private void stopStatsIfNeed() {
        if (!this.allowStatsUpload || System.currentTimeMillis() - this.startTime <= this.duration) {
            return;
        }
        this.allowStatsUpload = false;
        this.startTime = 0L;
    }

    public void add(int i, int i2, int i3, String str) {
        synchronized (this) {
            if (this.uuid == null) {
                MyLog.v(String.format(Locale.US, "StatsHandler.add() Should initialized before add", new Object[0]));
                return;
            }
            String activeConnPoint = Network.getActiveConnPoint(this.context.pushService);
            if (!TextUtils.isEmpty(activeConnPoint)) {
                internalAdd(i, i2, i3, str, activeConnPoint, System.currentTimeMillis());
            }
        }
    }

    void add(StatsEvent statsEvent) {
        synchronized (this) {
            this.statsContainer.stat(statsEvent);
        }
    }

    StatsEvent createStatsEvent() {
        StatsEvent statsEvent;
        synchronized (this) {
            statsEvent = new StatsEvent();
            statsEvent.setConnpt(Network.getActiveConnPoint(this.context.pushService));
            statsEvent.chid = (byte) 0;
            statsEvent.value = 1;
            statsEvent.setTime((int) (System.currentTimeMillis() / 1000));
        }
        return statsEvent;
    }

    public void init(XMPushService xMPushService) {
        synchronized (this) {
            this.context = new StatsContext(xMPushService);
            this.uuid = "";
            ServiceConfig.getInstance().addListener(new ServiceConfig.Listener() { // from class: com.xiaomi.stats.StatsHandler.1
                @Override // com.xiaomi.push.service.ServiceConfig.Listener
                public void onConfigMsgReceive(ChannelMessage.PushServiceConfigMsg pushServiceConfigMsg) {
                    if (pushServiceConfigMsg.hasDots()) {
                        StatsHandler.getInstance().setDuration(pushServiceConfigMsg.getDots());
                    }
                }
            });
        }
    }

    public boolean isAllowStats() {
        return this.allowStatsUpload;
    }

    StatsEvents retriveStatsEvents() {
        StatsEvents statsEventsRetriveStatsEvents;
        synchronized (this) {
            statsEventsRetriveStatsEvents = null;
            if (shouldSendStatsNow()) {
                int i = MIN_STATS_PING_SIZE;
                if (!Network.isWIFIConnected(this.context.pushService)) {
                    i = MIN_STATS_PING_SIZE / 2;
                }
                statsEventsRetriveStatsEvents = retriveStatsEvents(i);
            }
        }
        return statsEventsRetriveStatsEvents;
    }

    public void setDuration(int i) {
        if (i > 0) {
            int i2 = i * 1000;
            int i3 = i2;
            if (i2 > 604800000) {
                i3 = 604800000;
            }
            if (this.duration == i3 && this.allowStatsUpload) {
                return;
            }
            this.allowStatsUpload = true;
            this.startTime = System.currentTimeMillis();
            this.duration = i3;
            MyLog.v("enable dot duration = " + i3 + " start = " + this.startTime);
        }
    }

    boolean shouldSendStatsNow() {
        stopStatsIfNeed();
        return this.allowStatsUpload && this.statsContainer.getCount() > 0;
    }
}
