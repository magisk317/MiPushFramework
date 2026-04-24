package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.xiaomi.channel.commonutils.file.FileUtils
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.string.XMStringUtils
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

object NotificationIconHelper {
    private const val BIG_PICTURE_MAX_SIZE = 2_048_000
    private const val CLEAN_ICON_EXIST_PERIOD = 1_209_600_000L
    private const val CONNECT_TIMEOUT = 8000
    private const val MAX_SIZE = 102_400
    private const val PIC_FILE_DIR_PATH = "mipush_icon"
    private const val READ_TIMEOUT = 20_000
    private const val READ_UNIT = 1024
    private const val SMALL_ICON_FILE_DIR_MAX_SIZE = 15_728_640L
    private const val STANDARD_DENSITY = 160
    private const val STANDARD_ICON_SIZE = 48

    @JvmStatic
    var currentPicFileSize: Long = 0

    class GetDataResult(
        @JvmField var data: ByteArray?,
        @JvmField var downloadSize: Int,
    )

    class GetIconResult(
        @JvmField var bitmap: Bitmap?,
        @JvmField var downloadSize: Long,
    )

    @JvmStatic
    fun getIconFromUri(context: Context, uriString: String): Bitmap? {
        val uri = Uri.parse(uriString)
        var inputStream: InputStream? = null
        var sizeStream: InputStream? = null
        return try {
            sizeStream = context.contentResolver.openInputStream(uri)
            val sampleSize = getSampleSize(context, sizeStream)
            inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeStream(inputStream, null, options)
        } catch (e: IOException) {
            MyLog.e(e)
            null
        } finally {
            IOUtils.closeQuietly(inputStream)
            IOUtils.closeQuietly(sizeStream)
        }
    }

    @JvmStatic
    fun getIconFromUrl(context: Context, url: String, isSizeLimited: Boolean): GetIconResult {
        val result = GetIconResult(null, 0L)
        getBitmapFromFile(context, url)?.let {
            result.bitmap = it
            return result
        }
        var bitmapSizeStream: ByteArrayInputStream? = null
        try {
            val dataResult = getDataFromUrl(url, isSizeLimited) ?: return result
            result.downloadSize = dataResult.downloadSize.toLong()
            val data = dataResult.data ?: return result
            if (isSizeLimited) {
                bitmapSizeStream = ByteArrayInputStream(data)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = getSampleSize(context, bitmapSizeStream)
                }
                result.bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options)
                savePic2File(context, data, url)
            } else {
                result.bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            }
        } catch (e: Exception) {
            MyLog.e(e)
        } finally {
            IOUtils.closeQuietly(bitmapSizeStream)
        }
        return result
    }

    private fun cleanCachedPic(context: Context) {
        val directory = File(context.cacheDir.path + File.separator + PIC_FILE_DIR_PATH)
        if (!directory.exists()) {
            return
        }
        if (currentPicFileSize == 0L) {
            currentPicFileSize = FileUtils.getFolderSize(directory)
        }
        if (currentPicFileSize > SMALL_ICON_FILE_DIR_MAX_SIZE) {
            try {
                directory.listFiles()?.forEach { file ->
                    if (!file.isDirectory && kotlin.math.abs(System.currentTimeMillis() - file.lastModified()) > CLEAN_ICON_EXIST_PERIOD) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                MyLog.e(e)
            }
            currentPicFileSize = 0L
        }
    }

    private fun getBitmapFromFile(context: Context, url: String): Bitmap? {
        val file = File(context.cacheDir.path + File.separator + PIC_FILE_DIR_PATH, XMStringUtils.getMd5Digest(url))
        if (!file.exists()) {
            return null
        }
        var inputStream: FileInputStream? = null
        return try {
            inputStream = FileInputStream(file)
            BitmapFactory.decodeStream(inputStream).also {
                file.setLastModified(System.currentTimeMillis())
            }
        } catch (e: Exception) {
            MyLog.e(e)
            null
        } finally {
            IOUtils.closeQuietly(inputStream)
        }
    }

    private fun getDataFromUrl(urlString: String, sizeLimited: Boolean): GetDataResult? {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        return try {
            connection = URL(urlString).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty(
                "User-agent",
                "Mozilla/5.0 (Linux; U;) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/71.0.3578.141 Mobile Safari/537.36 XiaoMi/MiuiBrowser",
            )
            connection.connect()
            val contentLength = connection.contentLength
            if (sizeLimited && contentLength > MAX_SIZE) {
                MyLog.w("Bitmap size is too big, max size is 102400  contentLen size is $contentLength from url $urlString")
                return null
            }
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                MyLog.w("Invalid Http Response Code $responseCode received")
                return null
            }
            inputStream = connection.inputStream
            val output = ByteArrayOutputStream()
            var availableBytes = if (sizeLimited) MAX_SIZE else BIG_PICTURE_MAX_SIZE
            val chunk = ByteArray(READ_UNIT)
            while (availableBytes > 0) {
                val read = inputStream.read(chunk, 0, READ_UNIT)
                if (read == -1) {
                    break
                }
                availableBytes -= read
                output.write(chunk, 0, read)
            }
            if (availableBytes <= 0) {
                MyLog.w("length ${if (sizeLimited) MAX_SIZE else BIG_PICTURE_MAX_SIZE} exhausted.")
                GetDataResult(null, if (sizeLimited) MAX_SIZE else BIG_PICTURE_MAX_SIZE)
            } else {
                val data = output.toByteArray()
                GetDataResult(data, data.size)
            }
        } catch (_: SocketTimeoutException) {
            MyLog.e("Connect timeout to $urlString")
            null
        } catch (e: IOException) {
            MyLog.e(e)
            null
        } finally {
            IOUtils.closeQuietly(inputStream)
            connection?.disconnect()
        }
    }

    private fun getSampleSize(context: Context, inputStream: InputStream?): Int {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        if (options.outWidth == -1 || options.outHeight == -1) {
            MyLog.w("decode dimension failed for bitmap.")
            return 1
        }
        val targetWidth = kotlin.math.round((context.resources.displayMetrics.densityDpi / STANDARD_DENSITY.toFloat()) * STANDARD_ICON_SIZE).toInt()
        if (options.outWidth <= targetWidth || options.outHeight <= targetWidth) {
            return 1
        }
        return minOf(options.outWidth / targetWidth, options.outHeight / targetWidth)
    }

    private fun savePic2File(context: Context, data: ByteArray?, url: String) {
        if (data == null) {
            MyLog.w("cannot save small icon cause bitmap is null")
            return
        }
        cleanCachedPic(context)
        val directory = File(context.cacheDir.path + File.separator + PIC_FILE_DIR_PATH)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val file = File(directory, XMStringUtils.getMd5Digest(url))
        var fileOutputStream: FileOutputStream? = null
        var outputStream: BufferedOutputStream? = null
        try {
            if (!file.exists()) {
                file.createNewFile()
            }
            fileOutputStream = FileOutputStream(file)
            outputStream = BufferedOutputStream(fileOutputStream)
            outputStream.write(data)
            outputStream.flush()
            if (currentPicFileSize == 0L) {
                currentPicFileSize = FileUtils.getFolderSize(File(context.cacheDir.path + File.separator + PIC_FILE_DIR_PATH)) + file.length()
            }
        } catch (e: Exception) {
            MyLog.e(e)
        } finally {
            IOUtils.closeQuietly(outputStream)
            IOUtils.closeQuietly(fileOutputStream)
        }
    }
}
