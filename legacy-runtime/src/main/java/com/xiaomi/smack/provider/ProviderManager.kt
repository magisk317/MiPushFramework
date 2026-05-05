package com.xiaomi.smack.provider

import com.xiaomi.smack.packet.IQ
import com.xiaomi.smack.packet.PacketExtension
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/smack/provider/ProviderManager.java
 * This runtime keeps local provider collection and singleton replacement helpers.
 */
class ProviderManager private constructor() {
    private val extensionProviders: MutableMap<String, Any> = ConcurrentHashMap()
    private val iqProviders: MutableMap<String, Any> = ConcurrentHashMap()

    init {
        initialize()
    }

    private fun getClassLoaders(): Array<ClassLoader> {
        val classLoaders = arrayOf(
            ProviderManager::class.java.classLoader,
            Thread.currentThread().contextClassLoader,
        )
        val result = ArrayList<ClassLoader>()
        for (classLoader in classLoaders) {
            if (classLoader != null) {
                result.add(classLoader)
            }
        }
        return result.toTypedArray()
    }

    private fun getProviderKey(str: String?, str2: String?): String {
        return buildString {
            append("<")
            append(str)
            append("/>")
            if (str != null) {
                append("<")
                append(str2)
                append("/>")
            }
        }
    }

    fun addExtensionProvider(str: String?, str2: String?, obj: Any) {
        if (obj !is PacketExtensionProvider && obj !is Class<*>) {
            throw IllegalArgumentException("Provider must be a PacketExtensionProvider or a Class instance.")
        }
        extensionProviders[getProviderKey(str, str2)] = obj
    }

    fun addIQProvider(str: String?, str2: String?, obj: Any) {
        if (obj !is IQProvider && (obj !is Class<*> || !IQ::class.java.isAssignableFrom(obj))) {
            throw IllegalArgumentException("Provider must be an IQProvider or a Class instance.")
        }
        iqProviders[getProviderKey(str, str2)] = obj
    }

    fun getExtensionProvider(str: String?, str2: String?): Any? = extensionProviders[getProviderKey(str, str2)]

    fun getExtensionProviders(): Collection<Any> = Collections.unmodifiableCollection(extensionProviders.values)

    fun getIQProvider(str: String?, str2: String?): Any? = iqProviders[getProviderKey(str, str2)]

    fun getIQProviders(): Collection<Any> = Collections.unmodifiableCollection(iqProviders.values)

    @Throws(Throwable::class)
    fun initialize() {
        try {
            for (classLoader in getClassLoaders()) {
                val resources = classLoader.getResources("META-INF/smack.providers")
                while (resources.hasMoreElements()) {
                    resources.nextElement().openStream().use { inputStream ->
                        val parser = XmlPullParserFactory.newInstance().newPullParser()
                        parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", true)
                        parser.setInput(inputStream, "UTF-8")
                        var eventType = parser.eventType
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG) {
                                when (parser.name) {
                                    "iqProvider" -> loadIQProvider(parser)
                                    "extensionProvider" -> loadExtensionProvider(parser)
                                }
                            }
                            eventType = parser.next()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadIQProvider(parser: XmlPullParser) {
        parser.next()
        parser.next()
        val elementName = parser.nextText()
        parser.next()
        parser.next()
        val namespace = parser.nextText()
        parser.next()
        parser.next()
        val className = parser.nextText()
        val providerKey = getProviderKey(elementName, namespace)
        if (iqProviders.containsKey(providerKey)) {
            return
        }
        try {
            val cls = Class.forName(className)
            if (IQProvider::class.java.isAssignableFrom(cls)) {
                iqProviders[providerKey] = cls.getDeclaredConstructor().newInstance()
            } else if (IQ::class.java.isAssignableFrom(cls)) {
                iqProviders[providerKey] = cls
            }
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }
    }

    private fun loadExtensionProvider(parser: XmlPullParser) {
        parser.next()
        parser.next()
        val elementName = parser.nextText()
        parser.next()
        parser.next()
        val namespace = parser.nextText()
        parser.next()
        parser.next()
        val className = parser.nextText()
        val providerKey = getProviderKey(elementName, namespace)
        if (extensionProviders.containsKey(providerKey)) {
            return
        }
        try {
            val cls = Class.forName(className)
            if (PacketExtensionProvider::class.java.isAssignableFrom(cls)) {
                extensionProviders[providerKey] = cls.getDeclaredConstructor().newInstance()
            } else if (PacketExtension::class.java.isAssignableFrom(cls)) {
                extensionProviders[providerKey] = cls
            }
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }
    }

    fun removeExtensionProvider(str: String?, str2: String?) {
        extensionProviders.remove(getProviderKey(str, str2))
    }

    fun removeIQProvider(str: String?, str2: String?) {
        iqProviders.remove(getProviderKey(str, str2))
    }

    companion object {
        private var instance: ProviderManager? = null

        @JvmStatic
        fun getInstance(): ProviderManager {
            synchronized(ProviderManager::class.java) {
                try {
                    if (instance == null) {
                        instance = ProviderManager()
                    }
                    return instance!!
                } catch (throwable: Throwable) {
                    throw RuntimeException(throwable)
                }
            }
        }

        @JvmStatic
        fun setInstance(providerManager: ProviderManager) {
            synchronized(ProviderManager::class.java) {
                if (instance != null) {
                    throw IllegalStateException("ProviderManager singleton already set")
                }
                instance = providerManager
            }
        }
    }
}
