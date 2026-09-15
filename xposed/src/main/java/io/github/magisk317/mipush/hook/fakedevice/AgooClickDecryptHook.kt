package io.github.magisk317.mipush.hook.fakedevice

import android.content.Intent
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.currentApplication
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.hook
import io.github.magisk317.xposed.hookMethod
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Converts Agoo's encrypted Xiaomi click payload into the JSON expected by
 * the app's official BaseNotifyClick implementation.
 *
 * The hook deliberately runs only in Idle Fish. It invokes the target app's
 * own AgooFactory implementation so its SecurityGuard/app initialization and
 * device-material selection remain authoritative.
 */
internal object AgooClickDecryptHook {
    private const val TAG = "AgooClickDecrypt"
    private const val TARGET_PACKAGE = "com.taobao.idlefish"
    private const val PARSER_CLASS = "com.taobao.agoo.DefaultXiaomiMsgParseImpl"
    private const val PARSER_METHOD = "parseMsgFromIntent"
    private const val FACTORY_CLASS = "org.android.agoo.control.AgooFactory"
    private const val TAOBAO_REGISTER_CLASS = "com.taobao.agoo.TaobaoRegister"
    private const val ACCS_CONFIG_TAG = "default"
    private const val NOTIF_MANAGER_CLASS = "org.android.agoo.control.NotifManager"
    private const val MESSAGE_SERVICE_CLASS = "org.android.agoo.message.MessageService"
    private const val DECRYPT_METHOD = "parseEncryptedMsg"
    private const val ADAPTER_GLOBAL_INFO_CLASS = "com.taobao.accs.client.AdapterGlobalClientInfo"
    private const val ADAPTER_UTILITY_CLASS = "com.taobao.accs.utl.AdapterUtilityImpl"
    private const val ACCS_UTILITY_CLASS = "com.taobao.accs.utl.UtilityImpl"
    private const val AGOO_CONFIG_CLASS = "org.android.agoo.common.Config"
    private const val ENV_CLASS = "com.taobao.fleamarket.business.user_growth.alive.channel.Env"
    private const val UT_DEVICE_CLASS = "com.ta.utdid2.device.UTDevice"
    private const val SECURITY_GUARD_CLASS = "com.alibaba.wireless.security.open.SecurityGuardManager"
    private const val SECURITY_PARAM_CLASS = "com.alibaba.wireless.security.open.SecurityGuardParamContext"
    private const val SECURE_SIGNATURE_INTERFACE =
        "com.alibaba.wireless.security.open.securesignature.ISecureSignatureComponent"
    private const val ACCS_UTDID_STORE = "ACCS_SDK"
    private const val ENCRYPT_UTIL_CLASS = "org.android.agoo.common.EncryptUtil"
    private const val ACTIVITY_CLASS = "com.taobao.fleamarket.XiaoMiSystemMessageActivity"
    private const val ACTIVITY_CALLBACK_CLASS = "com.taobao.fleamarket.XiaoMiSystemMessageActivity\$1"
    private const val ACTIVITY_METHOD = "onMessage"
    private const val FALLBACK_ID_PREFIX = "fallback-"
    private const val FALLBACK_BODY_MARKER = "\"mipushFrameworkFallback\":true"
    private const val FALLBACK_THREAD_MARKER_TTL_MS = 5_000L
    private const val FALLBACK_REDIRECT_URI = "fleamarket://message"

    private val hookedParsers: MutableSet<String> =
        Collections.synchronizedSet(HashSet())
    private val hookedActivities: MutableSet<String> =
        Collections.synchronizedSet(HashSet())
    private val hookedLoadClassCallbacks: MutableSet<String> =
        Collections.synchronizedSet(HashSet())
    private val fallbackParserThread = ThreadLocal<Long>()
    private val stateDiagnosticLogged = AtomicBoolean(false)

    fun install(lpparam: LoadParam) {
        if (lpparam.packageName.orEmpty() != TARGET_PACKAGE) return

        val classLoader = lpparam.classLoader
        installParserIfAvailable(classLoader)
        installActivityIfAvailable(classLoader, ACTIVITY_CLASS)
        installActivityIfAvailable(classLoader, ACTIVITY_CALLBACK_CLASS)
        installLoadClassProbe(lpparam)
    }

    private fun installLoadClassProbe(lpparam: LoadParam) {
        val key = "${lpparam.packageName}@${lpparam.processName}#${System.identityHashCode(lpparam.classLoader)}"
        if (!hookedLoadClassCallbacks.add(key)) return

        ClassLoader::class.java.hookMethod("loadClass", String::class.java) {
            doAfter {
                val className = args.getOrNull(0) as? String ?: return@doAfter
                if (className != PARSER_CLASS &&
                    className != ACTIVITY_CLASS &&
                    className != ACTIVITY_CALLBACK_CLASS
                ) {
                    return@doAfter
                }
                val loadedClass = result as? Class<*> ?: return@doAfter
                val expectedLoader = lpparam.classLoader
                val loadedByTarget =
                    loadedClass.classLoader === expectedLoader || thisObject === expectedLoader
                if (!loadedByTarget) return@doAfter
                when (className) {
                    PARSER_CLASS -> installParser(loadedClass)
                    ACTIVITY_CLASS, ACTIVITY_CALLBACK_CLASS -> installActivity(loadedClass)
                }
            }
        }
    }

    private fun installActivityIfAvailable(classLoader: ClassLoader, className: String) {
        runCatching { classLoader.findClass(className) }
            .onSuccess(::installActivity)
    }

    private fun installActivity(clazz: Class<*>) {
        val methods = runCatching { clazz.declaredMethods.toList() }.getOrDefault(emptyList())
        methods
            .filter { method ->
                method.name == ACTIVITY_METHOD &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == Intent::class.java
            }
            .forEach(::hookActivityMessage)
    }

    private fun hookActivityMessage(method: Method) {
        val key = "${method.declaringClass.name}#${method.toGenericString()}"
        if (!hookedActivities.add(key)) return

        method.hook {
            replace {
                val intent = args.getOrNull(0) as? Intent
                if (!isFallbackClickIntent(intent)) {
                    return@replace invokeOriginal()
                }

                val activity = findEnclosingActivity(thisObject)
                if (activity == null) {
                    XLog.w(
                        TAG,
                        "fallback Agoo click callback had no enclosing Activity " +
                            "class=${method.declaringClass.name}",
                    )
                    return@replace null
                }

                runCatching {
                    val routeIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(FALLBACK_REDIRECT_URI))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    activity.startActivity(routeIntent)
                    XLog.i(
                        TAG,
                        "routed fallback Agoo click to $FALLBACK_REDIRECT_URI " +
                            "from=${method.declaringClass.name}",
                    )
                }.onFailure { error ->
                    XLog.w(
                        TAG,
                        "fallback Agoo click route failed error=${error.javaClass.simpleName}",
                    )
                }
                null
            }
        }
        XLog.d(TAG, "hooked ${method.declaringClass.name}.$ACTIVITY_METHOD fallback router")
    }

    private fun isFallbackClickIntent(intent: Intent?): Boolean {
        val extras = intent?.extras
        val messageId = extras?.getString("id")
        val body = extras?.getString("body").orEmpty()
        val bodyMarker = body.contains(FALLBACK_BODY_MARKER)
        val markedAt = fallbackParserThread.get()
        fallbackParserThread.remove()
        val threadMarker = markedAt != null &&
            System.currentTimeMillis() - markedAt <= FALLBACK_THREAD_MARKER_TTL_MS
        return messageId?.startsWith(FALLBACK_ID_PREFIX) == true || bodyMarker || threadMarker
    }

    private fun findEnclosingActivity(callback: Any?): android.app.Activity? {
        if (callback is android.app.Activity) return callback
        var currentClass: Class<*>? = callback?.javaClass
        while (currentClass != null) {
            currentClass.declaredFields.forEach { field ->
                if (!android.app.Activity::class.java.isAssignableFrom(field.type)) return@forEach
                val enclosingActivity = runCatching {
                    field.isAccessible = true
                    field.get(callback) as? android.app.Activity
                }.getOrNull()
                if (enclosingActivity != null) return enclosingActivity
            }
            currentClass = currentClass.superclass
        }
        return null
    }

    private fun installParserIfAvailable(classLoader: ClassLoader) {
        runCatching { classLoader.findClass(PARSER_CLASS) }
            .onSuccess(::installParser)
    }

    private fun installParser(clazz: Class<*>) {
        val methods = runCatching { clazz.declaredMethods.toList() }.getOrDefault(emptyList())
        methods
            .filter { it.name == PARSER_METHOD }
            .filter { method ->
                method.parameterTypes.size == 1 && method.parameterTypes[0] == Intent::class.java
            }
            .forEach { method -> hookParser(method) }
    }

    private fun hookParser(method: Method) {
        val key = "${method.declaringClass.name}#${method.toGenericString()}"
        if (!hookedParsers.add(key)) return

        method.hook {
            doAfter {
                val intent = args.getOrNull(0) as? Intent
                val parserResult = result
                val encrypted = parserResult as? String
                logParserDiagnostic(intent, parserResult)

                if (!encrypted.isNullOrEmpty() && isJsonObject(encrypted)) {
                    XLog.i(
                        TAG,
                        "Agoo Xiaomi parser already returned JSON " +
                            "length=${encrypted.length} payload=${sanitizePlaintextForDebug(encrypted)}",
                    )
                    return@doAfter
                }

                val decrypted = if (!encrypted.isNullOrEmpty()) {
                    decryptWithOfficialAgoo(method.declaringClass.classLoader, encrypted)
                } else {
                    null
                }

                if (decrypted != null && decrypted != encrypted && isJsonObject(decrypted)) {
                    result = decrypted
                    XLog.i(
                        TAG,
                        "replaced Agoo Xiaomi content with JSON " +
                            "encryptedLength=${encrypted?.length ?: 0} jsonLength=${decrypted.length} " +
                            "payload=${sanitizePlaintextForDebug(decrypted)}",
                    )
                } else {
                    fallbackParserThread.set(System.currentTimeMillis())
                    val fallback = buildFallbackPayload(intent)
                    result = fallback
                    XLog.w(
                        TAG,
                        "Agoo Xiaomi content was replaced with FALLBACK JSON " +
                            "origEncrypted=${encrypted != null} fallbackLength=${fallback.length} " +
                            "payload=${sanitizePlaintextForDebug(fallback)}",
                    )
                }
            }
        }
        XLog.d(TAG, "hooked ${method.declaringClass.name}.$PARSER_METHOD")
    }

    private fun buildFallbackPayload(intent: Intent?): String {
        val title = "系统消息"
        val text = "您收到一条新消息"
        val redirectUrl = "fleamarket://message"
        val bodyJson = """{"title":"$title","text":"$text","redirectUrl":"$redirectUrl","type":"CHAT","newType":"CHAT","mipushFrameworkFallback":true}"""
        val escapedBody = bodyJson.replace("\\", "\\\\").replace("\"", "\\\"")
        val msgId = "fallback-${System.currentTimeMillis()}"
        return """[{"p":"com.taobao.idlefish","i":"$msgId","b":"$escapedBody","f":256}]"""
    }

    private fun decryptWithOfficialAgoo(classLoader: ClassLoader?, encrypted: String): String? {
        if (classLoader == null) return null
        return try {
            val factoryClass = classLoader.loadClass(FACTORY_CLASS)
            ensureFactoryContext(factoryClass, classLoader)
            val decryptMethod = factoryClass.getDeclaredMethod(DECRYPT_METHOD, String::class.java)
            decryptMethod.isAccessible = true
            val officialResult = decryptMethod.invoke(null, encrypted) as? String
            if (officialResult != null && isJsonObject(officialResult)) {
                officialResult
            } else {
                decryptWithCandidateMaterials(classLoader, encrypted)
            }
        } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
            val cause = (error as? InvocationTargetException)?.targetException ?: error
            XLog.w(
                TAG,
                "Agoo Xiaomi decrypt failed " +
                    "encryptedLength=${encrypted.length} error=${cause.javaClass.simpleName}",
            )
            decryptWithCandidateMaterials(classLoader, encrypted)
        }
    }

    private fun ensureFactoryContext(factoryClass: Class<*>, classLoader: ClassLoader) {
        val context = currentApplication()?.applicationContext ?: return
        initializeAccsConfig(classLoader, context)

        val contextField = factoryClass.getDeclaredField("mContext")
        contextField.isAccessible = true
        if (contextField.get(null) == null) {
            val notifManagerClass = classLoader.loadClass(NOTIF_MANAGER_CLASS)
            val messageServiceClass = classLoader.loadClass(MESSAGE_SERVICE_CLASS)
            val factory = factoryClass.getDeclaredConstructor().newInstance()
            val initMethod = factoryClass.getDeclaredMethod(
                "init",
                android.content.Context::class.java,
                notifManagerClass,
                messageServiceClass,
            )
            initMethod.invoke(factory, context, null, null)
            XLog.d(TAG, "initialized AgooFactory via official init available=true")
        }
        logAgooPrerequisiteState(classLoader, context)
    }

    private fun initializeAccsConfig(classLoader: ClassLoader, context: android.content.Context) {
        runCatching {
            val registerClass = classLoader.loadClass(TAOBAO_REGISTER_CLASS)
            val setConfigTag = registerClass.getDeclaredMethod(
                "setAccsConfigTag",
                android.content.Context::class.java,
                String::class.java,
            )
            setConfigTag.isAccessible = true
            setConfigTag.invoke(null, context, ACCS_CONFIG_TAG)
            XLog.d(TAG, "synced official ACCS config available=true tag=default")
        }.onFailure { error ->
            val cause = (error as? InvocationTargetException)?.targetException ?: error
            XLog.w(
                TAG,
                "official ACCS config sync unavailable error=${cause.javaClass.simpleName}",
            )
        }
    }

    private fun logAgooPrerequisiteState(classLoader: ClassLoader, context: android.content.Context) {
        if (!stateDiagnosticLogged.compareAndSet(false, true)) return
        runCatching {
            val globalInfo = classLoader.loadClass(ADAPTER_GLOBAL_INFO_CLASS)
            val utility = classLoader.loadClass(ADAPTER_UTILITY_CLASS)
            val accsUtility = classLoader.loadClass(ACCS_UTILITY_CLASS)
            val config = classLoader.loadClass(AGOO_CONFIG_CLASS)

            val securityType = globalInfo.getDeclaredField("mSecurityType").let { field ->
                field.isAccessible = true
                field.getInt(null)
            }
            val authCode = readStaticString(globalInfo, "mAuthCode")
            val appSecret = readStaticString(utility, "mAgooAppSecret")
            val appKey = config.getDeclaredMethod("getAgooAppKey", android.content.Context::class.java)
                .invoke(null, context) as? String
            val deviceId = utility.getDeclaredMethod(
                "getDeviceId",
                android.content.Context::class.java,
            ).invoke(null, context) as? String
            val officialDeviceMaterial = resolveOfficialDeviceMaterial(accsUtility, utility, context)
            val utdid = runCatching {
                classLoader.loadClass(UT_DEVICE_CLASS)
                    .getDeclaredMethod("getUtdid", android.content.Context::class.java)
                    .invoke(null, context) as? String
            }.getOrNull()
            val ttid = runCatching {
                classLoader.loadClass(ENV_CLASS)
                    .getDeclaredMethod("getTtid", android.content.Context::class.java)
                    .also { it.isAccessible = true }
                    .invoke(null, context) as? String
            }.getOrNull()

            val securityGuard = runCatching {
                val guardClass = classLoader.loadClass(SECURITY_GUARD_CLASS)
                guardClass.methods.firstOrNull { method ->
                    method.name == "getInstance" &&
                        method.parameterTypes.contentEquals(arrayOf(android.content.Context::class.java))
                }?.invoke(null, context)
            }.getOrNull()
            val secureSignature = securityGuard?.let { guard ->
                runCatching {
                    guard.javaClass.methods.firstOrNull { method ->
                        method.name == "getSecureSignatureComp" && method.parameterTypes.isEmpty()
                    }?.invoke(guard)
                }.getOrNull()
            }
            val signatureProbe = probeSecureSignature(
                classLoader = classLoader,
                secureSignature = secureSignature,
                appKey = appKey,
                deviceMaterial = officialDeviceMaterial,
                authCode = authCode,
            )

            XLog.d(
                TAG,
                "Agoo prerequisites " +
                    "securityType=$securityType " +
                    "authCodeLen=${authCode.lengthOrZero()} " +
                    "appSecretLen=${appSecret.lengthOrZero()} " +
                    "appKeyLen=${appKey.lengthOrZero()} " +
                    "securityGuard=${securityGuard != null} " +
                    "secureSignature=${secureSignature != null} " +
                    "utdidLen=${utdid.lengthOrZero()} " +
                    "deviceIdLen=${deviceId.lengthOrZero()} " +
                    "officialDeviceLen=${officialDeviceMaterial.lengthOrZero()} " +
                    "ttidLen=${ttid.lengthOrZero()} " +
                    "signatureProbe=$signatureProbe",
            )
        }.onFailure { error ->
            XLog.w(TAG, "Agoo prerequisite diagnostic failed error=${error.javaClass.simpleName}")
        }
    }

    private fun resolveOfficialDeviceMaterial(
        accsUtility: Class<*>,
        adapterUtility: Class<*>,
        context: android.content.Context,
    ): String? {
        val utdidChanged = runCatching {
            accsUtility.getDeclaredMethod(
                "utdidChanged",
                String::class.java,
                android.content.Context::class.java,
            ).invoke(null, ACCS_UTDID_STORE, context) as Boolean
        }.getOrDefault(false)
        val selected = if (utdidChanged) {
            runCatching {
                accsUtility.getDeclaredMethod(
                    "getUtdid",
                    String::class.java,
                    android.content.Context::class.java,
                ).invoke(null, ACCS_UTDID_STORE, context) as? String
            }.getOrNull()
        } else {
            null
        }
        return selected?.takeUnless(String?::isNullOrEmpty) ?: runCatching {
            adapterUtility.getDeclaredMethod(
                "getDeviceId",
                android.content.Context::class.java,
            ).invoke(null, context) as? String
        }.getOrNull()
    }

    private fun probeSecureSignature(
        classLoader: ClassLoader,
        secureSignature: Any?,
        appKey: String?,
        deviceMaterial: String?,
        authCode: String?,
    ): String {
        if (secureSignature == null) return "missing_component"
        if (appKey.isNullOrEmpty() || deviceMaterial.isNullOrEmpty()) return "missing_input"
        return try {
            val paramClass = classLoader.loadClass(SECURITY_PARAM_CLASS)
            val param = paramClass.getDeclaredConstructor().newInstance()
            paramClass.getDeclaredField("appKey").let { field ->
                field.isAccessible = true
                field.set(param, appKey)
            }
            paramClass.getDeclaredField("requestType").let { field ->
                field.isAccessible = true
                field.setInt(param, 3)
            }
            paramClass.getDeclaredField("paramMap").let { field ->
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val values = field.get(param) as MutableMap<String, String>
                values["INPUT"] = appKey + deviceMaterial
            }
            val signatureInterface = classLoader.loadClass(SECURE_SIGNATURE_INTERFACE)
            val signRequest = signatureInterface.getMethod(
                "signRequest",
                paramClass,
                String::class.java,
            )
            val signed = signRequest.invoke(secureSignature, param, authCode) as? String
            if (signed.isNullOrEmpty()) "empty" else "ok:${signed.length}"
        } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
            val cause = (error as? InvocationTargetException)?.targetException ?: error
            "exception:${cause.javaClass.simpleName}"
        }
    }

    private fun readStaticString(clazz: Class<*>, fieldName: String): String? {
        val field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(null) as? String
    }

    private fun String?.lengthOrZero(): Int = this?.length ?: 0

    private fun logParserDiagnostic(intent: Intent?, parserResult: Any?) {
        val dataScheme = intent?.data?.scheme
        val parserState = when {
            parserResult == null -> "null"
            parserResult is String && parserResult.isEmpty() -> "empty"
            else -> "value"
        }
        val parserType = parserResult?.javaClass?.simpleName ?: "null"
        val parserLength = diagnosticValueLength(parserResult)
        val parserIsJson = parserResult is String && isJsonObject(parserResult)
        val component = intent?.component?.let { componentName ->
            "${componentName.packageName}/${componentName.className}"
        } ?: "null"

        XLog.i(
            TAG,
            "Agoo parseMsgFromIntent diagnostic " +
                "action=${intent?.action ?: "null"} " +
                "dataScheme=${dataScheme ?: "null"} isAgoo=${dataScheme.equals("agoo", ignoreCase = true)} " +
                "component=$component " +
                "extras=${describeIntentExtras(intent)} " +
                "key_message=${describeNamedExtra(intent, "key_message")} " +
                "mipush_serviceIntent=${describeNamedExtra(intent, "mipush_serviceIntent")} " +
                "resultState=$parserState resultType=$parserType resultLength=$parserLength " +
                "resultIsJson=$parserIsJson",
        )
    }

    private fun describeNamedExtra(intent: Intent?, key: String): String {
        val extras = intent?.extras ?: return "absent"
        if (!extras.containsKey(key)) return "absent"
        return runCatching {
            val value = readExtraValue(extras, key)
            "present(type=${value?.javaClass?.simpleName ?: "null"},length=${diagnosticValueLength(value)})"
        }.getOrElse {
            "present(type=unreadable,length=na)"
        }
    }

    @Suppress("DEPRECATION")
    private fun readExtraValue(extras: android.os.Bundle, key: String): Any? = extras.get(key)

    private fun describeIntentExtras(intent: Intent?): String {
        val extras = intent?.extras ?: return "null"
        return extras.keySet().sorted().joinToString(prefix = "[", postfix = "]") { key ->
            runCatching {
                val value = readExtraValue(extras, key)
                "$key:${value?.javaClass?.simpleName ?: "null"}/${diagnosticValueLength(value)}"
            }.getOrElse {
                "$key:unreadable/na"
            }
        }
    }

    private fun diagnosticValueLength(value: Any?): String = when (value) {
        null -> "0"
        is CharSequence -> value.length.toString()
        is ByteArray -> value.size.toString()
        is ShortArray -> value.size.toString()
        is IntArray -> value.size.toString()
        is LongArray -> value.size.toString()
        is FloatArray -> value.size.toString()
        is DoubleArray -> value.size.toString()
        is BooleanArray -> value.size.toString()
        is CharArray -> value.size.toString()
        is Array<*> -> value.size.toString()
        is Collection<*> -> value.size.toString()
        is android.os.Bundle -> value.size().toString()
        is Intent -> value.extras?.size()?.toString() ?: "0"
        else -> "na"
    }

    private fun sanitizePlaintextForDebug(value: String): String {
        val sensitiveField = Regex(
            "(?i)(\\\"(?:app[_-]?key|app[_-]?secret|device(?:[_-]?id)?|utdid|token|message[_-]?id)\\\"\\s*:\\s*)\\\"[^\\\"]*\\\""
        )
        return sensitiveField.replace(value) { "${it.groupValues[1]}\\\"<redacted>\\\"" }
    }

    private fun decryptWithCandidateMaterials(classLoader: ClassLoader, encrypted: String): String? {
        val context = currentApplication()?.applicationContext ?: return null
        return runCatching {
            val config = classLoader.loadClass(AGOO_CONFIG_CLASS)
            val appKey = config.getDeclaredMethod("getAgooAppKey", android.content.Context::class.java)
                .invoke(null, context) as? String
            if (appKey.isNullOrEmpty()) {
                XLog.w(TAG, "Agoo candidate decrypt failed: appKey is empty")
                return null
            }

            val globalInfo = classLoader.loadClass(ADAPTER_GLOBAL_INFO_CLASS)
            val authCode = readStaticString(globalInfo, "mAuthCode")

            val securityGuard = runCatching {
                val guardClass = classLoader.loadClass(SECURITY_GUARD_CLASS)
                guardClass.methods.firstOrNull { method ->
                    method.name == "getInstance" &&
                        method.parameterTypes.contentEquals(arrayOf(android.content.Context::class.java))
                }?.invoke(null, context)
            }.getOrNull()
            val secureSignature = securityGuard?.let { guard ->
                runCatching {
                    guard.javaClass.methods.firstOrNull { method ->
                        method.name == "getSecureSignatureComp" && method.parameterTypes.isEmpty()
                    }?.invoke(guard)
                }.getOrNull()
            }
            if (secureSignature == null) {
                XLog.w(TAG, "Agoo candidate decrypt failed: secureSignature is null")
                return null
            }

            val accsUtility = classLoader.loadClass(ACCS_UTILITY_CLASS)
            val adapterUtility = classLoader.loadClass(ADAPTER_UTILITY_CLASS)

            val candidates = listOfNotNull(
                resolveOfficialDeviceMaterial(accsUtility, adapterUtility, context),
                runCatching {
                    accsUtility.getDeclaredMethod("getUtdid", String::class.java, android.content.Context::class.java)
                        .invoke(null, ACCS_UTDID_STORE, context) as? String
                }.getOrNull(),
                runCatching {
                    adapterUtility.getDeclaredMethod("getDeviceId", android.content.Context::class.java)
                        .invoke(null, context) as? String
                }.getOrNull(),
                runCatching {
                    classLoader.loadClass(UT_DEVICE_CLASS)
                        .getDeclaredMethod("getUtdid", android.content.Context::class.java)
                        .invoke(null, context) as? String
                }.getOrNull(),
                runCatching {
                    android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID,
                    )
                }.getOrNull(),
            ).distinct().filter(String::isNotEmpty)

            XLog.d(TAG, "Agoo candidate decrypt attempting ${candidates.size} device candidates")

            for ((index, candidate) in candidates.withIndex()) {
                val candidateDecrypted = tryDecryptWithDeviceMaterial(
                    classLoader = classLoader,
                    secureSignature = secureSignature,
                    appKey = appKey,
                    deviceMaterial = candidate,
                    authCode = authCode,
                    encrypted = encrypted,
                )
                if (candidateDecrypted != null && isJsonObject(candidateDecrypted)) {
                    XLog.i(TAG, "Agoo candidate decrypt success with candidate index=$index candidateLen=${candidate.length}")
                    return candidateDecrypted
                }
            }
            XLog.w(TAG, "Agoo candidate decrypt: all ${candidates.size} candidates failed")
            null
        }.getOrElse { error ->
            XLog.w(TAG, "Agoo candidate decrypt error=${error.javaClass.simpleName}")
            null
        }
    }

    private fun tryDecryptWithDeviceMaterial(
        classLoader: ClassLoader,
        secureSignature: Any,
        appKey: String,
        deviceMaterial: String,
        authCode: String?,
        encrypted: String,
    ): String? {
        return try {
            val paramClass = classLoader.loadClass(SECURITY_PARAM_CLASS)
            val param = paramClass.getDeclaredConstructor().newInstance()
            paramClass.getDeclaredField("appKey").let { field ->
                field.isAccessible = true
                field.set(param, appKey)
            }
            paramClass.getDeclaredField("requestType").let { field ->
                field.isAccessible = true
                field.setInt(param, 3)
            }
            paramClass.getDeclaredField("paramMap").let { field ->
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val values = field.get(param) as MutableMap<String, String>
                values["INPUT"] = appKey + deviceMaterial
            }
            val signatureInterface = classLoader.loadClass(SECURE_SIGNATURE_INTERFACE)
            val signRequest = signatureInterface.getMethod(
                "signRequest",
                paramClass,
                String::class.java,
            )
            val strSignRequest = signRequest.invoke(secureSignature, param, authCode) as? String
            if (strSignRequest.isNullOrEmpty()) return null

            val length = strSignRequest.length
            val bArr = ByteArray(length / 2)
            for (i in 0 until length step 2) {
                bArr[i / 2] = (
                    (Character.digit(strSignRequest[i], 16) shl 4) +
                        Character.digit(strSignRequest[i + 1], 16)
                ).toByte()
            }

            val encryptUtilClass = classLoader.loadClass(ENCRYPT_UTIL_CLASS)
            val md5Method = encryptUtilClass.getDeclaredMethod("md5", ByteArray::class.java)
            val md5Key = md5Method.invoke(null, bArr) as ByteArray
            val md5Iv = md5Method.invoke(null, appKey.toByteArray(Charsets.UTF_8)) as ByteArray

            val secretKeySpec = javax.crypto.spec.SecretKeySpec(md5Key, "AES")
            val cipherBytes = android.util.Base64.decode(encrypted, 8)

            val aesDecryptMethod = encryptUtilClass.getDeclaredMethod(
                "aesDecrypt",
                ByteArray::class.java,
                javax.crypto.spec.SecretKeySpec::class.java,
                ByteArray::class.java,
            )
            val decryptedBytes = aesDecryptMethod.invoke(null, cipherBytes, secretKeySpec, md5Iv) as ByteArray
            String(decryptedBytes, Charsets.UTF_8)
        } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
            val cause = (error as? InvocationTargetException)?.targetException ?: error
            XLog.d(TAG, "tryDecryptWithDeviceMaterial failed: ${cause.javaClass.simpleName}")
            null
        }
    }

    private fun isJsonObject(value: String): Boolean {
        val trimmed = value.trimStart()
        return trimmed.startsWith('{') || trimmed.startsWith('[')
    }
}
