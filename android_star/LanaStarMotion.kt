package ai.dreibus.lanalive

import kotlin.math.*

internal object LanaStarMotion {
    enum class State(val period: Float, val pulse: Float) { IDLE(8f,.018f), LISTEN(3f,.035f), THINK(1.8f,.045f), PREPARE(4f,.02f), SPEAK(1.4f,.07f), STOP(0f,0f), ERROR(0f,0f) }
    fun state(raw: String): State {
        val s=raw.trim().lowercase(java.util.Locale.ROOT)
        return when {
            s.contains("ошиб") || s.contains("недоступ") || s.contains("не удалось") || s.contains("не подключ") || s.contains("не установлен") || s.contains("прервал") -> State.ERROR
            s.contains("остановлен") || s.startsWith("stop") -> State.STOP
            s.startsWith("слушаю") -> State.LISTEN
            s.startsWith("думаю") -> State.THINK
            s.startsWith("готовлю голос") -> State.PREPARE
            s == "отвечаю" -> State.SPEAK
            else -> State.IDLE
        }
    }
    data class Point(val x: Float,val y: Float)
    data class Frame(val center: Point,val radius: Float,val stars: List<Point>,val orbitX: Float,val orbitY: Float)
    fun frame(width: Float,height: Float,elapsed: Float,state: State): Frame {
        require(width.isFinite() && height.isFinite() && width > 0 && height > 0 && elapsed.isFinite())
        val size=min(width,height*.82f)
        val angle=if(state.period>0) ((elapsed%state.period)/state.period*2*PI).toFloat() else 0f
        val center=Point(width/2f,height*.43f + sin(angle)*size*.022f)
        val radius=size*(.225f+sin(angle)*state.pulse)
        val stars=(0 until 9).map { i ->
            val a=angle+(i*2*PI/9).toFloat()
            val spread=size*(.36f + .028f*(i%3))
            Point(center.x+cos(a)*spread,center.y+sin(a)*spread*.70f)
        }
        return Frame(center,radius,stars,size*.36f,size*.21f)
    }
}
