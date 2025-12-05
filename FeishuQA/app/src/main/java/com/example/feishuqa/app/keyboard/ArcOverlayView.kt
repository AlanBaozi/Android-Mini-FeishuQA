package com.example.feishuqa.app.keyboard

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class ArcOverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL }
    private val path = Path()
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat();
        val h = height.toFloat();
        val arc = 100f
        path.reset(); path.moveTo(0f, h); path.lineTo(0f, 80f)
        path.quadTo(w / 2, -arc, w, 80f)
        path.lineTo(w, h); path.close()
        canvas.drawPath(path, paint)
    }
}