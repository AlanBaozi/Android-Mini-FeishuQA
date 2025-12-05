package com.example.feishuqa.app.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.sin

class BarWaveView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3370FF.toInt(); style = Paint.Style.FILL; strokeCap = Paint.Cap.ROUND
    }
    private var phase = 0f;
    private var targetVol = 0f;
    private var currentVol = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { phase += 0.15f; currentVol += (targetVol - currentVol) * 0.1f; invalidate() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow(); animator.cancel()
    }

    fun updateVolume(v: Float) {
        targetVol = (abs(v) / 10f).coerceIn(0f, 1f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = 10f;
        val g = 8f;
        var sx = (width - (4 * w + 3 * g)) / 2f;
        val cy = height / 2f
        for (i in 0 until 4) {
            var h = 20f + sin(phase + i * 0.8f).toFloat() * 6f
            h += currentVol * 50f * (if (i == 1 || i == 2) 1.5f else 0.8f)
            canvas.drawRoundRect(RectF(sx, cy - h / 2, sx + w, cy + h / 2), w / 2, w / 2, paint)
            sx += w + g
        }
    }
}