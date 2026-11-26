package ai.dreibus.lanalive

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.SystemClock
import android.view.View
import kotlin.math.min

/** Local portrait and orbit animation, driven by the conversation status. */
internal class LanaPresenceView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val starPath = Path()
    private val portrait = BitmapFactory.decodeResource(resources, R.drawable.lana_approved_1047)
    private val portraitShader = portrait?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    private val portraitMatrix = Matrix()
    private val labelTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private var lastFrameAt = 0L
    private var state=LanaStarMotion.State.IDLE
    private var started=SystemClock.elapsedRealtime()
    private val animator=ValueAnimator.ofFloat(0f,1f).apply {
        duration=1000L
        repeatCount=ValueAnimator.INFINITE
        addUpdateListener {
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameAt >= 33L) {
                lastFrameAt = now
                invalidate()
            }
        }
    }
    init { contentDescription="LANA: Готова" }
    fun update(value:String) {
        val next=LanaStarMotion.state(value)
        if(next!=state){state=next;started=SystemClock.elapsedRealtime()}
        contentDescription="LANA: "+value.take(150)
        manageMotion();invalidate()
    }
    private fun manageMotion(){
        val enabled = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
        if (enabled && isAttachedToWindow && isShown && windowVisibility == VISIBLE && state.period > 0f) {
            if(!animator.isStarted)animator.start()
        }else animator.cancel()
    }
    override fun onAttachedToWindow(){super.onAttachedToWindow();manageMotion()}
    override fun onDetachedFromWindow(){animator.cancel();super.onDetachedFromWindow()}
    override fun onVisibilityChanged(changedView:View,visibility:Int){super.onVisibilityChanged(changedView,visibility);if(isAttachedToWindow)manageMotion()}
    override fun onWindowVisibilityChanged(visibility:Int){super.onWindowVisibilityChanged(visibility);if(isAttachedToWindow)manageMotion()}
    private fun star(c:Canvas,x:Float,y:Float,r:Float,color:Int){
        val p = starPath
        p.rewind()
        p.moveTo(x,y-r);p.lineTo(x+r*.24f,y-r*.24f);p.lineTo(x+r,y);p.lineTo(x+r*.24f,y+r*.24f)
        p.lineTo(x,y+r);p.lineTo(x-r*.24f,y+r*.24f);p.lineTo(x-r,y);p.lineTo(x-r*.24f,y-r*.24f);p.close()
        paint.shader=null;paint.style=Paint.Style.FILL;paint.color=color;c.drawPath(p,paint)
    }
    override fun onDraw(c:Canvas){
        super.onDraw(c)
        if(width<=0||height<=0)return
        val f=LanaStarMotion.frame(width.toFloat(),height.toFloat(),if (animator.isStarted) (SystemClock.elapsedRealtime()-started)/1000f else 0f,state)
        val x=f.center.x;val y=f.center.y;val r=f.radius
        val accent=when(state){
            LanaStarMotion.State.LISTEN -> Color.rgb(97,220,155)
            LanaStarMotion.State.THINK -> Color.rgb(255,211,114)
            LanaStarMotion.State.PREPARE -> Color.rgb(220,189,255)
            LanaStarMotion.State.SPEAK -> Color.rgb(255,154,206)
            LanaStarMotion.State.ERROR -> Color.rgb(230,111,100)
            LanaStarMotion.State.STOP -> Color.rgb(130,139,151)
            else -> Color.rgb(178,155,255)
        }
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = accent and 0x00ffffff or 0x22000000
        c.drawCircle(x, y, r * 1.12f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1f, r * .015f)
        paint.color = accent and 0x00ffffff or 0x77000000
        c.drawOval(x-f.orbitX,y-f.orbitY,x+f.orbitX,y+f.orbitY,paint)

        val bitmap = portrait
        val shader = portraitShader
        if (bitmap != null && shader != null) {
            val scale = 2f * r / min(bitmap.width, bitmap.height)
            portraitMatrix.setScale(scale, scale)
            portraitMatrix.postTranslate(x - bitmap.width * scale / 2f, y - bitmap.height * scale / 2f)
            shader.setLocalMatrix(portraitMatrix)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.shader = shader
            c.drawCircle(x, y, r, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = maxOf(1f, r * .027f)
            paint.color = accent
            c.drawCircle(x, y, r, paint)
        }
        f.stars.forEachIndexed { i,p ->
            val color = if (i % 3 == 0) Color.rgb(221, 144, 43) else accent
            star(c, p.x, p.y, maxOf(.5f, r * (.038f + .014f * (i % 3))), color)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(67,46,92)
        paint.typeface = labelTypeface
        paint.textAlign = Paint.Align.CENTER
        paint.textSize=min(13f*resources.displayMetrics.scaledDensity,height*.09f)
        val label=when(state){
            LanaStarMotion.State.IDLE -> "Готова"
            LanaStarMotion.State.LISTEN -> "Слушаю"
            LanaStarMotion.State.THINK -> "Думаю"
            LanaStarMotion.State.PREPARE -> "Готовлю голос"
            LanaStarMotion.State.SPEAK -> "Отвечаю"
            LanaStarMotion.State.STOP -> "Остановлена"
            LanaStarMotion.State.ERROR -> "Нужна проверка"
        }
        c.drawText(label,width/2f,height*.94f,paint)
    }
}
