package ai.dreibus.lanalive

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.min

/** Purely local vector star core. State reflects status, not a fabricated audio meter. */
internal class LanaPresenceView(context: Context) : View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var state=LanaStarMotion.State.IDLE
    private var started=SystemClock.elapsedRealtime()
    private val animator=ValueAnimator.ofFloat(0f,1f).apply {
        duration=1000L
        repeatCount=ValueAnimator.INFINITE
        addUpdateListener { invalidate() }
    }
    init { contentDescription="LANA: Готова" }
    fun update(value:String) {
        val next=LanaStarMotion.state(value)
        if(next!=state){state=next;started=SystemClock.elapsedRealtime()}
        contentDescription="LANA: "+value.take(150)
        manageMotion();invalidate()
    }
    private fun manageMotion(){
        if(isAttachedToWindow && isShown && windowVisibility==VISIBLE && state.period>0f){
            if(!animator.isStarted)animator.start()
        }else animator.cancel()
    }
    override fun onAttachedToWindow(){super.onAttachedToWindow();manageMotion()}
    override fun onDetachedFromWindow(){animator.cancel();super.onDetachedFromWindow()}
    override fun onVisibilityChanged(changedView:View,visibility:Int){super.onVisibilityChanged(changedView,visibility);if(isAttachedToWindow)manageMotion()}
    override fun onWindowVisibilityChanged(visibility:Int){super.onWindowVisibilityChanged(visibility);if(isAttachedToWindow)manageMotion()}
    private fun star(c:Canvas,x:Float,y:Float,r:Float,color:Int){
        val p=Path()
        p.moveTo(x,y-r);p.lineTo(x+r*.24f,y-r*.24f);p.lineTo(x+r,y);p.lineTo(x+r*.24f,y+r*.24f)
        p.lineTo(x,y+r);p.lineTo(x-r*.24f,y+r*.24f);p.lineTo(x-r,y);p.lineTo(x-r*.24f,y-r*.24f);p.close()
        paint.shader=null;paint.style=Paint.Style.FILL;paint.color=color;c.drawPath(p,paint)
    }
    override fun onDraw(c:Canvas){
        super.onDraw(c)
        if(width<=0||height<=0)return
        val f=LanaStarMotion.frame(width.toFloat(),height.toFloat(),(SystemClock.elapsedRealtime()-started)/1000f,state)
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
        val alphaAccent=accent and 0x00ffffff
        paint.style=Paint.Style.FILL;paint.color=Color.WHITE
        paint.shader=RadialGradient(x,y,r*1.8f,intArrayOf(alphaAccent or 0x66000000,alphaAccent or 0x22000000,alphaAccent),floatArrayOf(0f,.55f,1f),Shader.TileMode.CLAMP)
        c.drawCircle(x,y,r*1.8f,paint);paint.shader=null
        paint.style=Paint.Style.STROKE;paint.strokeWidth=maxOf(1f,r*.025f);paint.color=alphaAccent or 0x90000000.toInt()
        c.save();c.rotate(-20f,x,y);c.drawOval(x-f.orbitX,y-f.orbitY,x+f.orbitX,y+f.orbitY,paint);c.restore()
        paint.style=Paint.Style.FILL;paint.color=Color.WHITE
        paint.shader=RadialGradient(x-r*.28f,y-r*.35f,r*1.5f,intArrayOf(Color.WHITE,accent,Color.rgb(64,36,112)),floatArrayOf(0f,.36f,1f),Shader.TileMode.CLAMP)
        c.drawCircle(x,y,r,paint);paint.shader=null
        paint.style=Paint.Style.STROKE;paint.strokeWidth=maxOf(1f,r*.025f);paint.color=Color.argb(220,255,255,255)
        c.drawCircle(x,y,r*.96f,paint)
        f.stars.forEachIndexed { i,p -> star(c,p.x,p.y,maxOf(1f,r*(.07f+.035f*(i%3))),if(i%3==0)Color.rgb(255,216,106) else accent) }
        paint.style=Paint.Style.FILL;paint.color=Color.rgb(67,46,92);paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);paint.textAlign=Paint.Align.CENTER
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
