package com.xiaomi.push.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import com.xiaomi.channel.commonutils.logger.MyLog;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class MIPushOnlineResourceSupport {
    private static final int MAX_DOWNLOAD_ONLINE_PICTURE_WAIT_SECONDS = 180;
    private static final ExecutorService THREAD_POOL = Executors.newCachedThreadPool();

    private MIPushOnlineResourceSupport() {
    }

    static Bitmap getOnlinePictureResource(Context context, String str, boolean z) {
        Future<Bitmap> futureSubmit = THREAD_POOL.submit(new DownloadOnlinePicTask(str, context, z));
        try {
            Bitmap bitmap = futureSubmit.get(MAX_DOWNLOAD_ONLINE_PICTURE_WAIT_SECONDS, TimeUnit.SECONDS);
            if (bitmap == null) {
                futureSubmit.cancel(true);
            }
            return bitmap;
        } catch (InterruptedException e) {
            MyLog.e(e);
            futureSubmit.cancel(true);
            return null;
        } catch (ExecutionException e2) {
            MyLog.e(e2);
            futureSubmit.cancel(true);
            return null;
        } catch (TimeoutException e3) {
            MyLog.e(e3);
            futureSubmit.cancel(true);
            return null;
        } catch (Throwable th) {
            futureSubmit.cancel(true);
            throw th;
        }
    }

    private static final class DownloadOnlinePicTask implements Callable<Bitmap> {
        private final Context context;
        private final boolean isSizeLimited;
        private final String picUrl;

        private DownloadOnlinePicTask(String str, Context context, boolean z) {
            this.context = context;
            this.picUrl = str;
            this.isSizeLimited = z;
        }

        @Override
        public Bitmap call() {
            Bitmap bitmap = null;
            if (TextUtils.isEmpty(this.picUrl)) {
                MyLog.w("Failed get online picture/icon resource cause picUrl is empty");
            } else if (this.picUrl.startsWith("http")) {
                NotificationIconHelper.GetIconResult iconFromUrl = NotificationIconHelper.getIconFromUrl(this.context, this.picUrl, this.isSizeLimited);
                if (iconFromUrl != null) {
                    bitmap = iconFromUrl.bitmap;
                } else {
                    MyLog.w("Failed get online picture/icon resource");
                }
            } else {
                bitmap = NotificationIconHelper.getIconFromUri(this.context, this.picUrl);
                if (bitmap == null) {
                    MyLog.w("Failed get online picture/icon resource");
                }
            }
            return bitmap;
        }
    }
}
