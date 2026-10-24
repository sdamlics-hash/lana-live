package ai.dreibus.lanalive

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.min

/** LANA companion: a native animated leaf muse, not the former cropped fox bitmap.
 * All illustrated features are painted AFTER the glow, fully inside one fitted viewport.
 * Animation represents only the real voice phase.
 */
internal class LanaPresenceView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var state = "Готова"
    private val active get() = state in setOf("Слушаю", "Думаю", "Отвечаю")
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
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
        manageMotion()
        invalidate()
    }
    private fun manageMotion() {
        if (isAttachedToWindow && isShown && active) {
            if (!animator.isStarted) animator.start()
        } else { animator.cancel(); phase = 0f }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); manageMotion() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) manageMotion()
    }

    private fun fill(color: Int) {
        p.reset()
        p.isAntiAlias = true
        p.color = color
        p.style = Paint.Style.FILL
    }
    private fun oval(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        fill(color); c.drawOval(l,t,r,b,p)
    }
    private fun orb(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        fill(color); c.drawCircle(x,y,radius,p)
    }
    private fun shape(c: Canvas, color: Int, draw: (Path) -> Unit) {
        val path = Path()
        draw(path)
        fill(color)
        c.drawPath(path,p)
    }
    private fun leaf(c: Canvas, x: Float, y: Float, angle: Float, color: Int) {
        c.save()
        c.rotate(angle,x,y)
        shape(c,color) {
            it.moveTo(x,y)
            it.cubicTo(x-78f,y-67f,x-34f,y-142f,x+15f,y-158f)
            it.cubicTo(x+85f,y-94f,x+69f,y-27f,x,y)
            it.close()
        }
        fill(Color.argb(160,229,254,179))
        p.style = Paint.Style.STROKE
        p.strokeWidth = 7f
        c.drawLine(x,y,x+15f,y-154f,p)
        c.restore()
    }
    private fun star(c: Canvas, x: Float, y: Float, scale: Float) {
        val gold=Color.rgb(255,208,101)
        orb(c,x,y,scale*10f,gold)
        fill(Color.argb(130,255,220,145))
        p.strokeWidth = 5f*scale
        c.drawLine(x-22f*scale,y,x+22f*scale,y,p)
        c.drawLine(x,y-22f*scale,x,y+22f*scale,p)
    }

    private fun muse(c: Canvas) {
        val darkHair=Color.rgb(152,86,45)
        val gold=Color.rgb(240,174,85)
        val lightHair=Color.rgb(255,218,131)
        val cream=Color.rgb(255,224,185)
        val green=Color.rgb(53,139,89)
        val deepGreen=Color.rgb(28,94,66)

        // Glow is behind the character; never draw an opaque disc over her body.
        fill(Color.WHITE)
        p.shader = RadialGradient(500f,492f,450f,
            intArrayOf(Color.argb(180,213,245,203),Color.argb(75,249,228,176),Color.TRANSPARENT),
            null,Shader.TileMode.CLAMP)
        c.drawCircle(500f,490f,450f,p)
        p.shader = null

        // Hair halo, flowing curls and the visible shoulders.
        oval(c,222f,160f,783f,862f,darkHair)
        oval(c,241f,169f,764f,857f,gold)
        for (i in 0..3) {
            val y=420f+i*106f
            oval(c,208f-i*7f,y,335f,y+204f,if (i%2==0) darkHair else gold)
            oval(c,670f+i*7f,y,794f,y+204f,if (i%2==0) gold else darkHair)
            oval(c,227f,y+18f,297f,y+122f,lightHair)
            oval(c,709f,y+22f,771f,y+128f,lightHair)
        }

        // Dress entirely inside the fitted illustration area.
        shape(c,deepGreen) {
            it.moveTo(360f,757f)
            it.cubicTo(240f,775f,207f,881f,192f,959f)
            it.lineTo(812f,959f)
            it.cubicTo(793f,861f,755f,772f,641f,753f)
            it.close()
        }
        shape(c,green) {
            it.moveTo(377f,774f)
            it.cubicTo(265f,812f,274f,895f,265f,955f)
            it.lineTo(740f,955f)
            it.cubicTo(719f,853f,707f,799f,624f,770f)
            it.close()
        }
        shape(c,Color.rgb(133,191,105)) {
            it.moveTo(404f,793f);it.quadTo(500f,847f,599f,789f)
            it.quadTo(554f,899f,500f,934f);it.quadTo(444f,886f,404f,793f);it.close()
        }
        // Neck and face.
        oval(c,431f,690f,573f,824f,cream)
        oval(c,300f,256f,701f,740f,Color.rgb(198,119,72))
        fill(Color.WHITE)
        p.shader = LinearGradient(309f,330f,688f,720f,
            Color.rgb(255,241,211),Color.rgb(250,193,145),Shader.TileMode.CLAMP)
        c.drawOval(312f,273f,690f,727f,p)
        p.shader=null

        // Natural cheeks and expressive green eyes.
        oval(c,328f,573f,418f,621f,Color.argb(65,225,108,105))
        oval(c,593f,573f,676f,621f,Color.argb(65,225,108,105))
        for (x in arrayOf(402f,600f)) {
            oval(c,x-58f,479f,x+57f,567f,Color.rgb(95,63,47))
            oval(c,x-51f,488f,x+50f,560f,Color.WHITE)
            oval(c,x-33f,491f,x+35f,558f,deepGreen)
            orb(c,x+3f,526f,20f,Color.rgb(18,48,35))
            orb(c,x-15f,505f,13f,Color.WHITE)
            orb(c,x+19f,542f,5f,Color.WHITE)
        }
        // Nose and smile.
        orb(c,502f,588f,7f,Color.rgb(219,138,111))
        fill(Color.rgb(143,63,69))
        p.style=Paint.Style.STROKE
        p.strokeWidth=9f
        p.strokeCap=Paint.Cap.ROUND
        if (state=="Отвечаю") {
            p.style=Paint.Style.FILL
            c.drawOval(459f,621f,547f,658f,p)
            oval(c,477f,640f,531f,659f,Color.rgb(242,127,128))
        } else c.drawArc(457f,606f,546f,662f,8f,164f,false,p)

        // Bright bangs and small front curls: face remains entirely visible.
        shape(c,gold) {
            it.moveTo(277f,441f)
            it.cubicTo(255f,235f,403f,185f,515f,201f)
            it.cubicTo(707f,203f,754f,334f,711f,454f)
            it.cubicTo(655f,392f,638f,350f,608f,340f)
            it.cubicTo(531f,433f,447f,387f,389f,335f)
            it.cubicTo(371f,391f,321f,432f,277f,441f)
            it.close()
        }
        shape(c,lightHair) {
            it.moveTo(486f,204f)
            it.cubicTo(360f,220f,293f,307f,312f,408f)
            it.cubicTo(407f,380f,460f,330f,486f,204f)
            it.close()
        }
        shape(c,Color.rgb(255,207,108)) {
            it.moveTo(506f,207f)
            it.cubicTo(641f,208f,707f,312f,698f,401f)
            it.cubicTo(615f,383f,556f,334f,506f,207f)
            it.close()
        }

        // Living leafy crown instead of fox ears or a robotic propeller.
        leaf(c,427f,253f,-34f,Color.rgb(64,159,80))
        leaf(c,503f,232f,5f,Color.rgb(77,177,89))
        leaf(c,579f,260f,36f,Color.rgb(27,127,73))
        star(c,500f,264f,1.3f)
        star(c,237f,467f,.65f)
        star(c,774f,552f,.7f)
        if (active) star(c,740f,250f,.65f+phase*.35f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width<=0 || height<=0) return
        // Fit a square illustrated frame into 84% of available height and
        // keep a dedicated lower status strip. No clipping or bitmap offsets.
        val size = min(width*.84f,height*.84f)
        val bob = if (active) (phase-.5f)*size*.018f else 0f
        canvas.save()
        canvas.translate((width-size)/2f,(height-size)*.12f+bob)
        canvas.scale(size/1000f,size/1000f)
        muse(canvas)
        canvas.restore()
        fill(Color.rgb(46,100,68))
        p.textAlign=Paint.Align.CENTER
        p.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        p.textSize=min(14f*resources.displayMetrics.scaledDensity,height*.085f)
        canvas.drawText(state.take(32),width/2f,height*.975f,p)
    }
}
