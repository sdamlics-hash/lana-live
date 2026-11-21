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
        BitmapFactory.decodeResource(resources, R.drawable.lana_approved_1047)
    }
    private val portraitPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private fun drawAndroid(canvas: Canvas, frame: RectF) {
        val image = newPortrait ?: return
        canvas.drawBitmap(image, null, frame, portraitPaint)
    }
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
