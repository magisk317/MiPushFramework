package com.xiaomi.channel.commonutils.stats;

import java.util.LinkedList;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/stats/Stats.class */
public class Stats {
    private static final int MAX_STATS_ITEMS = 100;
    private LinkedList<Item> statsQueue = new LinkedList<>();

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/channel/commonutils/stats/Stats$Item.class */
    public static class Item {
        private static final Stats sStats = new Stats();
        public String annotation;
        public int key;
        public Object obj;

        Item(int i, Object obj) {
            this.key = i;
            this.obj = obj;
        }

        Item(int i, String str) {
            this.key = i;
            this.annotation = str;
        }
    }

    private void checkSize() {
        if (this.statsQueue.size() > 100) {
            this.statsQueue.removeFirst();
        }
    }

    public static Stats instance() {
        return Item.sStats;
    }

    public void count(int i) {
        synchronized (this) {
            this.statsQueue.add(new Item(i, (String) null));
            checkSize();
        }
    }

    public void count(int i, String str) {
        synchronized (this) {
            this.statsQueue.add(new Item(i, str));
            checkSize();
        }
    }

    public int getCount() {
        int size;
        synchronized (this) {
            size = this.statsQueue.size();
        }
        return size;
    }

    public LinkedList<Item> getStats() {
        LinkedList<Item> linkedList;
        synchronized (this) {
            linkedList = this.statsQueue;
            this.statsQueue = new LinkedList<>();
        }
        return linkedList;
    }

    public void stat(Object obj) {
        synchronized (this) {
            this.statsQueue.add(new Item(0, obj));
            checkSize();
        }
    }
}
