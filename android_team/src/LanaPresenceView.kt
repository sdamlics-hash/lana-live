package ai.dreibus.lanalive

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Animation follows the actual status text; it is not an agent activity simulation. */
internal class LanaPresenceView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var state = "Готова"
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800L; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
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
        if (isAttachedToWindow && isShown && state in setOf("Слушаю", "Думаю", "Отвечаю")) {
            if (!animator.isStarted) animator.start()
        } else { animator.cancel(); phase = 0f }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); if (isAttachedToWindow) updateAnimation() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height * .45f
        val r = minOf(width, height) * (.25f + phase * .025f)
        paint.color = Color.rgb(61, 177, 77)
        canvas.drawCircle(cx, cy, r, paint)
        paint.color = Color.rgb(116, 212, 72)
        canvas.save(); canvas.rotate(-32f, cx - r, cy - r)
        canvas.drawOval(cx - r * 1.4f, cy - r * 1.25f, cx + r * .1f, cy - r * .45f, paint); canvas.restore()
        paint.color = Color.rgb(34, 133, 57)
        canvas.save(); canvas.rotate(28f, cx + r, cy - r)
        canvas.drawOval(cx - r * .1f, cy - r * 1.25f, cx + r * 1.4f, cy - r * .45f, paint); canvas.restore()
        paint.color = Color.WHITE
        canvas.drawCircle(cx - r * .32f, cy - r * .12f, r * .09f, paint)
        canvas.drawCircle(cx + r * .32f, cy - r * .12f, r * .09f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = r * .07f
        canvas.drawArc(cx - r * .35f, cy, cx + r * .35f, cy + r * (.35f + if (state == "Отвечаю") phase * .15f else 0f), 0f, 180f, false, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(42, 111, 48); paint.textSize = height * .13f; paint.textAlign = Paint.Align.CENTER
        canvas.drawText(state, cx, height * .93f, paint)
    }
}
