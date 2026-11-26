package ai.dreibus.lanalive

import kotlin.math.*

internal object LanaStarMotion {
    enum class State(val period: Float) { IDLE(8f), LISTEN(3f), THINK(1.8f), PREPARE(4f), SPEAK(1.4f), STOP(0f), ERROR(0f) }
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
    fun frame(width: Float,height: Float,elapsed: Float,state: State, expanded: Boolean = false): Frame {
        require(width.isFinite() && height.isFinite() && width > 0 && height > 0 && elapsed.isFinite())
        val size=min(width,height*.82f)
        val angle=if(state.period>0) ((elapsed%state.period)/state.period*2*PI).toFloat() else 0f
        val center = Point(width / 2f, height * .43f + sin(angle) * size * .012f)
        val radius = size * .31f
        val gathering = when (state) {
            State.PREPARE, State.SPEAK -> ((elapsed / .56f).coerceIn(0f, 1f)).let { it * it * (3f - 2f * it) }
            else -> 0f
        }
        val baseX = if (expanded) min(width * .43f, size * .80f) else size * .438f
        val orbitX = baseX * (1f - .18f * gathering)
        val orbitY = size * (.405f - .039f * gathering)
        val stars = (0 until 9).map { i ->
            val track = i % 3
            val phase = (i * 2 * PI / 9).toFloat()
            val a = phase + if (track == 1) -angle else angle
            val lane = 1f + .024f * track + sin(2f * a + phase) * .018f
            val wobble = if (track == 2) sin(3f * a + phase) * size * .012f else 0f
            Point(center.x + cos(a) * orbitX * lane, center.y + sin(a) * orbitY * lane + wobble)
        }
        return Frame(center, radius, stars, orbitX, orbitY)
    }
}
