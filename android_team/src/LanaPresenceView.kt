package ai.dreibus.lanalive

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.min

/** A restrained portrait of adult LANA, not a chibi mascot. Visible in a 190dp view. */
internal class LanaPresenceView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var state = "Готова"
    private val moving get() = state in setOf("Слушаю", "Думаю", "Отвечаю")
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1700L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }
    fun update(value: String) {
        state = when {
            value.startsWith("Слушаю") -> "Слушаю"
            value.startsWith("Думаю") || value.startsWith("Передаю") -> "Думаю"
            value.startsWith("Отвечаю") -> "Отвечаю"
            value.contains("остановлен",true) -> "Остановлена"
            else -> value.take(40)
        }
        contentDescription = "LANA: $state"
        syncMotion(); invalidate()
    }
    private fun syncMotion() {
        if (isAttachedToWindow && isShown && moving) {
            if (!animator.isStarted) animator.start()
        } else { animator.cancel(); phase = 0f }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncMotion() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView,visibility)
        if (isAttachedToWindow) syncMotion()
    }
    private fun color(hex: String) = Color.parseColor(hex)
    private fun fill(col: Int) {
        p.reset()
        p.isAntiAlias = true
        p.color = col
        p.style = Paint.Style.FILL
    }
    private fun oval(c: Canvas,l:Float,t:Float,r:Float,b:Float,col:Int) {
        fill(col);c.drawOval(l,t,r,b,p)
    }
    private fun disk(c: Canvas,x:Float,y:Float,r:Float,col:Int) {
        fill(col);c.drawCircle(x,y,r,p)
    }
    private fun path(c:Canvas,col:Int,build:(Path)->Unit) {
        val shape=Path(); build(shape);fill(col);c.drawPath(shape,p)
    }
    private fun leaf(c:Canvas,x:Float,y:Float,rotation:Float,col:Int) {
        c.save();c.rotate(rotation,x,y)
        path(c,col) {
            it.moveTo(x,y)
            it.cubicTo(x-22f,y-24f,x-14f,y-59f,x+14f,y-72f)
            it.cubicTo(x+47f,y-48f,x+33f,y-17f,x,y)
            it.close()
        }
        fill(color("#BCE7B9"));p.style=Paint.Style.STROKE;p.strokeWidth=2f
        c.drawLine(x,y,x+14f,y-71f,p);c.restore()
    }
    private fun portrait(c:Canvas) {
        val base=color("#C9A36F");val amber=color("#E8C18A")
        val glint=color("#FFE3A7");val shade=color("#795439")
        val fern=color("#286C52");val lightFern=color("#64B082")
        // Halo remains behind the head, never obscuring the face.
        fill(Color.WHITE)
        p.shader=RadialGradient(500f,475f,420f,
            intArrayOf(Color.argb(65,103,204,139),Color.argb(25,251,203,132),Color.TRANSPARENT),
            null,Shader.TileMode.CLAMP)
        c.drawCircle(500f,475f,420f,p);p.shader=null

        // Long swept hair behind neck and shoulders.
        path(c,shade) {
            it.moveTo(311f,339f)
            it.cubicTo(296f,168f,421f,109f,545f,135f)
            it.cubicTo(739f,143f,756f,297f,736f,436f)
            it.cubicTo(733f,618f,788f,739f,826f,893f)
            it.cubicTo(681f,918f,591f,860f,500f,857f)
            it.cubicTo(409f,870f,308f,915f,176f,897f)
            it.cubicTo(228f,727f,289f,552f,311f,339f)
            it.close()
        }
        path(c,base) {
            it.moveTo(328f,294f)
            it.cubicTo(343f,175f,416f,143f,537f,156f)
            it.cubicTo(710f,175f,725f,316f,700f,461f)
            it.cubicTo(702f,694f,759f,814f,784f,885f)
            it.cubicTo(706f,905f,651f,877f,617f,838f)
            it.cubicTo(720f,663f,663f,373f,625f,273f)
            it.cubicTo(493f,196f,397f,286f,365f,478f)
            it.cubicTo(358f,636f,327f,767f,231f,880f)
            it.cubicTo(207f,753f,323f,479f,328f,294f)
            it.close()
        }
        // Adult shoulder silhouette with dark green garment.
        path(c,fern) {
            it.moveTo(418f,747f)
            it.cubicTo(291f,770f,245f,838f,220f,928f)
            it.lineTo(790f,928f)
            it.cubicTo(744f,837f,676f,775f,582f,747f)
            it.close()
        }
        path(c,lightFern) {
            it.moveTo(417f,764f);it.quadTo(502f,836f,590f,762f)
            it.quadTo(532f,891f,504f,916f);it.quadTo(462f,856f,417f,764f)
            it.close()
        }
        oval(c,447f,674f,559f,792f,color("#EDBF98"))
        // Adult face, narrow at jaw rather than spherical baby-face.
        path(c,color("#F0C19C")) {
            it.moveTo(350f,326f)
            it.cubicTo(364f,233f,423f,202f,515f,217f)
            it.cubicTo(642f,222f,674f,328f,659f,463f)
            it.cubicTo(651f,575f,601f,688f,525f,723f)
            it.cubicTo(497f,736f,466f,730f,439f,711f)
            it.cubicTo(372f,656f,336f,546f,342f,431f)
            it.close()
        }
        // Subtle depth/shadow along one cheek.
        fill(Color.WHITE)
        p.shader=LinearGradient(360f,280f,655f,626f,
            Color.argb(120,255,242,210),Color.argb(40,175,77,55),Shader.TileMode.CLAMP)
        c.drawOval(357f,245f,646f,710f,p);p.shader=null
        // Two smaller almond eyes (NOT oversized round cartoon eyes).
        for (x in arrayOf(430f,570f)) {
            path(c,color("#563F32")) {
                it.moveTo(x-40f,443f)
                it.quadTo(x,407f,x+40f,441f)
                it.quadTo(x,465f,x-40f,443f)
                it.close()
            }
            oval(c,x-33f,430f,x+34f,458f,color("#FFF1DE"))
            disk(c,x,444f,17f,color("#467D55"))
            disk(c,x,444f,8f,color("#183F30"))
            disk(c,x-7f,438f,5f,Color.WHITE)
            fill(color("#785443"));p.style=Paint.Style.STROKE;p.strokeWidth=8f;p.strokeCap=Paint.Cap.ROUND
            c.drawArc(x-42f,424f,x+42f,459f,188f,155f,false,p)
            c.drawArc(x-45f,379f,x+45f,412f,195f,145f,false,p)
        }
        // Nose and a light, reserved smile.
        fill(color("#CE927A"));p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND
        p.strokeWidth=5f;c.drawArc(480f,470f,519f,565f,350f,105f,false,p)
        fill(color("#B66F6C"));p.style=Paint.Style.STROKE;p.strokeWidth=7f
        c.drawArc(457f,573f,541f,615f,18f,143f,false,p)
        if (state=="Отвечаю") oval(c,479f,600f,522f,614f,color("#9C5659"))

        // Asymmetric swept fringe with leaf ornament, no antennae.
        path(c,base) {
            it.moveTo(329f,398f)
            it.cubicTo(317f,205f,431f,146f,552f,168f)
            it.cubicTo(651f,176f,712f,261f,697f,382f)
            it.cubicTo(633f,331f,616f,280f,568f,266f)
            it.cubicTo(503f,354f,408f,367f,329f,398f)
            it.close()
        }
        path(c,glint) {
            it.moveTo(364f,356f)
            it.cubicTo(369f,225f,446f,178f,526f,176f)
            it.cubicTo(486f,267f,444f,322f,364f,356f)
            it.close()
        }
        // Hair strands and discrete foliage on the side, not ears.
        fill(glint);p.style=Paint.Style.STROKE;p.strokeWidth=7f
        c.drawArc(294f,298f,431f,817f,60f,91f,false,p)
        c.drawArc(620f,257f,760f,835f,283f,98f,false,p)
        leaf(c,650f,307f,49f,color("#4F9F69"))
        leaf(c,686f,345f,65f,color("#2F7954"))
        disk(c,668f,304f,9f,color("#E9C56B"))

        // Small state indicator near shoulder, changes only on real state.
        if (moving) {
            disk(c,715f,802f,13f+phase*6f,
                if (state=="Слушаю") color("#3FB77B") else if (state=="Думаю") color("#ECB35C") else color("#D98870"))
        }
    }
    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        if (width<=0||height<=0) return
        val size=min(width*.88f,height*.86f)
        val drift=if (moving) (phase-.5f)*size*.014f else 0f
        canvas.save()
        canvas.translate((width-size)/2f,(height-size)*.10f+drift)
        canvas.scale(size/1000f,size/1000f)
        portrait(canvas)
        canvas.restore()
        fill(color("#3B7658"));p.textAlign=Paint.Align.CENTER
        p.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        p.textSize=min(14f*resources.displayMetrics.scaledDensity,height*.08f)
        canvas.drawText(state.take(28),width/2f,height*.97f,p)
    }
}
