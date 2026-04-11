package io.github.magisk317.mipush.common.utils

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.NonNull
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Code implements port from
 * https://www.cnblogs.com/Imageshop/p/3307308.html
 * AND
 * http://imagej.net/Auto_Threshold
 *
 * @author zts
 */
object ImgUtils {
    private const val NUM_256 = 256

    @JvmStatic
    fun trimImgToCircle(bitmap: Bitmap, colorOutsideCircle: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val radius = min(width, height)
        val finalWidth = radius
        val finalHeight = radius

        val bmOut = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmOut)

        val paint = Paint().apply {
            isAntiAlias = true
            color = -1
        }

        val rect = Rect(0, 0, finalWidth, finalHeight)
        val rectF = RectF(rect)

        canvas.drawColor(colorOutsideCircle)
        canvas.drawCircle(
            (rectF.left + rectF.width() / 2.0).toFloat(),
            (rectF.top + rectF.height() / 2.0).toFloat(),
            (radius / 2.0).toFloat(),
            paint
        )

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)

        return bmOut
    }

    @JvmStatic
    fun trimImgToCircle(colorOutsideCircle: Int, width: Int, height: Int, pixels: IntArray, rExpand: Int) {
        val r = min(width, height) / 2.0 + rExpand

        for (i in 0 until height) {
            for (j in 0 until width) {
                val a = (i - width / 2.0)
                val b = (j - height / 2.0)
                if (a * a + b * b > r * r) {
                    pixels[width * i + j] = colorOutsideCircle
                }
            }
        }
    }

    @JvmStatic
    fun convertToTransparentAndWhite(bitmap: Bitmap): Bitmap {
        val calculateThreshold = calculateThreshold(bitmap)
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in 0 until height) {
            for (j in 0 until width) {
                val dot = pixels[width * i + j]
                val red = (dot and 0x00FF0000) shr 16
                val green = (dot and 0x0000FF00) shr 8
                val blue = dot and 0x000000FF
                val gray = (red * 0.3 + green * 0.59 + blue * 0.11).toInt()

                if (gray > calculateThreshold) {
                    pixels[width * i + j] = Color.TRANSPARENT
                } else {
                    pixels[width * i + j] = Color.WHITE
                }
            }
        }

        trimImgToCircle(Color.TRANSPARENT, width, height, pixels, 0)
        invertColorIfWhitePredominate(width, height, pixels)
        trimImgToCircle(Color.TRANSPARENT, width, height, pixels, 0)

        //todo use bwareaopen
        denoiseWhitePoint(width, height, pixels, 3)

        return cropTransparent(width, height, pixels)
    }

    @NonNull
    @JvmStatic
    private fun cropTransparent(width: Int, height: Int, pixels: IntArray): Bitmap {
        var topPadding = height
        var leftPadding = width
        var rightPadding = width
        var bottomPadding = height

        for (h in 0 until height) {
            for (w in 0 until width) {
                if (pixels[width * h + w] != Color.TRANSPARENT) {
                    topPadding = min(topPadding, h)
                    leftPadding = min(leftPadding, w)
                    rightPadding = min(rightPadding, width - 1 - w)
                    bottomPadding = min(bottomPadding, height - 1 - h)
                }
            }
        }

        val verticalPadding = bottomPadding + topPadding
        val horizontalPadding = leftPadding + rightPadding
        val diff = verticalPadding - horizontalPadding
        if (verticalPadding > horizontalPadding) {
            bottomPadding -= diff / 2
            topPadding -= diff / 2
            bottomPadding = max(bottomPadding, 0)
            topPadding = max(topPadding, 0)
        } else if (verticalPadding < horizontalPadding) {
            leftPadding += diff / 2
            rightPadding += diff / 2
            leftPadding = max(leftPadding, 0)
            rightPadding = max(rightPadding, 0)
        }

        val cropHeight = height - bottomPadding - topPadding
        val cropWidth = width - leftPadding - rightPadding

        val padding = (cropHeight + cropWidth) / 16

        val newPix = IntArray(cropWidth * cropHeight)

        var i = 0
        for (h in topPadding until topPadding + cropHeight) {
            for (w in leftPadding until leftPadding + cropWidth) {
                newPix[i++] = pixels[width * h + w]
            }
        }

        val newBmp = Bitmap.createBitmap(cropWidth + padding * 2, cropHeight + padding * 2, Bitmap.Config.ARGB_8888)
        newBmp.setPixels(newPix, 0, cropWidth, padding, padding, cropWidth, cropHeight)

        return newBmp
    }

    @JvmStatic
    private fun invertColorIfWhitePredominate(width: Int, height: Int, pixels: IntArray) {
        var whiteCnt = 0
        var tsCnt = 0
        for (color in pixels) {
            if (color == Color.TRANSPARENT) {
                tsCnt++
            } else {
                whiteCnt++
            }
        }

        if (whiteCnt > tsCnt) {
            //revert WHITE and TRANSPARENT
            for (i in 0 until height) {
                for (j in 0 until width) {
                    val dot = pixels[width * i + j]
                    if (dot == Color.WHITE) {
                        pixels[width * i + j] = Color.TRANSPARENT
                    } else {
                        pixels[width * i + j] = Color.WHITE
                    }
                }
            }
        }
    }

    @JvmStatic
    private fun denoiseWhitePoint(width: Int, height: Int, pixels: IntArray, exThre: Int) {
        for (i in 1 until height) {
            for (j in 1 until width) {
                val dots = intArrayOf(
                    getPixel(width, pixels, i - 1, j - 1),
                    getPixel(width, pixels, i - 1, j),
                    getPixel(width, pixels, i - 1, j + 1),
                    getPixel(width, pixels, i, j - 1),
                    getPixel(width, pixels, i, j + 1),
                    getPixel(width, pixels, i + 1, j - 1),
                    getPixel(width, pixels, i + 1, j),
                    getPixel(width, pixels, i + 1, j + 1)
                )

                var whCnt = 0
                var trCnt = 0

                for (dot in dots) {
                    if (dot == Color.WHITE) {
                        whCnt++
                    } else {
                        trCnt++
                    }
                }

                if (trCnt + exThre > dots.size) {
                    pixels[width * i + j] = Color.TRANSPARENT
                }
            }
        }
    }

    @JvmStatic
    private fun getPixel(width: Int, pixels: IntArray, h: Int, w: Int): Int {
        if (w >= width) {
            return Color.TRANSPARENT
        }
        if (width * h + w >= pixels.size) {
            return Color.TRANSPARENT
        }
        return pixels[width * h + w]
    }

    @JvmStatic
    private fun getGreyHistogram(bitmap: Bitmap, histogram: IntArray) {
        val width = bitmap.width
        val height = bitmap.height
        for (x in 0 until width) {
            for (y in 0 until height) {
                val dot = bitmap.getPixel(x, y)
                val red = (dot and 0x00FF0000) shr 16
                val green = (dot and 0x0000FF00) shr 8
                val blue = dot and 0x000000FF
                val gray = (red * 0.3 + green * 0.59 + blue * 0.11).toInt()
                histogram[gray]++
            }
        }
    }

    @JvmStatic
    private fun isDimodal(histogram: DoubleArray): Boolean {
        // 对直方图的峰进行计数，只有峰数位2才为双峰
        var count = 0
        for (i in 1 until NUM_256 - 1) {
            if (histogram[i - 1] < histogram[i] && histogram[i + 1] < histogram[i]) {
                count++
                if (count > 2) {
                    return false
                }
            }
        }
        return count == 2
    }

    @JvmStatic
    private fun calculateThreshold(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        trimImgToCircle(Color.WHITE, width, height, pixels, 0)

        val newBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        newBmp.setPixels(pixels, 0, width, 0, 0, width, height)

        val histogram = IntArray(NUM_256)
        getGreyHistogram(newBmp, histogram)

        val thresholds = ArrayList<Int>()
        thresholds.add(calculateThresholdByOSTU(newBmp, histogram))
        thresholds.add(calculateThresholdByMinimum(histogram))
        thresholds.add(calculateThresholdByMean(histogram))

        thresholds.sort()

        return (thresholds[thresholds.size - 1] * 3 + thresholds[thresholds.size - 2]) / 4
    }

    @JvmStatic
    private fun calculateThresholdByOSTU(bitmap: Bitmap, histogram: IntArray): Int {
        val total = bitmap.width * bitmap.height
        var sum = 0.0
        for (i in 0 until NUM_256) {
            sum += i * histogram[i]
        }

        var sumB = 0.0
        var wB = 0

        var varMax = 0.0
        var threshold = 0

        for (i in 0 until NUM_256) {
            wB += histogram[i]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break

            sumB += (i * histogram[i]).toDouble()
            val mB = sumB / wB
            val mF = (sum - sumB) / wF

            val varBetween = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)

            if (varBetween > varMax) {
                varMax = varBetween
                threshold = i
            }
        }

        return threshold
    }

    @JvmStatic
    private fun calculateThresholdByMinimum(histogram: IntArray): Int {
        var y: Int
        var iter = 0
        val histgramc = DoubleArray(NUM_256)
        val histgramcc = DoubleArray(NUM_256)
        for (yIdx in 0 until NUM_256) {
            histgramc[yIdx] = histogram[yIdx].toDouble()
            histgramcc[yIdx] = histogram[yIdx].toDouble()
        }

        while (!isDimodal(histgramcc)) {
            histgramcc[0] = (histgramc[0] + histgramc[0] + histgramc[1]) / 3
            for (yIdx in 1 until NUM_256 - 1) {
                histgramcc[yIdx] = (histgramc[yIdx - 1] + histgramc[yIdx] + histgramc[yIdx + 1]) / 3
            }
            histgramcc[255] = (histgramc[254] + histgramc[255] + histgramc[255]) / 3
            System.arraycopy(histgramcc, 0, histgramc, 0, NUM_256)
            iter++
            if (iter >= 1000) return -1
        }
        // 阈值极为两峰之间的最小值
        var peakFound = false
        for (yIdx in 1 until NUM_256 - 1) {
            if (histgramcc[yIdx - 1] < histgramcc[yIdx] && histgramcc[yIdx + 1] < histgramcc[yIdx]) {
                peakFound = true
            }
            if (peakFound && histgramcc[yIdx - 1] >= histgramcc[yIdx] && histgramcc[yIdx + 1] >= histgramcc[yIdx]) {
                return yIdx - 1
            }
        }
        return -1
    }

    @JvmStatic
    private fun calculateThresholdByMean(histogram: IntArray): Int {
        var sum = 0
        var amount = 0
        for (i in 0 until NUM_256) {
            amount += histogram[i]
            sum += i * histogram[i]
        }
        return if (amount == 0) 0 else sum / amount
    }

    @JvmStatic
    fun drawableToBitmap(drawable: Drawable): Bitmap {
        return if (drawable is BitmapDrawable) {
            drawable.bitmap
        } else {
            val w = if (drawable.intrinsicWidth <= 0) 1 else drawable.intrinsicWidth
            val h = if (drawable.intrinsicHeight <= 0) 1 else drawable.intrinsicHeight
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
    }

    @JvmStatic
    fun scaleImage(bm: Bitmap?, newWidth: Int, newHeight: Int): Bitmap? {
        if (bm == null) return null
        val width = bm.width
        val height = bm.height
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height
        val matrix = Matrix()
        matrix.postScale(scaleWidth, scaleHeight)
        val newbm = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, true)
        if (!bm.isRecycled) {
            bm.recycle()
        }
        return newbm
    }
}
