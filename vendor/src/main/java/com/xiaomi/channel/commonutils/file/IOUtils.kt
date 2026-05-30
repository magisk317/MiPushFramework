package com.xiaomi.channel.commonutils.file

import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import java.io.*
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object IOUtils {
    private const val BUFFER_SIZE = 1024
    private const val STREAM_BUFFER_SIZE = 4096

    @JvmField
    val SUPPORTED_IMAGE_FORMATS = arrayOf("jpg", "png", "bmp", "gif", "webp")

    @JvmStatic
    fun closeQuietly(closeable: Closeable?) {
        if (closeable != null) {
            try {
                closeable.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copyFile(src: File, dest: File) {
        if (src.absolutePath == dest.absolutePath) return
        FileInputStream(src).use { fis ->
            FileOutputStream(dest).use { fos ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } >= 0) {
                    fos.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    @JvmStatic
    fun createFileQuietly(file: File): Boolean {
        return try {
            if (file.isDirectory) return false
            if (file.exists()) return true
            val parentFile = file.parentFile ?: return false
            if (parentFile.exists() || parentFile.mkdirs()) {
                file.createNewFile()
            } else {
                false
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }

    @JvmStatic
    fun deleteDirs(file: File) {
        MyLog.v("deleteDirs filePath = ${file.absolutePath}")
        if (file.isDirectory) {
            val files = file.listFiles()
            if (files != null && files.isNotEmpty()) {
                for (f in files) {
                    if (f.isFile) {
                        f.delete()
                    } else {
                        deleteDirs(f)
                    }
                }
            }
            file.delete()
        }
    }

    @JvmStatic
    fun fileToStr(file: File): String? {
        val stringWriter = StringWriter()
        var inputStreamReader: InputStreamReader? = null
        try {
            inputStreamReader = InputStreamReader(BufferedInputStream(FileInputStream(file)))
            val cArr = CharArray(2048)
            var i: Int
            while (inputStreamReader.read(cArr).also { i = it } != -1) {
                stringWriter.write(cArr, 0, i)
            }
            return stringWriter.toString()
        } catch (e: IOException) {
            MyLog.v("read file :${file.absolutePath} failure :${e.message}")
            return null
        } finally {
            closeQuietly(inputStreamReader)
            closeQuietly(stringWriter)
        }
    }

    @JvmStatic
    fun gZip(bArr: ByteArray): ByteArray {
        return try {
            val byteArrayOutputStream = ByteArrayOutputStream()
            GZIPOutputStream(byteArrayOutputStream).use { gzos ->
                gzos.write(bArr)
                gzos.finish()
            }
            byteArrayOutputStream.toByteArray().also { byteArrayOutputStream.close() }
        } catch (e: Exception) {
            bArr
        }
    }

    @JvmStatic
    @Throws(NoSuchAlgorithmException::class, IOException::class)
    fun getFileMD5Digest(path: String): ByteArray {
        val messageDigest = MessageDigest.getInstance("MD5")
        FileInputStream(File(path)).use { fis ->
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            var i: Int
            while (fis.read(buffer).also { i = it } != -1) {
                messageDigest.update(buffer, 0, i)
            }
        }
        return messageDigest.digest()
    }

    @JvmStatic
    @Throws(NoSuchAlgorithmException::class, IOException::class)
    fun getFileSha1Digest(path: String): ByteArray {
        val messageDigest = MessageDigest.getInstance("SHA1")
        FileInputStream(File(path)).use { fis ->
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            var i: Int
            while (fis.read(buffer).also { i = it } != -1) {
                messageDigest.update(buffer, 0, i)
            }
        }
        return messageDigest.digest()
    }

    @JvmStatic
    fun getFileSuffix(path: String): String {
        val lastDotIndex = path.lastIndexOf('.')
        return if (lastDotIndex > 0) path.substring(lastDotIndex + 1) else ""
    }

    @JvmStatic
    fun hideFromMediaScanner(dir: File) {
        val nomedia = File(dir, ".nomedia")
        if (nomedia.exists() && nomedia.isFile) return
        try {
            nomedia.createNewFile()
        } catch (e: IOException) {
            MyLog.e(e)
        }
    }

    @JvmStatic
    fun isSupportImageSuffix(suffix: String?): Boolean {
        if (TextUtils.isEmpty(suffix)) return false
        return SUPPORTED_IMAGE_FORMATS.any { it.equals(suffix, ignoreCase = true) }
    }

    @JvmStatic
    fun readInputStream(inputStream: InputStream): ByteArray? {
        val byteArrayOutputStream = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        return try {
            var i: Int
            while (inputStream.read(buffer, 0, 8192).also { i = it } > 0) {
                byteArrayOutputStream.write(buffer, 0, i)
            }
            byteArrayOutputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            closeQuietly(inputStream)
            closeQuietly(byteArrayOutputStream)
        }
    }

    @JvmStatic
    fun remove(file: File) {
        if (!file.isDirectory) {
            if (file.exists()) file.delete()
        } else {
            file.listFiles()?.forEach { remove(it) }
            file.delete()
        }
    }

    @JvmStatic
    fun strToFile(file: File, content: String?) {
        if (content == null) return
        if (!file.exists()) {
            MyLog.v("mkdir ${file.absolutePath}")
            file.parentFile?.mkdirs()
        }
        var bufferedWriter: BufferedWriter? = null
        try {
            bufferedWriter = BufferedWriter(OutputStreamWriter(FileOutputStream(file)))
            bufferedWriter.write(content)
        } catch (e: IOException) {
            MyLog.v("write file :${file.absolutePath} failure :${e.message}")
        } finally {
            closeQuietly(bufferedWriter)
        }
    }

    @JvmStatic
    fun unZip(srcZip: String, destDir: String): Boolean {
        if (TextUtils.isEmpty(destDir) || TextUtils.isEmpty(srcZip)) return false
        val outDir = if (destDir.endsWith("/")) destDir else "$destDir/"
        return try {
            ZipInputStream(BufferedInputStream(FileInputStream(srcZip))).use { zipInputStream ->
                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                while (true) {
                    val entry = zipInputStream.nextEntry ?: return@use true
                    val name = entry.name
                    val file = File(outDir + name)
                    if (!name.endsWith("/")) {
                        val parentStr = file.parent ?: continue
                        val parent = File(parentStr)
                        if (!parent.exists() || !parent.isDirectory) {
                            parent.mkdirs()
                            hideFromMediaScanner(parent)
                        }
                        BufferedOutputStream(FileOutputStream(file), STREAM_BUFFER_SIZE).use { bos ->
                            var i: Int
                            while (zipInputStream.read(buffer, 0, STREAM_BUFFER_SIZE).also { i = it } != -1) {
                                bos.write(buffer, 0, i)
                            }
                            bos.flush()
                        }
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                true
            }
        } catch (e: IOException) {
            MyLog.e("unzip failed", e)
            false
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun zip(zipOutputStream: ZipOutputStream, file: File, path: String?, fileFilter: FileFilter?) {
        val str2 = path ?: ""
        var fileInputStream: FileInputStream? = null
        try {
            if (file.isDirectory) {
                val listFiles = fileFilter?.let { file.listFiles(it) } ?: file.listFiles()
                zipOutputStream.putNextEntry(ZipEntry("$str2${File.separator}"))
                val str3 = if (TextUtils.isEmpty(str2)) "" else "$str2${File.separator}"
                listFiles?.forEach { f ->
                    zip(zipOutputStream, f, "$str3${f.name}", null)
                }
                val dirFiles = file.listFiles { f -> f.isDirectory }
                if (dirFiles != null) {
                    for (f in dirFiles) {
                        zip(zipOutputStream, f, "$str3${File.separator}${f.name}", fileFilter)
                    }
                }
                return
            }
            if (TextUtils.isEmpty(str2)) {
                zipOutputStream.putNextEntry(ZipEntry("${Date().time}.txt"))
            } else {
                zipOutputStream.putNextEntry(ZipEntry(str2))
            }
            fileInputStream = FileInputStream(file)
            val buffer = ByteArray(BUFFER_SIZE)
            var read: Int
            while (fileInputStream.read(buffer).also { read = it } != -1) {
                zipOutputStream.write(buffer, 0, read)
            }
        } catch (e: IOException) {
            MyLog.e("zipFiction failed with exception: ${e}")
        } finally {
            closeQuietly(fileInputStream)
        }
    }

    @JvmStatic
    fun zip(zipOutputStream: ZipOutputStream, entryName: String?, inputStream: InputStream) {
        try {
            if (TextUtils.isEmpty(entryName)) {
                zipOutputStream.putNextEntry(ZipEntry("${Date().time}.txt"))
            } else {
                zipOutputStream.putNextEntry(ZipEntry(entryName))
            }
            val buffer = ByteArray(BUFFER_SIZE)
            var i: Int
            while (inputStream.read(buffer).also { i = it } != -1) {
                zipOutputStream.write(buffer, 0, i)
            }
        } catch (e: IOException) {
            MyLog.e("zipFiction failed with exception: ${e}")
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun zip(file: File, zipFile: File) {
        var zipOutputStream: ZipOutputStream? = null
        try {
            zipOutputStream = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile)))
            zipOutputStream.setLevel(Deflater.BEST_SPEED)
            zip(zipOutputStream, file, file.name, null)
        } finally {
            closeQuietly(zipOutputStream)
        }
    }
}
