package com.xiaomi.smack
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.mipush.sdk.OperatePushHelper
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.net.URL
import java.util.Vector

object SmackConfiguration {
    private const val SMACK_VERSION = "3.1.0"

    @JvmStatic
    var keepAliveInterval: Int = 330_000

    @JvmStatic
    var packetReplyTimeout: Int = OperatePushHelper.TIME_OUT
        set(value) {
            field = if (value <= 0) OperatePushHelper.TIME_OUT else value
        }
        get() {
            return if (field <= 0) OperatePushHelper.TIME_OUT else field
        }

    @JvmStatic
    var pingInterval: Int = 600_000

    @JvmStatic
    var serverShutdownTimeout: Int = 330_000

    private val defaultMechs = Vector<String>()

    init {
        try {
            for (classLoader in classLoaders) {
                val resources = classLoader.getResources("META-INF/smack-config.xml")
                while (resources.hasMoreElements()) {
                    val url = resources.nextElement()
                    var inputStream: InputStream? = null
                    try {
                        inputStream = url.openStream()
                        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
                            setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", true)
                            setInput(inputStream, "UTF-8")
                        }
                        var eventType = parser.eventType
                        do {
                            if (eventType == XmlPullParser.START_TAG) {
                                when (parser.name) {
                                    "className" -> parseClassToLoad(parser)
                                    "packetReplyTimeout" -> packetReplyTimeout = parseIntProperty(parser, packetReplyTimeout)
                                    "keepAliveInterval" -> keepAliveInterval = parseIntProperty(parser, keepAliveInterval)
                                    "mechName" -> defaultMechs.add(parser.nextText())
                                }
                            }
                            eventType = parser.next()
                        } while (eventType != XmlPullParser.END_DOCUMENT)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        inputStream?.close()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun addSaslMech(mech: String) {
        if (!defaultMechs.contains(mech)) {
            defaultMechs.add(mech)
        }
    }

    @JvmStatic
    fun addSaslMechs(collection: Collection<String>) {
        for (mech in collection) {
            addSaslMech(mech)
        }
    }

    @JvmStatic
    fun getSaslMechs(): List<String> = defaultMechs

    @JvmStatic
    fun getServerShutdownTimeOut(): Int = serverShutdownTimeout

    @JvmStatic
    val version: String
        get() = SMACK_VERSION

    @JvmStatic
    fun removeSaslMech(mech: String) {
        defaultMechs.remove(mech)
    }

    @JvmStatic
    fun removeSaslMechs(collection: Collection<String>) {
        for (mech in collection) {
            removeSaslMech(mech)
        }
    }

    private val classLoaders: Array<ClassLoader>
        get() {
            val loaders = arrayOf(
                SmackConfiguration::class.java.classLoader,
                Thread.currentThread().contextClassLoader,
            )
            return loaders.filterNotNull().toTypedArray()
        }

    private fun parseClassToLoad(parser: XmlPullParser) {
        val className = parser.nextText()
        try {
            Class.forName(className)
        } catch (e: ClassNotFoundException) {
            System.err.println("Error! A startup class specified in smack-config.xml could not be loaded: $className")
        }
    }

    private fun parseIntProperty(parser: XmlPullParser, defaultValue: Int): Int {
        return try {
            parser.nextText().toInt()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            defaultValue
        }
    }
}
