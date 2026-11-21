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
    private fun drawAndroid(canvas: Canvas, frame: RectF) {
        canvas.save()
        canvas.translate(frame.left, frame.top)
        canvas.scale(frame.width()/400f, frame.height()/540f)
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        fun fill(color:Int) { p.reset(); p.isAntiAlias=true; p.color=color; p.style=Paint.Style.FILL }
        fun round(l:Float,t:Float,r:Float,b:Float,rad:Float,c:Int) {
            fill(c); canvas.drawRoundRect(l,t,r,b,rad,rad,p)
        }
        val navy=Color.rgb(14,26,43)
        val steel=Color.rgb(69,101,125)
        val silver=Color.rgb(178,197,207)
        val cyan=Color.rgb(98,227,238)
        val glow=when(state) {
            "Слушаю" -> Color.rgb(83,221,164)
            "Думаю" -> Color.rgb(240,190,91)
            "Отвечаю" -> Color.rgb(246,139,174)
            else -> cyan
        }
        fill(Color.rgb(8,16,30)); canvas.drawRect(0f,0f,400f,540f,p)
        p.shader=RadialGradient(200f,215f,270f,intArrayOf(Color.rgb(38,77,100),Color.rgb(8,16,30)),null,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,400f,540f,p);p.shader=null
        fill(Color.argb(80,100,211,235));p.style=Paint.Style.STROKE;p.strokeWidth=2f
        canvas.drawCircle(200f,230f,172f,p);canvas.drawCircle(200f,230f,155f,p)
        // Shoulder armor and illuminated sternum
        val shoulders=Path().apply {
            moveTo(24f,540f);lineTo(34f,460f);cubicTo(43f,414f,91f,398f,145f,391f)
            lineTo(255f,391f);cubicTo(309f,398f,357f,414f,366f,460f)
            lineTo(376f,540f);close()
        }
        fill(navy);canvas.drawPath(shoulders,p)
        fill(steel);canvas.drawRoundRect(135f,365f,265f,455f,32f,32f,p)
        round(158f,377f,242f,449f,20f,navy)
        fill(glow);canvas.drawCircle(200f,417f,16f,p)
        fill(Color.rgb(18,38,58));canvas.drawOval(45f,420f,154f,550f,p)
        canvas.drawOval(246f,420f,355f,550f,p)
        fill(silver);p.style=Paint.Style.STROKE;p.strokeWidth=5f
        canvas.drawArc(52f,427f,155f,550f,188f,115f,false,p)
        canvas.drawArc(245f,427f,348f,550f,237f,115f,false,p)
        // Side head housings and hair-like cybernetic panels
        fill(Color.rgb(23,45,66))
        canvas.drawOval(79f,64f,321f,410f,p)
        val left=Path().apply {
            moveTo(132f,72f);cubicTo(42f,99f,48f,302f,74f,405f)
            lineTo(112f,378f);lineTo(129f,179f);close()
        }
        fill(steel);canvas.drawPath(left,p)
        val right=Path().apply {
            moveTo(268f,72f);cubicTo(358f,99f,352f,302f,326f,405f)
            lineTo(288f,378f);lineTo(271f,179f);close()
        }
        canvas.drawPath(right,p)
        round(77f,202f,112f,296f,15f,Color.rgb(39,72,93))
        round(288f,202f,323f,296f,15f,Color.rgb(39,72,93))
        // Feminine tapered faceplate
        val face=Path().apply {
            moveTo(145f,98f);cubicTo(177f,74f,223f,74f,255f,98f)
            cubicTo(295f,127f,299f,209f,289f,290f)
            cubicTo(281f,347f,235f,384f,200f,390f)
            cubicTo(165f,384f,119f,347f,111f,290f)
            cubicTo(101f,209f,105f,127f,145f,98f);close()
        }
        fill(silver);canvas.drawPath(face,p)
        val inner=Path().apply {
            moveTo(149f,116f);cubicTo(180f,94f,220f,94f,251f,116f)
            cubicTo(275f,149f,281f,214f,271f,283f)
            cubicTo(265f,333f,231f,364f,200f,371f)
            cubicTo(169f,364f,135f,333f,129f,283f)
            cubicTo(119f,214f,125f,149f,149f,116f);close()
        }
        fill(Color.rgb(123,155,174));canvas.drawPath(inner,p)
        fill(Color.rgb(195,215,220));canvas.drawOval(136f,129f,264f,329f,p)
        // Crown and eyebrows
        round(138f,109f,262f,150f,18f,Color.rgb(38,67,88))
        fill(glow);canvas.drawRoundRect(173f,124f,227f,130f,3f,3f,p)
        round(135f,207f,191f,220f,6f,navy)
        round(209f,207f,265f,220f,6f,navy)
        // Animated eyes, with active-state glow
        val eyeH=if(state=="Думаю") 8f+phase*5f else 15f
        round(145f,229f-eyeH/2f,187f,229f+eyeH/2f,8f,glow)
        round(213f,229f-eyeH/2f,255f,229f+eyeH/2f,8f,glow)
        fill(Color.rgb(19,45,60));canvas.drawCircle(166f,229f,5f,p)
        canvas.drawCircle(234f,229f,5f,p)
        // Nose and expression panel
        fill(steel);canvas.drawRoundRect(194f,253f,206f,296f,5f,5f,p)
        round(160f,311f,240f,341f,13f,Color.rgb(35,57,76))
        val mouthHeight=if(state=="Отвечаю") 3f+phase*12f else 3f
        round(176f,325f-mouthHeight/2f,224f,325f+mouthHeight/2f,4f,glow)
        // Cheek and temple circuitry
        fill(glow);p.style=Paint.Style.STROKE;p.strokeWidth=2.5f
        canvas.drawLine(124f,267f,145f,284f,p);canvas.drawLine(255f,284f,276f,267f,p)
        canvas.drawLine(107f,157f,127f,177f,p);canvas.drawLine(273f,177f,293f,157f,p)
        fill(glow);canvas.drawCircle(145f,284f,3.5f,p);canvas.drawCircle(255f,284f,3.5f,p)
        canvas.restore()
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
        val targetHeight=min(height*.83f,width*.72f)
        val targetWidth=targetHeight*400f/540f
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
