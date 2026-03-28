package com.xiaomi.network;

import java.net.InetSocketAddress;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/network/Host.class */
public final class Host {
    private String hostAddress;
    private int port;

    public Host(String str, int i) {
        this.hostAddress = str;
        this.port = i;
    }

    public static InetSocketAddress from(String str, int i) {
        Host host = parse(str, i);
        return new InetSocketAddress(host.getHost(), host.getPort());
    }

    public static Host parse(String str, int i) {
        int iLastIndexOf = str.lastIndexOf(":");
        String strSubstring = str;
        int i2 = i;
        if (iLastIndexOf != -1) {
            strSubstring = str.substring(0, iLastIndexOf);
            try {
                int i3 = Integer.parseInt(str.substring(iLastIndexOf + 1));
                int i4 = i3;
                if (i3 <= 0) {
                    i4 = i;
                }
                i2 = i4;
            } catch (NumberFormatException e) {
                i2 = i;
            }
        }
        return new Host(strSubstring, i2);
    }

    public String getHost() {
        return this.hostAddress;
    }

    public int getPort() {
        return this.port;
    }

    public String toString() {
        if (this.port <= 0) {
            return this.hostAddress;
        }
        return this.hostAddress + ":" + this.port;
    }
}
