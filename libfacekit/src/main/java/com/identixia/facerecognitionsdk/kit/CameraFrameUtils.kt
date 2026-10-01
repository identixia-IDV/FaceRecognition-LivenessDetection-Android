package com.identixia.facerecognitionsdk.kit


import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.identixia.facerecognitionsdk.FaceRecognitionSDK
import kotlin.math.max
import kotlin.math.roundToInt


object CameraFrameUtils {
    private const val ENGINE_MAX_SIDE = 1280
    private const val PREVIEW_MAX_SIDE = 640


    /** Live Identify/Capture frames: NV21 → yuv2Bitmap (mode 6 back / 7 front) → long side ≤ 640. */
    @JvmStatic
    @SuppressLint("UnsafeOptInUsageError")
    fun fromImageProxy(imageProxy: ImageProxy, backCamera: Boolean): Bitmap? {
        val image = imageProxy.image ?: return null
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val mode = if (backCamera) 6 else 7
        val bitmap = FaceRecognitionSDK.yuv2Bitmap(nv21, image.width, image.height, mode) ?: return null
        val frame = prepareForEngine(bitmap, PREVIEW_MAX_SIDE)
        if (frame !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        return frame
    }


    /** Downscale for live preview / VideoWorker frames (long side ≤ 640). */
    fun prepareForEngine(src: Bitmap, maxSide: Int = PREVIEW_MAX_SIDE): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src
        val longSide = max(w, h)
        if (longSide <= maxSide) return src
        val scale = maxSide.toFloat() / longSide
        val tw = max(1, (w * scale).roundToInt())
        val th = max(1, (h * scale).roundToInt())
        return Bitmap.createScaledBitmap(src, tw, th, true)
    }


    /** Same downscale as FaceRecognitionSDK bitmapToRgb (long side ≤ 1280) for gallery crop coords. */
    fun enginePreparedImage(src: Bitmap, maxSide: Int = ENGINE_MAX_SIDE): Bitmap =
        prepareForEngine(src, maxSide)


    fun cropFace(
        fromEngineImage: Bitmap,
        region: RectF,
        paddingFraction: Float = 0.20f,
    ): Bitmap? {
        val window = cropWindow(fromEngineImage.width, fromEngineImage.height, region, paddingFraction)
            ?: return null
        return try {
            Bitmap.createBitmap(
                fromEngineImage,
                window.left,
                window.top,
                window.width(),
                window.height(),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Same window as [cropFace]; maps full-image landmark points into crop pixel space. */
    fun mapLandmarksToCrop(
        imageW: Int,
        imageH: Int,
        region: RectF,
        landmarks: List<android.graphics.PointF>,
        paddingFraction: Float = 0.20f,
    ): FloatArray {
        val window = cropWindow(imageW, imageH, region, paddingFraction) ?: return FloatArray(0)
        val out = FloatArray(landmarks.size * 2)
        for (i in landmarks.indices) {
            out[i * 2] = landmarks[i].x - window.left
            out[i * 2 + 1] = landmarks[i].y - window.top
        }
        return out
    }

    private fun cropWindow(
        iw: Int,
        ih: Int,
        region: RectF,
        paddingFraction: Float,
    ): Rect? {
        if (iw <= 0 || ih <= 0 || region.width() <= 1f || region.height() <= 1f) return null
        var left = region.left - region.width() * paddingFraction
        var top = region.top - region.height() * paddingFraction
        var right = region.right + region.width() * paddingFraction
        var bottom = region.bottom + region.height() * paddingFraction
        var cropW = right - left
        var cropH = bottom - top
        if (cropW > iw) {
            left = 0f
            cropW = iw.toFloat()
        } else {
            left = left.coerceIn(0f, max(0f, iw - cropW))
        }
        if (cropH > ih) {
            top = 0f
            cropH = ih.toFloat()
        } else {
            top = top.coerceIn(0f, max(0f, ih - cropH))
        }
        val rect = Rect(
            left.roundToInt(),
            top.roundToInt(),
            (left + cropW).roundToInt().coerceAtMost(iw),
            (top + cropH).roundToInt().coerceAtMost(ih),
        )
        if (rect.width() <= 1 || rect.height() <= 1) return null
        return rect
    }


    @JvmStatic
    fun copyArgb(src: Bitmap): Bitmap {
        if (src.config == Bitmap.Config.ARGB_8888 && !src.isRecycled) {
            return src.copy(Bitmap.Config.ARGB_8888, false)
        }
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, null)
        return out
    }
}
