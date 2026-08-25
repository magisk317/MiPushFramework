package io.github.magisk317.mipush.notification

import android.graphics.Color
import android.text.Editable
import android.text.Html
import android.text.Spannable
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import org.xml.sax.XMLReader

/**
 * Custom Html.TagHandler that parses `<ft>` tags for rich notification text.
 *
 * Supports:
 * - `<ft color="#ff0000">red text</ft>`
 * - `<ft size="14">bigger text</ft>`
 * - `<ft color="#ff0000" size="14">styled text</ft>`
 *
 * Based on stock xmsf 7.4.67 `com.xiaomi.push.sweetnotification.SweetTagHandler`.
 */
class SweetTagHandler : Html.TagHandler {

    private data class FtProperties(val color: String?, val size: String?)

    private val startIndices = mutableListOf<Int>()
    private val properties = mutableListOf<FtProperties>()

    override fun handleTag(opening: Boolean, tag: String, output: Editable, xmlReader: XMLReader) {
        if (!"ft".equals(tag, ignoreCase = true)) return

        if (opening) {
            startIndices.add(output.length)
            val color = getAttribute(xmlReader, "color")
            val size = getAttribute(xmlReader, "size")
            properties.add(FtProperties(color, size))
        } else {
            if (properties.isEmpty()) return
            val props = properties.removeAt(properties.lastIndex)
            val start = startIndices.removeAt(startIndices.lastIndex)
            val end = output.length

            props.size?.let { sizeStr ->
                val sizeVal = sizeStr.toIntOrNull() ?: return@let
                output.setSpan(
                    AbsoluteSizeSpan(sizeVal, true),
                    start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            props.color?.let { colorStr ->
                try {
                    output.setSpan(
                        ForegroundColorSpan(Color.parseColor(colorStr)),
                        start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } catch (_: IllegalArgumentException) {
                }
            }
        }
    }

    companion object {
        private fun getAttribute(xmlReader: XMLReader, name: String): String? {
            return try {
                val field = xmlReader.javaClass.getDeclaredField("theNewElement")
                field.isAccessible = true
                val element = field.get(xmlReader)
                val attsField = element.javaClass.getDeclaredField("theAtts")
                attsField.isAccessible = true
                val atts = attsField.get(element)
                val dataField = atts.javaClass.getDeclaredField("data")
                dataField.isAccessible = true
                val data = dataField.get(atts) as Array<*>
                val lengthField = atts.javaClass.getDeclaredField("length")
                lengthField.isAccessible = true
                val length = lengthField.get(atts) as Int
                for (i in 0 until length) {
                    val idx = i * 5
                    if (name == data[idx + 1]) {
                        return data[idx + 4] as? String
                    }
                }
                null
            } catch (_: Exception) {
                null
            }
        }

        @JvmStatic
        fun containsFtTag(text: String?): Boolean {
            return text != null && text.contains("<ft", ignoreCase = true)
        }

        @JvmStatic
        fun renderFtHtml(text: String): Spanned {
            return Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT, null, SweetTagHandler())
        }

        @JvmStatic
        fun renderFtHtmlIfNeeded(text: String?): CharSequence {
            val value = text.orEmpty()
            return if (containsFtTag(value)) renderFtHtml(value) else value
        }
    }
}
