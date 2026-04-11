package com.xiaomi.push.service;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;
import com.google.protobuf.micro.CodedInputStreamMicro;
import com.google.protobuf.micro.CodedOutputStreamMicro;
import com.xiaomi.channel.commonutils.android.DeviceInfo;
import com.xiaomi.channel.commonutils.android.Region;
import com.xiaomi.channel.commonutils.android.SystemUtils;
import com.xiaomi.channel.commonutils.file.IOUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor;
import com.xiaomi.network.HttpUtils;
import com.xiaomi.push.protobuf.ChannelConfig;
import com.xiaomi.push.protobuf.ChannelMessage;
import com.xiaomi.smack.util.TaskExecutor;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/ServiceConfig.class */
public class ServiceConfig {
    private static final String CLOUDCONFIG = "XMCloudCfg";
    private static final String CONFIG_URL = "https://resolver.msg.xiaomi.net/psc/?t=a";
    private static final String CONFIG_URL_GLOBAL = "https://resolver.msg.global.xiaomi.net/psc/?t=a";
    private static final String PREF_NAME = "XMPushServiceConfig";
    private static final String PREF_UUID = "DeviceUUID";
    private static String sDeviceUUID;
    private static ServiceConfig sInstance = new ServiceConfig();
    private ChannelConfig.PushServiceConfig mConfig;
    private List<Listener> mListener = new ArrayList<>();
    private SerializedAsyncTaskProcessor.SerializedAsyncTask mPendingFetchTask;

    /* JADX INFO: loaded from: miuipushsdkshared_3_7_9.jar:com/xiaomi/push/service/ServiceConfig$Listener.class */
    public static abstract class Listener {
        public void onConfigChange(ChannelConfig.PushServiceConfig pushServiceConfig) {
        }

        public void onConfigMsgReceive(ChannelMessage.PushServiceConfigMsg pushServiceConfigMsg) {
        }
    }

    private ServiceConfig() {
    }

    private void checkLoad() {
        if (this.mConfig == null) {
            load();
        }
    }

    private void fetchConfig() {
        if (this.mPendingFetchTask != null) {
            return;
        }
        SerializedAsyncTaskProcessor.SerializedAsyncTask serializedAsyncTask = new SerializedAsyncTaskProcessor.SerializedAsyncTask() { // from class: com.xiaomi.push.service.ServiceConfig.1
            boolean success = false;

            @Override // com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor.SerializedAsyncTask
            public void postProcess() {
                Listener[] listenerArr;
                ServiceConfig.this.mPendingFetchTask = null;
                if (this.success) {
                    synchronized (ServiceConfig.this) {
                        listenerArr = (Listener[]) ServiceConfig.this.mListener.toArray(new Listener[ServiceConfig.this.mListener.size()]);
                    }
                    for (Listener listener : listenerArr) {
                        listener.onConfigChange(ServiceConfig.this.mConfig);
                    }
                }
            }

            @Override // com.xiaomi.channel.commonutils.misc.SerializedAsyncTaskProcessor.SerializedAsyncTask
            public void process() {
                try {
                    String region = AppRegionStorage.getInstance(SystemUtils.getContext()).getRegion();
                    ChannelConfig.PushServiceConfig from = ChannelConfig.PushServiceConfig.parseFrom(Base64.decode(HttpUtils.get(SystemUtils.getContext(), (TextUtils.isEmpty(region) || Region.China.name().equals(region)) ? ServiceConfig.CONFIG_URL : ServiceConfig.CONFIG_URL_GLOBAL, null), 10));
                    if (from != null) {
                        ServiceConfig.this.mConfig = from;
                        this.success = true;
                        ServiceConfig.this.save();
                    }
                } catch (Exception e) {
                    MyLog.w("fetch config failure: " + e.getMessage());
                }
            }
        };
        this.mPendingFetchTask = serializedAsyncTask;
        TaskExecutor.execute(serializedAsyncTask);
    }

    public static String getDeviceUUID() {
        String str;
        synchronized (ServiceConfig.class) {
            try {
                if (sDeviceUUID == null) {
                    SharedPreferences sharedPreferences = SystemUtils.getContext().getSharedPreferences(PREF_NAME, 0);
                    String string = sharedPreferences.getString(PREF_UUID, null);
                    sDeviceUUID = string;
                    if (string == null) {
                        String deviceId = DeviceInfo.getDeviceId(SystemUtils.getContext(), false);
                        sDeviceUUID = deviceId;
                        if (deviceId != null) {
                            sharedPreferences.edit().putString(PREF_UUID, sDeviceUUID).commit();
                        }
                    }
                }
                str = sDeviceUUID;
            } catch (Throwable th) {
                throw th;
            }
        }
        return str;
    }

    public static ServiceConfig getInstance() {
        return sInstance;
    }

    private void load() {
        BufferedInputStream bufferedInputStream = null;
        BufferedInputStream bufferedInputStream2 = null;
        try {
            try {
                BufferedInputStream bufferedInputStream3 = new BufferedInputStream(SystemUtils.getContext().openFileInput(CLOUDCONFIG));
                this.mConfig = ChannelConfig.PushServiceConfig.parseFrom(CodedInputStreamMicro.newInstance(bufferedInputStream3));
                bufferedInputStream = bufferedInputStream3;
                bufferedInputStream2 = bufferedInputStream3;
                bufferedInputStream3.close();
                bufferedInputStream2 = bufferedInputStream3;
            } catch (Exception e) {
                StringBuilder sb = new StringBuilder();
                sb.append("load config failure: ");
                sb.append(e.getMessage());
                bufferedInputStream = bufferedInputStream2;
                MyLog.w(sb.toString());
            }
            IOUtils.closeQuietly(bufferedInputStream2);
            if (this.mConfig == null) {
                this.mConfig = new ChannelConfig.PushServiceConfig();
            }
        } catch (Throwable th) {
            IOUtils.closeQuietly(bufferedInputStream);
            throw th;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void save() {
        try {
            if (this.mConfig != null) {
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(SystemUtils.getContext().openFileOutput(CLOUDCONFIG, 0));
                CodedOutputStreamMicro codedOutputStreamMicroNewInstance = CodedOutputStreamMicro.newInstance(bufferedOutputStream);
                this.mConfig.writeTo(codedOutputStreamMicroNewInstance);
                codedOutputStreamMicroNewInstance.flush();
                bufferedOutputStream.close();
            }
        } catch (Exception e) {
            MyLog.w("save config failure: " + e.getMessage());
        }
    }

    public void addListener(Listener listener) {
        synchronized (this) {
            this.mListener.add(listener);
        }
    }

    void clear() {
        synchronized (this) {
            this.mListener.clear();
        }
    }

    public boolean getBoolSetting(String str, boolean z) {
        return SystemUtils.getContext().getSharedPreferences(PREF_NAME, 0).getBoolean(str, z);
    }

    public ChannelConfig.PushServiceConfig getConfig() {
        checkLoad();
        return this.mConfig;
    }

    int getConfigVersion() {
        checkLoad();
        ChannelConfig.PushServiceConfig pushServiceConfig = this.mConfig;
        if (pushServiceConfig != null) {
            return pushServiceConfig.getConfigVersion();
        }
        return 0;
    }

    void handle(ChannelMessage.PushServiceConfigMsg pushServiceConfigMsg) {
        Listener[] listenerArr;
        if (pushServiceConfigMsg.hasCloudVersion() && pushServiceConfigMsg.getCloudVersion() > getConfigVersion()) {
            fetchConfig();
        }
        synchronized (this) {
            List<Listener> list = this.mListener;
            listenerArr = (Listener[]) list.toArray(new Listener[list.size()]);
        }
        for (Listener listener : listenerArr) {
            listener.onConfigMsgReceive(pushServiceConfigMsg);
        }
    }

    public void removeListener(Listener listener) {
        synchronized (this) {
            this.mListener.remove(listener);
        }
    }

    public void setSetting(String str, boolean z) {
        SystemUtils.getContext().getSharedPreferences(PREF_NAME, 0).edit().putBoolean(str, z).commit();
    }
}
