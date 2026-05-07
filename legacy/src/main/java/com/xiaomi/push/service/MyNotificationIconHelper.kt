package com.xiaomi.push.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class MyNotificationIconHelper {
    class GetIconResult(
        @JvmField var bitmap: Bitmap?,
        @JvmField var downloadSize: Long
    )

    class GetDataResult(
        @JvmField var data: ByteArray?,
        @JvmField var downloadSize: Int
    )

    companion object {
        const val KiB = 1024
        const val MiB = 1024 * KiB

        private const val CONNECT_TIMEOUT = 8000
        private const val READ_TIMEOUT = 20000
        private const val READ_UNIT = 1024
        private const val STANDARD_DENSITY = 160
        private const val STANDARD_ICON_SIZE = 48

        @JvmStatic
        fun getIconFromUrl(context: Context, urlStr: String, maxDownloadBytes: Int): GetIconResult {
            var isForBitmapSize: InputStream? = null
            val result = GetIconResult(null, 0L)
            try {
                val getDataResult = getDataFromUrl(urlStr, maxDownloadBytes)
                if (getDataResult != null) {
                    result.downloadSize = getDataResult.downloadSize.toLong()
                    val data = getDataResult.data
                    if (data != null) {
                        val bitmapSizeStream = ByteArrayInputStream(data)
                        try {
                            val sampleSize = getSampleSize(context, bitmapSizeStream)
                            val options = BitmapFactory.Options()
                            options.inSampleSize = sampleSize
                            result.bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options)
                            isForBitmapSize = bitmapSizeStream
                        } catch (e: Exception) {
                            isForBitmapSize = bitmapSizeStream
                            MyLog.e(e)
                            IOUtils.closeQuietly(isForBitmapSize)
                            return result
                        }
                    }
                }
            } catch (_: Throwable) {
            }
            IOUtils.closeQuietly(isForBitmapSize)
            return result
        }

        private fun getDataFromUrl(urlStr: String, maxDownloadBytes: Int): GetDataResult? {
            var conn: HttpURLConnection? = null
            var inputStream: InputStream? = null
            return try {
                val url = URL(urlStr)
                conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = CONNECT_TIMEOUT
                conn.readTimeout = READ_TIMEOUT
                conn.connect()

                val contentLen = conn.contentLength
                if (contentLen > maxDownloadBytes) {
                    MyLog.w("Bitmap size is too big, max size is $maxDownloadBytes  contentLen size is $contentLen from url $urlStr")
                    return null
                }

                val responseCode = conn.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    MyLog.w("Invalid Http Response Code $responseCode received")
                    return null
                }

                inputStream = conn.inputStream
                val tempOutStream = ByteArrayOutputStream()
                var availableSpace = maxDownloadBytes
                val dataUnit = ByteArray(READ_UNIT)
                while (availableSpace > 0) {
                    val readCount = inputStream.read(dataUnit, 0, READ_UNIT)
                    if (readCount == -1) {
                        break
                    }
                    availableSpace -= readCount
                    tempOutStream.write(dataUnit, 0, readCount)
                }

                if (availableSpace <= 0) {
                    MyLog.w("length $maxDownloadBytes exhausted.")
                    GetDataResult(null, maxDownloadBytes)
                } else {
                    val data = tempOutStream.toByteArray()
                    GetDataResult(data, data.size)
                }
            } catch (e: IOException) {
                MyLog.e(e)
                null
            } finally {
                IOUtils.closeQuietly(inputStream)
                conn?.disconnect()
            }
        }

        @JvmStatic
        fun getIconFromUri(context: Context, uriStr: String): Bitmap? {
            var bitmap: Bitmap? = null
            val uri = Uri.parse(uriStr)
            var inputStream: InputStream? = null
            var sizeStream: InputStream? = null
            try {
                sizeStream = context.contentResolver.openInputStream(uri)
                val sampleSize = getSampleSize(context, sizeStream)
                inputStream = context.contentResolver.openInputStream(uri)
                val options = BitmapFactory.Options()
                options.inSampleSize = sampleSize
                bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            } catch (e: IOException) {
                MyLog.e(e)
            } finally {
                IOUtils.closeQuietly(inputStream)
                IOUtils.closeQuietly(sizeStream)
            }
            return bitmap
        }

        private fun getSampleSize(context: Context, inputStream: InputStream?): Int {
            val opt = BitmapFactory.Options()
            opt.inJustDecodeBounds = true
            BitmapFactory.decodeStream(inputStream, null, opt)
            if (opt.outWidth == -1 || opt.outHeight == -1) {
                MyLog.w("decode dimension failed for bitmap.")
                return 1
            }
            val screenDensity = context.resources.displayMetrics.densityDpi
            val targetWidth = Math.round((screenDensity.toFloat() / STANDARD_DENSITY) * STANDARD_ICON_SIZE)
            if (opt.outWidth <= targetWidth || opt.outHeight <= targetWidth) {
                return 1
            }
            return minOf(opt.outWidth / targetWidth, opt.outHeight / targetWidth)
        }
    }
}
