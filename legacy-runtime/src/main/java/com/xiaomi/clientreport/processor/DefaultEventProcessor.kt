package com.xiaomi.clientreport.processor

import android.content.Context
import android.text.TextUtils
import android.text.format.Formatter
import android.util.Base64
import com.xiaomi.channel.commonutils.android.DataCryptUtils
import com.xiaomi.channel.commonutils.file.IOUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.ByteUtils
import com.xiaomi.channel.commonutils.string.XMStringUtils
import com.xiaomi.clientreport.data.BaseClientReport
import com.xiaomi.clientreport.data.EventClientReport
import com.xiaomi.clientreport.manager.ClientReportLogicManager
import com.xiaomi.clientreport.util.ClientReportUtil
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException

open class DefaultEventProcessor(context: Context) : IEventProcessor {

    companion object {
        private const val DATA_FILE_MAX_SIZE = 5242880
        private const val DATA_MAX_SIZE = 4096
        private const val FOLDER = "event"
        private const val MAGIC_NUMBER = -573785174
        private const val MAX_SAME_PRODUCTION_FILE_NUM = 100
        private const val UPLOAD_FOLDER = "eventUploading"
    }

    @JvmField
    protected var mContext: Context = context
    private var mEventMap: HashMap<String, ArrayList<BaseClientReport>>? = null

    init {
        setContext(context)
    }

    fun getFirstEventFileName(baseClientReport: BaseClientReport): String {
        return baseClientReport.production.toString()
    }

    private fun getWriteFileName(baseClientReport: BaseClientReport): String? {
        val externalFilesDir = mContext.getExternalFilesDir(FOLDER)
        val firstEventFileName = getFirstEventFileName(baseClientReport)
        if (externalFilesDir == null) return null
        val str2 = "${externalFilesDir.absolutePath}${File.separator}$firstEventFileName"
        var i = 0
        while (i < MAX_SAME_PRODUCTION_FILE_NUM) {
            val str = "$str2$i"
            if (ClientReportUtil.isFileCanBeUse(mContext, str)) {
                return str
            }
            i++
        }
        return null
    }

    private fun readFile(str: String): List<String> {
        val arrayList = ArrayList<String>()
        var bufferedInputStream: BufferedInputStream? = null
        try {
            bufferedInputStream = BufferedInputStream(FileInputStream(File(str)))
            val bArr = ByteArray(4)
            while (true) {
                val read = bufferedInputStream.read()
                if (read == -1) break
                bArr[0] = read.toByte()
                if (bufferedInputStream.read(bArr, 1, 3) != 3) {
                    MyLog.e("eventData read from cache file failed cause magicNumber error")
                    break
                }
                if (ByteUtils.toInt(bArr) != MAGIC_NUMBER) {
                    MyLog.e("eventData read from cache file failed cause magicNumber error")
                    break
                }
                if (bufferedInputStream.read(bArr) != 4) {
                    MyLog.e("eventData read from cache file failed cause lengthBuffer error")
                    break
                }
                val i = ByteUtils.toInt(bArr)
                if (i < 1 || i > DATA_MAX_SIZE) {
                    MyLog.e("eventData read from cache file failed cause lengthBuffer < 1 || lengthBuffer > 4K")
                    break
                }
                val bArr2 = ByteArray(i)
                var i2 = 0
                while (i2 < i) {
                    val read2 = bufferedInputStream.read(bArr2, i2, i - i2)
                    if (read2 == -1) break
                    i2 += read2
                }
                if (i2 != i) {
                    MyLog.e("eventData read from cache file failed cause buffer size not equal length")
                    break
                }
                val strBytesToString = bytesToString(bArr2)
                if (strBytesToString.isNotEmpty()) {
                    arrayList.add(strBytesToString)
                }
            }
        } catch (e: Exception) {
            MyLog.e(e)
        } finally {
            IOUtils.closeQuietly(bufferedInputStream)
        }
        return arrayList
    }

    private fun releaseLock(randomAccessFile: RandomAccessFile?, fileLock: FileLock?) {
        if (fileLock != null && fileLock.isValid) {
            try {
                fileLock.release()
            } catch (e: IOException) {
                MyLog.e(e)
            }
        }
        IOUtils.closeQuietly(randomAccessFile)
    }

    private fun reportDropFile(str: String, str2: String) {
        val eventClientReportNewEvent =
            ClientReportLogicManager.getInstance(mContext).newEvent(
                5001,
                "24:$str,$str2",
            )
        val arrayList = ArrayList<String>(1)
        arrayList.add(eventClientReportNewEvent.toJsonString())
        send(arrayList)
    }

    private fun write2FileLocked(baseClientReportArr: Array<BaseClientReport>): Array<BaseClientReport>? {
        val writeFileName = getWriteFileName(baseClientReportArr[0]) ?: return null
        var randomAccessFile: RandomAccessFile? = null
        var fileLock: FileLock? = null
        var bufferedOutputStream: BufferedOutputStream? = null
        try {
            val file = File("$writeFileName.lock")
            IOUtils.createFileQuietly(file)
            randomAccessFile = RandomAccessFile(file, "rw")
            fileLock = randomAccessFile.channel.lock()
            bufferedOutputStream = BufferedOutputStream(FileOutputStream(File(writeFileName), true))
            for (i in baseClientReportArr.indices) {
                val baseClientReport = baseClientReportArr[i] ?: continue
                if (!ClientReportUtil.isFileCanBeUse(mContext, writeFileName)) {
                    val length = baseClientReportArr.size - i
                    val baseClientReportArr2 = arrayOfNulls<BaseClientReport>(length)
                    System.arraycopy(baseClientReportArr, i, baseClientReportArr2, 0, length)
                    @Suppress("UNCHECKED_CAST")
                    return baseClientReportArr2 as Array<BaseClientReport>
                }
                val bArrStringToBytes = stringToBytes(baseClientReport.toJsonString())
                if (bArrStringToBytes.isNotEmpty() && bArrStringToBytes.size <= DATA_MAX_SIZE) {
                    bufferedOutputStream.write(ByteUtils.parseInt(MAGIC_NUMBER))
                    bufferedOutputStream.write(ByteUtils.parseInt(bArrStringToBytes.size))
                    bufferedOutputStream.write(bArrStringToBytes)
                    bufferedOutputStream.flush()
                } else {
                    MyLog.e("event data throw a invalid item ")
                }
            }
        } catch (e: Exception) {
            MyLog.e("event data write to cache file failed cause exception", e)
        } finally {
            IOUtils.closeQuietly(bufferedOutputStream)
            releaseLock(randomAccessFile, fileLock)
        }
        return null
    }

    override fun bytesToString(bArr: ByteArray): String {
        if (bArr.isEmpty()) return ""
        if (!ClientReportLogicManager.getInstance(mContext).config.isEventEncrypted) {
            return XMStringUtils.bytesToString(bArr) ?: ""
        }
        val eventKeyWithDefault = ClientReportUtil.getEventKeyWithDefault(mContext)
        if (eventKeyWithDefault.isEmpty()) return ""
        val key = ClientReportUtil.parseKey(eventKeyWithDefault) ?: return ""
        if (key.isEmpty()) return ""
        return try {
            XMStringUtils.bytesToString(
                Base64.decode(
                    DataCryptUtils.mipushDecrypt(key, bArr),
                    2,
                ),
            ) ?: ""
        } catch (e: InvalidAlgorithmParameterException) {
            MyLog.e(e)
            ""
        } catch (e: InvalidKeyException) {
            MyLog.e(e)
            ""
        } catch (e: NoSuchAlgorithmException) {
            MyLog.e(e)
            ""
        } catch (e: BadPaddingException) {
            MyLog.e(e)
            ""
        } catch (e: IllegalBlockSizeException) {
            MyLog.e(e)
            ""
        } catch (e: NoSuchPaddingException) {
            MyLog.e(e)
            ""
        }
    }

    override fun preProcess(baseClientReport: BaseClientReport) {
        if (baseClientReport is EventClientReport && mEventMap != null) {
            val firstEventFileName = getFirstEventFileName(baseClientReport)
            var arrayList2 = mEventMap!![firstEventFileName]
            if (arrayList2 == null) {
                arrayList2 = ArrayList()
            }
            arrayList2.add(baseClientReport)
            mEventMap!![firstEventFileName] = arrayList2
        }
    }

    override fun process() {
        val map = mEventMap ?: return
        if (map.isNotEmpty()) {
            for (key in map.keys) {
                val arrayList = map[key]
                if (arrayList != null && arrayList.isNotEmpty()) {
                    val baseClientReportArr = arrayOfNulls<BaseClientReport>(arrayList.size)
                    arrayList.toArray(baseClientReportArr)
                    @Suppress("UNCHECKED_CAST")
                    write(baseClientReportArr as Array<BaseClientReport>)
                }
            }
        }
        mEventMap!!.clear()
    }

    override fun readAndSend() {
        ClientReportUtil.moveFiles(mContext, FOLDER, UPLOAD_FOLDER)
        val readFileName = ClientReportUtil.getReadFileName(mContext, UPLOAD_FOLDER)
        if (readFileName.isNullOrEmpty()) return
        var randomAccessFile2: RandomAccessFile? = null
        var fileLockLock: FileLock? = null
        var file3: File? = null
        val length = readFileName.size
        var i = 0
        while (i < length) {
            val file4 = readFileName[i]
            if (file4 == null) {
                if (fileLockLock != null && fileLockLock.isValid) {
                    try {
                        fileLockLock.release()
                    } catch (e: IOException) {
                        MyLog.e(e)
                    }
                }
                IOUtils.closeQuietly(randomAccessFile2)
                if (file3 != null) {
                    file3.delete()
                }
            } else {
                val randomAccessFile3 = randomAccessFile2
                val fileLock2 = fileLockLock
                val file5 = file3
                var randomAccessFile4: RandomAccessFile? = randomAccessFile2
                var fileLock3: FileLock? = fileLockLock
                var file: File? = file3
                try {
                    if (file4.length() > DATA_FILE_MAX_SIZE) {
                        MyLog.e("eventData read from cache file failed because ${file4.name} is too big")
                        reportDropFile(file4.name, Formatter.formatFileSize(mContext, file4.length()))
                        file4.delete()
                        if (fileLockLock != null && fileLockLock.isValid) {
                            try {
                                fileLockLock.release()
                            } catch (e: IOException) {
                                MyLog.e(e)
                            }
                        }
                        IOUtils.closeQuietly(randomAccessFile2)
                        if (file3 != null) {
                            file = file3
                            file.delete()
                        }
                    } else {
                        val absolutePath = file4.absolutePath
                        val file6 = File("$absolutePath.lock")
                        IOUtils.createFileQuietly(file6)
                        randomAccessFile2 = RandomAccessFile(file6, "rw")
                        fileLockLock = randomAccessFile2.channel.lock()
                        send(readFile(absolutePath))
                        file4.delete()
                        if (fileLockLock != null && fileLockLock.isValid) {
                            try {
                                fileLockLock.release()
                            } catch (e: IOException) {
                                MyLog.e(e)
                            }
                        }
                        IOUtils.closeQuietly(randomAccessFile2)
                        file = file6
                    }
                } catch (e: Exception) {
                    MyLog.e(e)
                    if (fileLock3 != null && fileLock3.isValid) {
                        try {
                            fileLock3.release()
                        } catch (e: IOException) {
                            MyLog.e(e)
                        }
                    }
                    IOUtils.closeQuietly(randomAccessFile4)
                    if (file != null) {
                        file.delete()
                    }
                } catch (th: Throwable) {
                    if (fileLock2 != null && fileLock2.isValid) {
                        try {
                            fileLock2.release()
                        } catch (e: IOException) {
                            MyLog.e(e)
                        }
                    }
                    IOUtils.closeQuietly(randomAccessFile3)
                    if (file5 != null) {
                        file5.delete()
                    }
                    throw th
                }
                file?.delete()
            }
            i++
        }
    }

    override fun send(list: List<String>) {
        ClientReportUtil.sendFile(mContext, list)
    }

    fun setContext(context: Context) {
        mContext = context
    }

    override fun setEventMap(map: HashMap<String, ArrayList<BaseClientReport>>) {
        mEventMap = map
    }

    override fun stringToBytes(str: String): ByteArray {
        if (str.isEmpty()) return ByteArray(0)
        if (!ClientReportLogicManager.getInstance(mContext).config.isEventEncrypted) {
            return XMStringUtils.getBytes(str) ?: ByteArray(0)
        }
        val eventKeyWithDefault = ClientReportUtil.getEventKeyWithDefault(mContext)
        val bytes = XMStringUtils.getBytes(str)
        if (eventKeyWithDefault.isEmpty() || bytes == null || bytes.size <= 1) return ByteArray(0)
        val key = ClientReportUtil.parseKey(eventKeyWithDefault) ?: return ByteArray(0)
        return try {
            if (key.size > 1) {
                DataCryptUtils.mipushEncrypt(key, Base64.encode(bytes, 2)) ?: ByteArray(0)
            } else {
                ByteArray(0)
            }
        } catch (e: Exception) {
            MyLog.e(e)
            ByteArray(0)
        }
    }

    override fun write(baseClientReportArr: Array<BaseClientReport>) {
        if (baseClientReportArr.isEmpty() || baseClientReportArr[0] == null) {
            MyLog.w("event data write to cache file failed because data null")
            return
        }
        var baseClientReportArr2 = baseClientReportArr
        var baseClientReportArrWrite2FileLocked: Array<BaseClientReport>?
        do {
            baseClientReportArrWrite2FileLocked = write2FileLocked(baseClientReportArr2)
            if (baseClientReportArrWrite2FileLocked == null || baseClientReportArrWrite2FileLocked.isEmpty()) {
                return
            } else {
                baseClientReportArr2 = baseClientReportArrWrite2FileLocked
            }
        } while (baseClientReportArrWrite2FileLocked[0] != null)
    }
}
