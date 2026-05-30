package com.xiaomi.smack

import com.xiaomi.smack.packet.StreamError
import com.xiaomi.smack.packet.XMPPError
import java.io.PrintStream
import java.io.PrintWriter

/*
 * Stock reference: com.xiaomi.xmsf 7.4.67-C (versionCode 70004067),
 * split-XiaomiServiceFrameworkCN-master.apk sha256 444e9f128591e04e38672bfe44a246ab3fa97ae68e95882839d8a7afe766df2b,
 * JADX path: com.xiaomi.xmsf/stock/split-XiaomiServiceFrameworkCN-master/sources/qa/j.java
 * Stock class name is obfuscated as qa.j; this file keeps the deobfuscated com.xiaomi.smack.XMPPException API.
 */
class XMPPException : Exception {
    var error: XMPPError? = null
        private set
    var streamError: StreamError? = null
        private set
    var wrappedThrowable: Throwable? = null
        private set

    constructor()

    constructor(streamError: StreamError) {
        this.streamError = streamError
    }

    constructor(error: XMPPError) {
        this.error = error
    }

    constructor(message: String) : super(message)

    constructor(
        message: String,
        error: XMPPError,
    ) : super(message) {
        this.error = error
    }

    constructor(
        message: String,
        error: XMPPError,
        wrappedThrowable: Throwable,
    ) : super(message) {
        this.error = error
        this.wrappedThrowable = wrappedThrowable
    }

    constructor(
        message: String,
        wrappedThrowable: Throwable,
    ) : super(message) {
        this.wrappedThrowable = wrappedThrowable
    }

    constructor(wrappedThrowable: Throwable) {
        this.wrappedThrowable = wrappedThrowable
    }

    override val message: String?
        get() {
            val superMessage = super.message
            return when {
                superMessage != null -> superMessage
                error != null -> error.toString()
                streamError != null -> streamError.toString()
                else -> null
            }
        }

    fun getXMPPError(): XMPPError? = error

    override fun printStackTrace() {
        printStackTrace(System.err)
    }

    override fun printStackTrace(s: PrintStream) {
        super.printStackTrace(s)
        wrappedThrowable?.let {
            s.println("Nested Exception: ")
            it.printStackTrace(s)
        }
    }

    override fun printStackTrace(s: PrintWriter) {
        super.printStackTrace(s)
        wrappedThrowable?.let {
            s.println("Nested Exception: ")
            it.printStackTrace(s)
        }
    }

    override fun toString(): String {
        val sb = StringBuilder()
        super.message?.let {
            sb.append(it)
            sb.append(": ")
        }
        error?.let { sb.append(it) }
        streamError?.let { sb.append(it) }
        wrappedThrowable?.let {
            sb.append("\n  -- caused by: ")
            sb.append(it)
        }
        return sb.toString()
    }
}
