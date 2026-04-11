package com.xiaomi.push.service

import android.content.Context
import android.graphics.Bitmap
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object MIPushOnlineResourceSupport {
    private const val MAX_DOWNLOAD_ONLINE_PICTURE_WAIT_SECONDS = 180L
    private val threadPool: ExecutorService = Executors.newCachedThreadPool()

    @JvmStatic
    fun getOnlinePictureResource(context: Context, picUrl: String, isSizeLimited: Boolean): Bitmap? {
        val future = threadPool.submit(DownloadOnlinePicTask(picUrl, context, isSizeLimited))
        return try {
            future.get(MAX_DOWNLOAD_ONLINE_PICTURE_WAIT_SECONDS, TimeUnit.SECONDS).also { bitmap ->
                if (bitmap == null) {
                    future.cancel(true)
                }
            }
        } catch (e: InterruptedException) {
            MyLog.e(e)
            future.cancel(true)
            null
        } catch (e: ExecutionException) {
            MyLog.e(e)
            future.cancel(true)
            null
        } catch (e: TimeoutException) {
            MyLog.e(e)
            future.cancel(true)
            null
        } catch (t: Throwable) {
            future.cancel(true)
            throw t
        }
    }

    private class DownloadOnlinePicTask(
        private val picUrl: String,
        private val context: Context,
        private val isSizeLimited: Boolean,
    ) : Callable<Bitmap?> {
        override fun call(): Bitmap? {
            if (TextUtils.isEmpty(picUrl)) {
                MyLog.w("Failed get online picture/icon resource cause picUrl is empty")
                return null
            }
            if (picUrl.startsWith("http")) {
                return NotificationIconHelper.getIconFromUrl(context, picUrl, isSizeLimited)?.bitmap.also { bitmap ->
                    if (bitmap == null) {
                        MyLog.w("Failed get online picture/icon resource")
                    }
                }
            }
            return NotificationIconHelper.getIconFromUri(context, picUrl).also { bitmap ->
                if (bitmap == null) {
                    MyLog.w("Failed get online picture/icon resource")
                }
            }
        }
    }
}
