package ai.dreibus.lanalive

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.min

/** Full portrait rendering. The illustration is a bundled local WebP resource,
 * never downloaded at runtime. Voice-state animation is only visual feedback.
 */
internal class LanaPresenceView(context: Context) : View(context) {
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var state = "Готова"
    private val animated get() = state in setOf("Слушаю", "Думаю", "Отвечаю")
    private val animator = ValueAnimator.ofFloat(0f,1f).apply {
        duration=1700L
        repeatCount=ValueAnimator.INFINITE
        repeatMode=ValueAnimator.REVERSE
        addUpdateListener { phase=it.animatedValue as Float; invalidate() }
    }
    fun update(value:String) {
        state = when {
            value.startsWith("Слушаю") -> "Слушаю"
            value.startsWith("Думаю") || value.startsWith("Передаю") -> "Думаю"
            value.startsWith("Отвечаю") -> "Отвечаю"
            value.contains("остановлен",true) -> "Остановлена"
            else -> value.take(40)
        }
        contentDescription="LANA: $state"
        manageMotion(); invalidate()
    }
    private fun manageMotion() {
        if (isAttachedToWindow && isShown && animated) {
            if (!animator.isStarted) animator.start()
        } else { animator.cancel(); phase=0f }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); manageMotion() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView:View,visibility:Int) {
        super.onVisibilityChanged(changedView,visibility)
        if (isAttachedToWindow) manageMotion()
    }
    /** Local vector android face; never loads an external image. */
    private val newPortrait by lazy {
        val bytes = android.util.Base64.decode(PORTRAIT_WEBP, android.util.Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    private val portraitPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private fun drawAndroid(canvas: Canvas, frame: RectF) {
        val image = newPortrait ?: return
        canvas.drawBitmap(image, null, frame, portraitPaint)
    }
    private val PORTRAIT_WEBP = "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"
    /** Voice-phase feedback outside the portrait. Animation is cosmetic and
     * driven only by the current state, never a fabricated microphone meter.
     */
    private fun drawVoiceActivity(canvas: Canvas, centerX: Float, centerY: Float, unit: Float) {
        if (!animated) return
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.strokeCap = Paint.Cap.ROUND
        val green = Color.rgb(48,154,91)
        val gold = Color.rgb(185,138,58)
        when(state) {
            "Слушаю" -> {
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = maxOf(1.5f,unit*.16f)
                strokePaint.color = Color.argb((110+100*phase).toInt(),48,154,91)
                canvas.drawCircle(centerX,centerY,unit*(.45f+.36f*phase),strokePaint)
                strokePaint.style = Paint.Style.FILL
                strokePaint.color = green
                canvas.drawCircle(centerX,centerY,unit*.26f,strokePaint)
            }
            "Думаю" -> {
                strokePaint.style = Paint.Style.FILL
                for(i in 0..2) {
                    strokePaint.color = if(i == (phase*3f).toInt().coerceIn(0,2)) gold else Color.argb(90,185,138,58)
                    canvas.drawCircle(centerX+(i-1)*unit*.82f,centerY,unit*.23f,strokePaint)
                }
            }
            "Отвечаю" -> {
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = maxOf(1.6f,unit*.2f)
                strokePaint.color = Color.rgb(175,101,77)
                for(i in 0..4) {
                    val amplitude = if(i%2==0) .38f else .75f
                    val half = unit*(amplitude+.20f*phase)
                    val x=centerX+(i-2)*unit*.52f
                    canvas.drawLine(x,centerY-half,x,centerY+half,strokePaint)
                }
            }
        }
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        if(width<=0||height<=0)return
        val targetHeight=min(height*.90f,width*.90f)
        val targetWidth=targetHeight
        val dx=(width-targetWidth)/2f
        val dy=maxOf(2f,(height-targetHeight)*.10f)+if(animated)(phase-.5f)*height*.015f else 0f
        val frame=RectF(dx,dy,dx+targetWidth,dy+targetHeight)
        val rounding=targetWidth*.14f
        drawAndroid(canvas,frame)
        val accent=when(state) {
            "Слушаю" -> Color.rgb(50,153,95)
            "Думаю" -> Color.rgb(195,152,76)
            "Отвечаю" -> Color.rgb(195,123,101)
            else -> Color.rgb(117,164,123)
        }
        // The badge lives above the portrait, not on the face. A separate
        // drawable path avoids the masked fox/halo bug seen in 1036-1038.
        // No corner leaf badge: keep the portrait unobstructed.
        strokePaint.color=accent
        strokePaint.style=Paint.Style.STROKE
        strokePaint.strokeWidth=if(animated) 3.2f else 1.7f
        canvas.drawRoundRect(frame,rounding,rounding,strokePaint)
        val feedbackY = min(frame.bottom + height*.045f, height*.91f)
        drawVoiceActivity(canvas,width/2f,feedbackY,min(height*.026f,16f*resources.displayMetrics.density))
        strokePaint.style=Paint.Style.FILL
        strokePaint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        strokePaint.textAlign=Paint.Align.CENTER
        strokePaint.textSize=min(14f*resources.displayMetrics.scaledDensity,height*.085f)
        strokePaint.color=Color.rgb(57,111,77)
        canvas.drawText(state.take(30),width/2f,height*.975f,strokePaint)
    }
}
