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
        val cx = width / 2f; val cy = height * .43f
        val r = minOf(width, height) * .29f
        val active = state in setOf("Слушаю", "Думаю", "Отвечаю")
        paint.style = Paint.Style.STROKE; paint.strokeWidth = r * .035f
        paint.color = Color.argb(if (active) (75 + phase * 110).toInt() else 65,36,146,86)
        canvas.drawCircle(cx,cy,r*(1.22f+phase*.09f),paint)
        canvas.save(); canvas.rotate(if (state == "Думаю") phase * 180f else -25f,cx,cy)
        paint.strokeWidth = r*.08f; paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.rgb(78,178,91)
        canvas.drawArc(cx-r*1.24f,cy-r*1.24f,cx+r*1.24f,cy+r*1.24f,20f,95f,false,paint)
        canvas.restore(); paint.style = Paint.Style.FILL
        // A stylized female portrait with emerald hair and a leaf crown.
        paint.shader = android.graphics.LinearGradient(cx-r,cy-r,cx+r,cy+r,
            Color.rgb(13,86,63),Color.rgb(55,151,85),android.graphics.Shader.TileMode.CLAMP)
        canvas.drawOval(cx-r*.86f,cy-r*1.03f,cx+r*.86f,cy+r*.85f,paint)
        canvas.drawOval(cx-r,cy+r*.5f,cx+r,cy+r*1.1f,paint)
        paint.shader = null; paint.color = Color.rgb(246,220,191)
        canvas.drawRoundRect(cx-r*.18f,cy+r*.38f,cx+r*.18f,cy+r*.8f,r*.12f,r*.12f,paint)
        canvas.drawOval(cx-r*.53f,cy-r*.65f,cx+r*.53f,cy+r*.57f,paint)
        paint.color = Color.rgb(22,99,66)
        val hair = android.graphics.Path().apply {
            moveTo(cx-r*.63f,cy-r*.05f); cubicTo(cx-r*.73f,cy-r*1.12f,cx+r*.5f,cy-r*1.12f,cx+r*.61f,cy-r*.1f)
            cubicTo(cx+r*.3f,cy-r*.26f,cx+r*.03f,cy-r*.56f,cx-r*.09f,cy-r*.66f)
            cubicTo(cx-r*.23f,cy-r*.25f,cx-r*.42f,cy-r*.14f,cx-r*.63f,cy-r*.05f)
        }; canvas.drawPath(hair,paint)
        paint.color = Color.rgb(38,82,57); paint.strokeWidth = r*.045f; paint.style = Paint.Style.STROKE
        canvas.drawArc(cx-r*.37f,cy-r*.09f,cx-r*.1f,cy+r*.06f,190f,150f,false,paint)
        canvas.drawArc(cx+r*.1f,cy-r*.09f,cx+r*.37f,cy+r*.06f,190f,150f,false,paint)
        paint.color = Color.rgb(170,91,81)
        val mouth = if (state == "Отвечаю") .08f + phase*.14f else .07f
        canvas.drawOval(cx-r*.15f,cy+r*.28f,cx+r*.15f,cy+r*(.28f+mouth),paint)
        paint.style = Paint.Style.FILL
        for (i in 0..2) {
            paint.color = if (i == 1) Color.rgb(122,196,76) else Color.rgb(66,153,68)
            canvas.save(); canvas.rotate((i-1)*32f,cx,cy-r*.95f)
            canvas.drawOval(cx-r*.14f,cy-r*1.28f,cx+r*.14f,cy-r*.79f,paint); canvas.restore()
        }
        paint.color = Color.rgb(42,111,68); paint.textSize = height*.12f; paint.textAlign = Paint.Align.CENTER
        canvas.drawText(state,cx,height*.96f,paint)
    }
}
