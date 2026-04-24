package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message as AndroidMessage
import android.os.Messenger
import android.os.RemoteException
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.BuildSettings
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.smack.packet.IQ
import com.xiaomi.smack.packet.Message
import com.xiaomi.smack.packet.Presence
import org.apache.http.NameValuePair

class ServiceClient private constructor(context: Context) {
    private val context = context.applicationContext
    private var clientMessenger: Messenger? = null
    private val pendingMessages = ArrayList<AndroidMessage>()
    private var connectingService = false
    private var miuiPushServiceEnabled = false
    private val messenger = Messenger(
        object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: AndroidMessage) {
                super.handleMessage(message)
            }
        },
    )

    init {
        if (serviceInstalled()) {
            MyLog.v("use miui push service")
            miuiPushServiceEnabled = true
        }
    }

    fun batchSendMessage(messages: Array<Message>, encrypt: Boolean): Boolean {
        if (!Network.hasNetwork(context)) {
            return false
        }
        val intent = createServiceIntent()
        val bundles = ServiceClientPacketSupport.buildMessageBundles(messages)
        if (bundles.isEmpty()) {
            return false
        }
        intent.action = PushConstants.ACTION_BATCH_SEND_MESSAGE
        intent.putExtra(PushConstants.EXTRA_SESSION, session)
        intent.putExtra(PushConstants.EXTRA_PACKETS, bundles)
        intent.putExtra(PushConstants.EXTRA_ENCYPT, encrypt)
        return startServiceSafely(intent)
    }

    fun checkAlive() {
        createServiceIntent().apply {
            action = PushServiceConstants.ACTION_CHECK_ALIVE
            startServiceSafely(this)
        }
    }

    fun closeChannel(): Boolean {
        return createServiceIntent().apply {
            action = PushConstants.ACTION_CLOSE_CHANNEL
        }.let(::startServiceSafely)
    }

    fun closeChannel(channelId: String): Boolean {
        return createServiceIntent().apply {
            action = PushConstants.ACTION_CLOSE_CHANNEL
            putExtra(PushConstants.EXTRA_CHANNEL_ID, channelId)
        }.let(::startServiceSafely)
    }

    fun closeChannel(channelId: String, userId: String): Boolean {
        return createServiceIntent().apply {
            action = PushConstants.ACTION_CLOSE_CHANNEL
            putExtra(PushConstants.EXTRA_CHANNEL_ID, channelId)
            putExtra(PushConstants.EXTRA_USER_ID, userId)
        }.let(::startServiceSafely)
    }

    @Deprecated("Use the map-based overload instead.")
    fun forceReconnection(
        userId: String,
        channelId: String,
        token: String,
        authMethod: String,
        security: String,
        kick: Boolean,
        clientAttributes: List<NameValuePair>?,
        cloudAttributes: List<NameValuePair>?,
    ): Boolean {
        return forceReconnection(
            userId,
            channelId,
            token,
            authMethod,
            security,
            kick,
            ServiceClientIntentSupport.translate(clientAttributes),
            ServiceClientIntentSupport.translate(cloudAttributes),
        )
    }

    fun forceReconnection(
        userId: String,
        channelId: String,
        token: String,
        authMethod: String,
        security: String,
        kick: Boolean,
        clientAttributes: Map<String, String>?,
        cloudAttributes: Map<String, String>?,
    ): Boolean {
        return createServiceIntent().apply {
            action = PushConstants.ACTION_FORCE_RECONNECT
            putOpenParamsIntoIntent(this, userId, channelId, token, authMethod, security, kick, clientAttributes, cloudAttributes)
        }.let(::startServiceSafely)
    }

    fun isMiuiPushServiceEnabled(): Boolean = miuiPushServiceEnabled

    fun openChannel(
        userId: String,
        channelId: String,
        token: String,
        authMethod: String,
        security: String,
        clientAttributes: Map<String, String>?,
        cloudAttributes: Map<String, String>?,
        kick: Boolean,
    ): Int {
        createServiceIntent().apply {
            action = PushConstants.ACTION_OPEN_CHANNEL
            putOpenParamsIntoIntent(this, userId, channelId, token, authMethod, security, kick, clientAttributes, cloudAttributes)
            startServiceSafely(this)
        }
        return 0
    }

    @Deprecated("Use the map-based overload instead.")
    fun openChannel(
        userId: String,
        channelId: String,
        token: String,
        authMethod: String,
        security: String,
        kick: Boolean,
        clientAttributes: List<NameValuePair>?,
        cloudAttributes: List<NameValuePair>?,
    ): Int {
        return openChannel(
            userId,
            channelId,
            token,
            authMethod,
            security,
            ServiceClientIntentSupport.translate(clientAttributes),
            ServiceClientIntentSupport.translate(cloudAttributes),
            kick,
        )
    }

    @Deprecated("Use the map-based overload instead.")
    fun resetConnection(
        userId: String,
        channelId: String,
        token: String,
        authMethod: String,
        security: String,
        kick: Boolean,
        clientAttributes: List<NameValuePair>?,
        cloudAttributes: List<NameValuePair>?,
    ) {
        resetConnection(
            userId,
            channelId,
            token,
            authMethod,
            security,
            kick,
            ServiceClientIntentSupport.translate(clientAttributes),
            ServiceClientIntentSupport.translate(cloudAttributes),
        )
    }

    fun resetConnection(
        userId: String,
        channelId: String,
        token: String,
        authMethod: String,
        security: String,
        kick: Boolean,
        clientAttributes: Map<String, String>?,
        cloudAttributes: Map<String, String>?,
    ) {
        createServiceIntent().apply {
            action = PushConstants.ACTION_RESET_CONNECTION
            putOpenParamsIntoIntent(this, userId, channelId, token, authMethod, security, kick, clientAttributes, cloudAttributes)
            startServiceSafely(this)
        }
    }

    fun sendIQ(packet: IQ): Boolean {
        if (!Network.hasNetwork(context)) {
            return false
        }
        val bundle = ServiceClientPacketSupport.buildPacketBundle(packet) ?: return false
        return createServiceIntent().apply {
            action = PushConstants.ACTION_SEND_IQ
            putExtra(PushConstants.EXTRA_SESSION, session)
            putExtra(PushConstants.EXTRA_PACKET, bundle)
        }.let(::startServiceSafely)
    }

    fun sendMessage(message: Message, encrypt: Boolean): Boolean {
        if (!Network.hasNetwork(context)) {
            return false
        }
        val bundle = ServiceClientPacketSupport.buildMessageBundle(message) ?: return false
        return createServiceIntent().apply {
            action = PushConstants.ACTION_SEND_MESSAGE
            putExtra(PushConstants.EXTRA_SESSION, session)
            putExtra(PushConstants.EXTRA_PACKET, bundle)
            putExtra(PushConstants.EXTRA_ENCYPT, encrypt)
        }.let(::startServiceSafely)
    }

    fun sendPresence(packet: Presence): Boolean {
        if (!Network.hasNetwork(context)) {
            return false
        }
        val bundle = ServiceClientPacketSupport.buildPacketBundle(packet) ?: return false
        return createServiceIntent().apply {
            action = PushConstants.ACTION_SEND_PRESENCE
            putExtra(PushConstants.EXTRA_SESSION, session)
            putExtra(PushConstants.EXTRA_PACKET, bundle)
        }.let(::startServiceSafely)
    }

    fun startServiceSafely(intent: Intent): Boolean {
        return try {
            if (MIUIUtils.isMIUI() || Build.VERSION.SDK_INT < 26) {
                context.startService(intent)
            } else {
                bindServiceSafely(intent)
            }
            true
        } catch (e: Exception) {
            MyLog.e(e)
            false
        }
    }

    @Deprecated("Use the map-based overload instead.")
    fun updateChannelInfo(channelId: String, clientAttributes: List<NameValuePair>?, cloudAttributes: List<NameValuePair>?) {
        updateChannelInfo(channelId, translate(clientAttributes), translate(cloudAttributes))
    }

    fun updateChannelInfo(channelId: String, clientAttributes: Map<String, String>?, cloudAttributes: Map<String, String>?) {
        createServiceIntent().apply {
            action = PushConstants.ACTION_UPDATE_CHANNEL_INFO
            clientAttributes?.let(ServiceClientIntentSupport::joinAttributes)?.takeIf { it.isNotEmpty() }?.let {
                putExtra(PushConstants.EXTRA_CLIENT_ATTR, it)
            }
            cloudAttributes?.let(ServiceClientIntentSupport::joinAttributes)?.takeIf { it.isNotEmpty() }?.let {
                putExtra(PushConstants.EXTRA_CLOUD_ATTR, it)
            }
            putExtra(PushConstants.EXTRA_CHANNEL_ID, channelId)
            startServiceSafely(this)
        }
    }

    private fun bindServiceSafely(intent: Intent) {
        synchronized(this) {
            if (connectingService) {
                if (pendingMessages.size >= MAX_PENDING_MESSAGES_SIZE) {
                    pendingMessages.removeAt(0)
                }
                pendingMessages += parseToMessage(intent)
                return
            }
            val currentMessenger = clientMessenger
            if (currentMessenger == null) {
                context.bindService(
                    intent,
                    object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, serviceBinder: IBinder) {
                            synchronized(this@ServiceClient) {
                                clientMessenger = Messenger(serviceBinder)
                                connectingService = false
                                pendingMessages.forEach { pendingMessage ->
                                    try {
                                        clientMessenger?.send(pendingMessage)
                                    } catch (e: RemoteException) {
                                        MyLog.e(e)
                                    }
                                }
                                pendingMessages.clear()
                            }
                        }

                        override fun onServiceDisconnected(name: ComponentName) {
                            clientMessenger = null
                            connectingService = false
                        }
                    },
                    1,
                )
                connectingService = true
                pendingMessages.clear()
                pendingMessages += parseToMessage(intent)
                return
            }
            try {
                currentMessenger.send(parseToMessage(intent))
            } catch (_: RemoteException) {
                clientMessenger = null
                connectingService = false
            }
        }
    }

    private fun createServiceIntent(): Intent {
        return ServiceClientIntentSupport.createServiceIntent(context, isMiuiPushServiceEnabled())
    }

    private fun parseToMessage(intent: Intent): AndroidMessage {
        return AndroidMessage.obtain().apply {
            what = 17
            obj = intent
        }
    }

    @Deprecated("Use the map-based overload instead.")
    private fun putOpenParamsIntoIntent(
        intent: Intent,
        userId: String,
        channelId: String,
        token: String,
        authMethod: String,
        security: String,
        kick: Boolean,
        clientAttributes: List<NameValuePair>?,
        cloudAttributes: List<NameValuePair>?,
    ) {
        putOpenParamsIntoIntent(
            intent,
            userId,
            channelId,
            token,
            authMethod,
            security,
            kick,
            ServiceClientIntentSupport.translate(clientAttributes),
            ServiceClientIntentSupport.translate(cloudAttributes),
        )
    }

    private fun putOpenParamsIntoIntent(
        intent: Intent,
        userId: String,
        channelId: String,
        token: String,
        authMethod: String,
        security: String,
        kick: Boolean,
        clientAttributes: Map<String, String>?,
        cloudAttributes: Map<String, String>?,
    ) {
        ServiceClientIntentSupport.putOpenParamsIntoIntent(
            intent,
            userId,
            channelId,
            token,
            authMethod,
            security,
            kick,
            clientAttributes,
            cloudAttributes,
            session,
            messenger,
        )
    }

    private fun serviceInstalled(): Boolean {
        if (BuildSettings.IsTestBuild) {
            return false
        }
        return try {
            AppInfoUtils.getVersionCode(context, PushConstants.PUSH_SERVICE_PACKAGE_NAME) >= MIN_MIUI_PUSH_VERSION
        } catch (_: Exception) {
            false
        }
    }

    private fun translate(list: List<NameValuePair>?): Map<String, String> {
        return ServiceClientIntentSupport.translate(list)
    }

    companion object {
        private const val MAX_PENDING_MESSAGES_SIZE = 50
        private const val MIN_MIUI_PUSH_VERSION = 104

        @Volatile
        private var instance: ServiceClient? = null
        private var session: String? = null

        @JvmStatic
        fun getInstance(context: Context): ServiceClient {
            return instance ?: synchronized(this) {
                instance ?: ServiceClient(context).also { instance = it }
            }
        }

        @JvmStatic
        fun getSession(): String? = session

        @JvmStatic
        fun setSession(value: String?) {
            session = value
        }
    }
}
