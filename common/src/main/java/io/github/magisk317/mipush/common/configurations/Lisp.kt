package io.github.magisk317.mipush.common.configurations

import io.github.magisk317.mipush.common.configurations.LispEvaluator
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Source-compatible facade for configuration callers.
 * Expression semantics live in [LispEvaluator]; JVM-only codecs stay here.
 */
object Lisp {
    fun interface Evaluable {
        fun evaluate(expr: Any?): Any?
    }

    @JvmStatic
    fun evaluate(expr: Any?, extension: Evaluable): Any? {
        return LispEvaluator.evaluate(
            expr = expr,
            extension = LispEvaluator.Extension { evaluated -> extension.evaluate(evaluated) },
            codec = JvmCodec,
        )
    }

    private object JvmCodec : LispEvaluator.Codec {
        override fun decodeUri(value: String): String? {
            return runCatching {
                URLDecoder.decode(value, StandardCharsets.UTF_8.name())
            }.getOrNull()
        }

        override fun decodeBase64(value: String): String? {
            val decoders = listOf(
                Base64.getDecoder(),
                Base64.getUrlDecoder(),
                Base64.getMimeDecoder(),
            )
            return decoders.firstNotNullOfOrNull { decoder ->
                runCatching {
                    String(decoder.decode(value), StandardCharsets.UTF_8)
                }.getOrNull()
            }
        }
    }
}
