package com.identixia.facerecognitionsdk.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Size
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.identixia.facerecognitionsdk.R
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Identity capture guide: scrim, corner brackets, tick ring, flowing progress.
 * Red when blocked; green flow while holding for capture.
 */
class IdentityGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x660F1A22
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(6f)
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(12f)
        strokeCap = Paint.Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        color = 0x88FFFFFF.toInt()
    }
    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val holePath = Path()
    private val bracketPath = Path()
    private val circleRect = RectF()
    private val tmpRoi = RectF()

    private var frameSize: Size? = null
    private var mirrorX = false
    private var state: FACE_CAPTURE_STATE = FACE_CAPTURE_STATE.NO_FACE
    private var progress = 0f
    private var displayProgress = 0f
    private var pulse = 0f
    private var spin = 0f

    private val colorRed = ContextCompat.getColor(context, R.color.ix_danger)
    private val colorGreen = ContextCompat.getColor(context, R.color.ix_ok)
    private val colorAccent = ContextCompat.getColor(context, R.color.ix_accent)
    private val colorWarn = 0xFFB45309.toInt()

    private var pulseAnimator: ValueAnimator? = null
    private var spinAnimator: ValueAnimator? = null

    fun setFrameSize(size: Size?) {
        frameSize = size
        invalidate()
    }

    fun setMirrorX(mirror: Boolean) {
        mirrorX = mirror
        invalidate()
    }

    fun setGuideState(state: FACE_CAPTURE_STATE, progress: Float) {
        this.state = state
        this.progress = progress.coerceIn(0f, 1f)
        invalidate()
    }

    /** @deprecated use [setGuideState] */
    fun setCaptureState(allowed: Boolean, progress: Float) {
        setGuideState(
            if (allowed) FACE_CAPTURE_STATE.CAPTURE_OK else FACE_CAPTURE_STATE.FIT_IN_CIRCLE,
            progress,
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startMotion()
    }

    override fun onDetachedFromWindow() {
        stopMotion()
        super.onDetachedFromWindow()
    }

    private fun startMotion() {
        if (pulseAnimator == null) {
            pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1400L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    pulse = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
        if (spinAnimator == null) {
            spinAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 4800L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    spin = it.animatedValue as Float
                    // Ease displayed progress toward target for smoother flow.
                    displayProgress += (progress - displayProgress) * 0.22f
                    invalidate()
                }
                start()
            }
        }
    }

    private fun stopMotion() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        spinAnimator?.cancel()
        spinAnimator = null
    }

    override fun onDraw(canvas: Canvas) {
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return

        mapRoiToView(viewW, viewH, circleRect)
        val cx = circleRect.centerX()
        val cy = circleRect.centerY()
        val baseR = min(circleRect.width(), circleRect.height()) / 2f
        val searching = state == FACE_CAPTURE_STATE.NO_FACE
        val allowed = state == FACE_CAPTURE_STATE.CAPTURE_OK
        val pulseScale = if (searching) 1f + 0.035f * pulse else if (!allowed) 1f + 0.012f * pulse else 1f
        val radius = baseR * pulseScale

        holePath.reset()
        holePath.addRect(0f, 0f, viewW, viewH, Path.Direction.CW)
        holePath.addCircle(cx, cy, radius, Path.Direction.CCW)
        holePath.fillType = Path.FillType.EVEN_ODD
        canvas.drawPath(holePath, scrimPaint)

        val ringColor = ringColorFor(state)
        trackPaint.color = ColorUtils.setAlphaComponent(ringColor, if (allowed) 90 else 140)
        canvas.drawCircle(cx, cy, radius, trackPaint)

        drawTicks(canvas, cx, cy, radius, ringColor)
        drawSpinArc(canvas, cx, cy, radius, ringColor, searching || !allowed)

        if (searching) {
            drawBrackets(canvas, cx, cy, radius * (1.08f + 0.04f * pulse), ringColor)
        }

        progressPaint.color = ringColor
        glowPaint.color = ColorUtils.setAlphaComponent(
            ringColor,
            (55 + 50 * displayProgress).toInt().coerceIn(0, 120),
        )
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        if (allowed) {
            canvas.drawArc(oval, -90f, 360f * displayProgress, false, glowPaint)
            canvas.drawArc(oval, -90f, 360f * displayProgress, false, progressPaint)
        } else {
            canvas.drawArc(oval, -90f, 360f, false, progressPaint)
        }
    }

    private fun ringColorFor(state: FACE_CAPTURE_STATE): Int = when (state) {
        FACE_CAPTURE_STATE.CAPTURE_OK -> colorGreen
        FACE_CAPTURE_STATE.NO_FACE -> colorAccent
        FACE_CAPTURE_STATE.MULTIPLE_FACES,
        FACE_CAPTURE_STATE.FACE_OCCLUDED,
        FACE_CAPTURE_STATE.SPOOFED_FACE,
        -> colorRed
        FACE_CAPTURE_STATE.FIT_IN_CIRCLE,
        FACE_CAPTURE_STATE.MOVE_CLOSER,
        FACE_CAPTURE_STATE.NO_FRONT,
        FACE_CAPTURE_STATE.EYE_CLOSED,
        FACE_CAPTURE_STATE.MOUTH_OPENED,
        -> colorWarn
    }

    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        tickPaint.color = ColorUtils.setAlphaComponent(color, 160)
        val count = 36
        for (i in 0 until count) {
            val deg = Math.toRadians((i * (360.0 / count)) + spin * 0.15)
            val cos = cos(deg).toFloat()
            val sin = sin(deg).toFloat()
            val major = i % 3 == 0
            val inner = radius + dp(if (major) 4f else 2f)
            val outer = radius + dp(if (major) 12f else 7f)
            canvas.drawLine(
                cx + cos * inner,
                cy + sin * inner,
                cx + cos * outer,
                cy + sin * outer,
                tickPaint,
            )
        }
    }

    private fun drawSpinArc(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        active: Boolean,
    ) {
        if (!active) return
        val paint = Paint(progressPaint)
        paint.strokeWidth = dp(3f)
        paint.color = ColorUtils.setAlphaComponent(color, 180)
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(oval, spin, 54f, false, paint)
        paint.color = ColorUtils.setAlphaComponent(color, 90)
        canvas.drawArc(oval, spin + 180f, 40f, false, paint)
    }

    private fun drawBrackets(canvas: Canvas, cx: Float, cy: Float, half: Float, color: Int) {
        bracketPaint.color = ColorUtils.setAlphaComponent(color, 220)
        val len = half * 0.28f
        val inset = half * 0.72f
        fun corner(ox: Float, oy: Float, sx: Float, sy: Float) {
            bracketPath.reset()
            bracketPath.moveTo(cx + ox * inset, cy + oy * inset + sy * len)
            bracketPath.lineTo(cx + ox * inset, cy + oy * inset)
            bracketPath.lineTo(cx + ox * inset + sx * len, cy + oy * inset)
            canvas.drawPath(bracketPath, bracketPaint)
        }
        corner(-1f, -1f, 1f, 1f)
        corner(1f, -1f, -1f, 1f)
        corner(-1f, 1f, 1f, -1f)
        corner(1f, 1f, -1f, -1f)
    }

    private fun mapRoiToView(viewW: Float, viewH: Float, out: RectF) {
        val fs = frameSize
        if (fs == null || fs.width <= 0 || fs.height <= 0) {
            val m = viewW / 6f
            val side = viewW - 2f * m
            val top = (viewH - side) / 2f
            out.set(m, top, viewW - m, top + side)
            return
        }
        val frameW = fs.width.toFloat()
        val frameH = fs.height.toFloat()
        roiInFrame(fs, tmpRoi)
        val scale = max(viewW / frameW, viewH / frameH)
        val dx = (viewW - frameW * scale) / 2f
        val dy = (viewH - frameH * scale) / 2f
        var left = tmpRoi.left * scale + dx
        var right = tmpRoi.right * scale + dx
        val top = tmpRoi.top * scale + dy
        val bottom = tmpRoi.bottom * scale + dy
        if (mirrorX) {
            val l = viewW - right
            val r = viewW - left
            left = l
            right = r
        }
        out.set(left, top, right, bottom)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    companion object {
        @JvmStatic
        fun roiInFrame(frameSize: Size, out: RectF = RectF()): RectF {
            val margin = frameSize.width / 6
            val side = frameSize.width - 2 * margin
            val top = (frameSize.height - side) / 2f
            out.set(
                margin.toFloat(),
                top,
                (frameSize.width - margin).toFloat(),
                top + side,
            )
            return out
        }
    }
}
