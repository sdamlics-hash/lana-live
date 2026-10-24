package ai.dreibus.lanalive

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View

/** Native robot mascot; animation reflects runtime state, never simulated agent work. */
internal class LanaPresenceView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
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
    private fun drawRobot(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        canvas.save()
        canvas.translate(cx, cy + if (active) radius * .025f * phase else 0f)
        canvas.scale(radius / 100f, radius / 100f)
        fun fill(color: Int) { paint.style = Paint.Style.FILL; paint.color = color }
        val shell = Color.rgb(237, 225, 204)
        val coral = Color.rgb(218, 126, 103)
        val leaf = Color.rgb(79, 151, 101)
        fill(Color.rgb(227, 217, 198))
        canvas.drawOval(-56f, 66f, 56f, 82f, paint)
        fill(shell)
        canvas.drawRoundRect(-43f, 32f, 43f, 77f, 23f, 23f, paint)
        canvas.drawRoundRect(-77f, -21f, -55f, 25f, 11f, 11f, paint)
        canvas.drawRoundRect(55f, -21f, 77f, 25f, 11f, 11f, paint)
        fill(coral)
        canvas.drawRoundRect(-65f, -48f, 65f, 48f, 29f, 29f, paint)
        fill(shell)
        canvas.drawRoundRect(-61f, -46f, 61f, 43f, 27f, 27f, paint)
        canvas.drawRoundRect(-4f, -72f, 4f, -43f, 4f, 4f, paint)
        fill(leaf)
        canvas.drawOval(-3f, -86f, 25f, -68f, paint)
        canvas.drawOval(-24f, -79f, 2f, -63f, paint)
        fill(Color.rgb(73, 88, 79))
        canvas.drawRoundRect(-49f, -31f, 49f, 25f, 20f, 20f, paint)
        fill(Color.rgb(246, 235, 195))
        val eyeHeight = if (state == "Остановлена") 2f else 8f
        canvas.drawRoundRect(-31f, -10f-eyeHeight, -14f, -10f+eyeHeight, 8f, 8f, paint)
        canvas.drawRoundRect(14f, -10f-eyeHeight, 31f, -10f+eyeHeight, 8f, 8f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(-10f, -1f, 10f, 13f, 10f, 160f, false, paint)
        fill(coral)
        canvas.drawOval(-40f, 5f, -29f, 11f, paint)
        canvas.drawOval(29f, 5f, 40f, 11f, paint)
        canvas.drawCircle(0f, 55f, 8f, paint)
        fill(Color.rgb(251, 246, 235))
        canvas.drawCircle(-2f, 53f, 2f, paint)
        canvas.restore()
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
        drawRobot(canvas, cx, cy, radius)
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
