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
        val r = minOf(width, height) * .32f
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
        // Open almond eyes, distinct brows and a small relaxed smile.
        paint.strokeWidth = r*.025f; paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(44,69,53)
        for (side in listOf(-1f,1f)) {
            val ex = cx + side*r*.245f
            val eye = android.graphics.Path().apply {
                moveTo(ex-r*.12f,cy-r*.015f)
                quadTo(ex,cy-r*.14f,ex+r*.12f,cy-r*.015f)
                quadTo(ex,cy+r*.075f,ex-r*.12f,cy-r*.015f)
            }
            paint.style = Paint.Style.FILL; paint.color = Color.rgb(255,252,243)
            canvas.drawPath(eye,paint)
            paint.color = Color.rgb(43,117,85); canvas.drawCircle(ex,cy-r*.025f,r*.055f,paint)
            paint.color = Color.rgb(29,47,39); canvas.drawCircle(ex,cy-r*.025f,r*.027f,paint)
            paint.style = Paint.Style.STROKE; canvas.drawPath(eye,paint)
            canvas.drawArc(ex-r*.13f,cy-r*.23f,ex+r*.13f,cy-r*.1f,205f,130f,false,paint)
        }
        paint.color = Color.rgb(207,163,136); paint.strokeWidth = r*.018f
        val nose = android.graphics.Path().apply {
            moveTo(cx+r*.015f,cy+r*.045f); quadTo(cx-r*.045f,cy+r*.16f,cx+r*.04f,cy+r*.17f)
        }; canvas.drawPath(nose,paint)
        paint.color = Color.rgb(169,78,82); paint.strokeWidth = r*.03f
        if (state == "Отвечаю") {
            paint.style = Paint.Style.FILL
            canvas.drawOval(cx-r*.095f,cy+r*.29f,cx+r*.095f,cy+r*(.33f+phase*.1f),paint)
        } else {
            canvas.drawArc(cx-r*.13f,cy+r*.24f,cx+r*.13f,cy+r*.35f,10f,160f,false,paint)
        }
        // Asymmetric leaf ornament above one temple, rather than a head-top tuft.
        paint.style = Paint.Style.FILL
        for (i in 0..2) {
            paint.color = if (i == 1) Color.rgb(148,204,83) else Color.rgb(80,162,77)
            val lx=cx+r*(.44f+i*.12f); val ly=cy-r*(.8f+i*.1f)
            canvas.save(); canvas.rotate(35f+i*24f,lx,ly)
            canvas.drawOval(lx-r*.075f,ly-r*.22f,lx+r*.075f,ly+r*.09f,paint); canvas.restore()
        }
        paint.color = Color.rgb(42,111,68); paint.textSize = height*.12f; paint.textAlign = Paint.Align.CENTER
        canvas.drawText(state,cx,height*.96f,paint)
    }
}
