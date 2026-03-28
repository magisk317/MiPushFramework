package com.xiaomi.smack;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/smack/ConnectionListener.class */
public interface ConnectionListener {
    void connectionClosed(Connection connection, int i, Exception exc);

    void connectionStarted(Connection connection);

    void reconnectionFailed(Connection connection, Exception exc);

    void reconnectionSuccessful(Connection connection);
}
