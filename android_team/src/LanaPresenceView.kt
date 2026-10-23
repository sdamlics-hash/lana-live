package ai.dreibus.lanalive

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View

/** Local portrait; motion indicates actual runtime state, never simulated agent work. */
internal class LanaPresenceView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val portrait by lazy { BitmapFactory.decodeResource(resources, R.drawable.lana_portrait) }
    private var phase = 0f
    private var state = "Готова"
    private val active get() = state in setOf("Слушаю", "Думаю", "Отвечаю")
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600L; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }
    fun update(value: String) {
        state = when {
            value.startsWith("Слушаю") -> "Слушаю"
            value.startsWith("Думаю") || value.startsWith("Передаю") -> "Думаю"
            value.startsWith("Отвечаю") -> "Отвечаю"
            value.contains("остановлен", true) -> "Остановлена"
            else -> value.take(60)
        }
        contentDescription = "Лана: $state"
        updateAnimation(); invalidate()
    }
    private fun updateAnimation() {
        if (isAttachedToWindow && isShown && active) {
            if (!animator.isStarted) animator.start()
        } else { animator.cancel(); phase = 0f }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) updateAnimation()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val radius = minOf(width * .38f, height * .36f)
        val cy = height * .41f
        val accent = when (state) {
            "Слушаю" -> Color.rgb(63,140,107)
            "Думаю" -> Color.rgb(184,140,80)
            "Отвечаю" -> Color.rgb(188,112,100)
            else -> Color.rgb(142,157,144)
        }
        paint.shader = null; paint.style = Paint.Style.FILL
        paint.color = Color.argb(if (active) (22 + phase*20).toInt() else 18, 93,139,105)
        canvas.drawCircle(cx,cy,radius*(1.10f+phase*.035f),paint)
        val bounds = RectF(cx-radius,cy-radius,cx+radius,cy+radius)
        canvas.save()
        canvas.clipPath(Path().apply { addCircle(cx,cy,radius,Path.Direction.CW) })
        paint.color = Color.rgb(250,244,235)
        canvas.drawRect(bounds,paint)
        portrait?.let { canvas.drawBitmap(it,null,bounds,paint) }
        canvas.restore()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = resources.displayMetrics.density * 2f
        paint.color = accent; paint.strokeCap = Paint.Cap.ROUND
        if (active) {
            val start = if (state == "Думаю") 30f+phase*240f else 35f
            canvas.drawArc(cx-radius*1.08f,cy-radius*1.08f,cx+radius*1.08f,cy+radius*1.08f,start,100f+phase*55f,false,paint)
        }
        // A voice meter is decorative state feedback, not an audio-amplitude claim.
        if (state == "Отвечаю") {
            paint.style = Paint.Style.FILL
            for (i in -2..2) {
                val x = cx+i*radius*.12f
                val bar = radius*(.04f + .08f*(1f-kotlin.math.abs(i)/3f)*phase)
                canvas.drawRoundRect(x-radius*.025f,cy+radius*.84f-bar,x+radius*.025f,cy+radius*.84f+bar,radius*.02f,radius*.02f,paint)
            }
        }
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-medium",Typeface.NORMAL)
        paint.textSize = minOf(14f*resources.displayMetrics.scaledDensity,height*.085f)
        paint.color = Color.rgb(81,99,85)
        canvas.drawText(state,cx,height*.96f,paint)
    }
}
