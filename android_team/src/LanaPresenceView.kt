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
    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val portrait by lazy { BitmapFactory.decodeResource(resources, R.drawable.lana_avatar_1039) }
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
    /** Small original LANA leaf-orbit emblem. Drawn from vectors at runtime,
     * so its visibility never depends on an external image or network fetch.
     */
    /** Elegant single-leaf brand mark. No rotor motion or false audio animation. */
    private fun drawMark(canvas: Canvas, x: Float, y: Float, r: Float, accent: Int) {
        canvas.save()
        canvas.translate(x,y)
        strokePaint.reset()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.FILL
        strokePaint.color = Color.argb(226, 25, 62, 46)
        canvas.drawCircle(0f,0f,r,strokePaint)
        strokePaint.style = Paint.Style.STROKE
        strokePaint.color = Color.argb(if(animated) (136+phase*95).toInt() else 132, 229, 199, 103)
        strokePaint.strokeWidth = maxOf(1.3f,r*.08f)
        canvas.drawCircle(0f,0f,r*.83f,strokePaint)
        val leaf = Path().apply {
            moveTo(-r*.55f,r*.48f)
            cubicTo(-r*.66f,-r*.26f,-r*.07f,-r*.83f,r*.61f,-r*.70f)
            cubicTo(r*.59f,r*.12f,r*.15f,r*.56f,-r*.55f,r*.48f)
            close()
        }
        strokePaint.style = Paint.Style.FILL
        strokePaint.color = Color.rgb(105,208,107)
        canvas.drawPath(leaf,strokePaint)
        strokePaint.style = Paint.Style.STROKE
        strokePaint.color = Color.rgb(244,220,130)
        strokePaint.strokeWidth = maxOf(1.1f,r*.09f)
        strokePaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(-r*.39f,r*.37f,r*.43f,-r*.51f,strokePaint)
        strokePaint.style = Paint.Style.FILL
        strokePaint.color = if(state=="Отвечаю") Color.rgb(255,229,150) else accent
        canvas.drawCircle(-r*.38f,r*.38f,r*.10f,strokePaint)
        canvas.restore()
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        if(width<=0||height<=0)return
        val image=portrait
        if (image == null) {
            drawMark(canvas,width/2f,height*.44f,min(width,height)*.27f,Color.rgb(77,175,100))
            return
        }
        // Keep the full face and shoulders in the frame; no overlaid opaque circle.
        val targetHeight=min(height*.83f,width*.64f)
        val targetWidth=targetHeight*image.width/image.height
        val dx=(width-targetWidth)/2f
        val dy=maxOf(2f,(height-targetHeight)*.10f)+if(animated)(phase-.5f)*height*.015f else 0f
        val frame=RectF(dx,dy,dx+targetWidth,dy+targetHeight)
        val rounding=targetWidth*.14f

        strokePaint.reset(); strokePaint.isAntiAlias=true
        strokePaint.color=Color.argb(if(animated) (40+20*phase).toInt() else 27,66,157,110)
        strokePaint.style=Paint.Style.FILL
        canvas.drawRoundRect(RectF(frame.left-8f,frame.top-8f,frame.right+8f,frame.bottom+8f),
            rounding+8f,rounding+8f,strokePaint)

        val mask=Path().apply { addRoundRect(frame,rounding,rounding,Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(mask)
        canvas.drawBitmap(image,null,frame,photoPaint)
        canvas.restore()

        val accent=when(state) {
            "Слушаю" -> Color.rgb(50,153,95)
            "Думаю" -> Color.rgb(195,152,76)
            "Отвечаю" -> Color.rgb(195,123,101)
            else -> Color.rgb(117,164,123)
        }
        // The badge lives above the portrait, not on the face. A separate
        // drawable path avoids the masked fox/halo bug seen in 1036-1038.
        val markR = min(targetWidth*.19f,24f*resources.displayMetrics.density)
        drawMark(canvas, frame.right-markR*.28f, frame.top+markR*.52f, markR*.84f, accent)
        strokePaint.color=accent
        strokePaint.style=Paint.Style.STROKE
        strokePaint.strokeWidth=if(animated) 3.2f else 1.7f
        canvas.drawRoundRect(frame,rounding,rounding,strokePaint)
        strokePaint.style=Paint.Style.FILL
        strokePaint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        strokePaint.textAlign=Paint.Align.CENTER
        strokePaint.textSize=min(14f*resources.displayMetrics.scaledDensity,height*.085f)
        strokePaint.color=Color.rgb(57,111,77)
        canvas.drawText(state.take(30),width/2f,height*.975f,strokePaint)
    }
}
