package io.github.magisk317.mipush.common.configurations

import android.os.Build
import io.github.aakira.napier.Napier
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.Callable

object Lisp {
    private val TAG = Lisp::class.java.simpleName
    private val logger = object {
        fun e(msg: String, t: Throwable? = null) = Napier.e(msg, t, tag = TAG)
    }

    fun interface Evaluable {
        fun evaluate(expr: Any?): Any?
    }

    @JvmStatic
    fun evaluate(expr: Any?, extension: Evaluable): Any? {
        if (expr is String) {
            return expr
        }
        if (expr is JSONArray) {
            return evaluate(expr, extension)
        }
        return extension.evaluate(expr)
    }

    private fun evaluate(expr: JSONArray, extension: Evaluable): Any? {
        val method = evaluate(expr.opt(0), extension) as? String ?: return null
        if (method == "cond") {
            return evaluateCond(expr, extension)
        }

        val evaluated = JSONArray().apply {
            put(method)
            for (i in 1 until expr.length()) {
                put(evaluate(expr.opt(i), extension))
            }
        }

        val methods = hashMapOf<String, Callable<Any?>>(
            "hash" to Callable { evaluated.optString(1).hashCode() },
            "decode-uri" to Callable { URLDecoder.decode(evaluated.optString(1), StandardCharsets.UTF_8.name()) },
            "decode-base64" to Callable {
                val base64 = evaluated.optString(1)
                val decoders: Array<Callable<ByteArray>> =
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        arrayOf(
                            Callable { android.util.Base64.decode(base64, android.util.Base64.DEFAULT) },
                            Callable { android.util.Base64.decode(base64, android.util.Base64.URL_SAFE) }
                        )
                    } else {
                        arrayOf(
                            Callable { Base64.getDecoder().decode(base64) },
                            Callable { Base64.getUrlDecoder().decode(base64) },
                            Callable { Base64.getMimeDecoder().decode(base64) }
                        )
                    }
                var err: Exception? = null
                for (decoder in decoders) {
                    try {
                        val decoded = decoder.call()
                        return@Callable String(decoded, StandardCharsets.UTF_8)
                    } catch (e: Exception) {
                        err = e
                    }
                }
                throw (err ?: IllegalStateException("decode-base64 failed"))
            },
            "parse-json" to Callable { JSONTokener(evaluated.optString(1)).nextValue() },
            "property" to Callable {
                val obj = evaluated.opt(2)
                when (obj) {
                    is JSONObject -> obj.opt(evaluated.optString(1))
                    is JSONArray -> obj.opt(evaluated.optInt(1))
                    else -> null
                }
            },
            "replace" to Callable {
                val src = evaluated.optString(1)
                val ptn = evaluated.optString(2)
                val rep = evaluated.optString(3)
                src.replace(ptn.toRegex(), rep)
            }
        )

        val ret = methods[method] ?: return extension.evaluate(evaluated)
        return try {
            ret.call()
        } catch (e: Exception) {
            logger.e(method, e)
            null
        }
    }

    private fun evaluateCond(expr: JSONArray, extension: Evaluable): Any? {
        for (i in 1 until expr.length()) {
            val clause = expr.optJSONArray(i) ?: return null
            val test = clause.opt(0)
            if (test is JSONArray) {
                if (evaluate(test, extension) == true) {
                    var ret: Any? = null
                    for (j in 1 until clause.length()) {
                        ret = evaluate(expr.opt(j), extension)
                    }
                    return ret
                }
            }
            val subCond = JSONArray().apply {
                put("cond")
                put(clause)
            }
            val value = extension.evaluate(subCond)
            if (value != null) {
                return value
            }
        }
        return null
    }
}
