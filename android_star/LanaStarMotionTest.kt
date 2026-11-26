package ai.dreibus.lanalive
import org.junit.Test
import org.junit.Assert.*
class LanaStarMotionTest {
    private val S=LanaStarMotion.State.values()
    private fun state(s:String,t:LanaStarMotion.State)=assertEquals(t,LanaStarMotion.state(s))
    private fun f(w:Float,h:Float,t:Float,s:LanaStarMotion.State)=LanaStarMotion.frame(w,h,t,s)
    @Test fun listen(){state("Слушаю…",S[1])}
    @Test fun think(){state("Думаю…",S[2])}
    @Test fun prepare(){state("Готовлю голос…",S[3])}
    @Test fun speak(){state("Отвечаю",S[4])}
    @Test fun completed(){state("Ответ завершён",S[0])}
    @Test fun stop(){state("Слух остановлен",S[5])}
    @Test fun error(){state("Ошибка системной озвучки",S[6])}
    @Test fun missingVoice(){state("Офлайн-голос русского языка не установлен",S[6])}
    @Test fun textIsNotSpeech(){state("Отвечаю текстом",S[0])}
    @Test fun unavailable(){state("Сервер недоступен",S[6])}
    @Test fun zeroRejected(){assertTrue(runCatching{f(0f,200f,0f,S[0])}.isFailure)}
    @Test fun nanRejected(){assertTrue(runCatching{f(200f,200f,Float.NaN,S[0])}.isFailure)}
    @Test fun nineStars(){assertEquals(9,f(200f,200f,0f,S[0]).stars.size)}
    @Test fun idleMoves(){assertNotEquals(f(200f,200f,0f,S[0]),f(200f,200f,1f,S[0]))}
    @Test fun stopFrozen(){assertEquals(f(200f,200f,0f,S[5]),f(200f,200f,10f,S[5]))}
    @Test fun errorFrozen(){assertEquals(f(200f,200f,0f,S[6]),f(200f,200f,10f,S[6]))}
    @Test fun periodic(){assertEquals(f(200f,200f,0f,S[1]),f(200f,200f,3f,S[1]))}
    @Test fun portraitBounds(){for(s in S) for(i in 0..100){val a=f(220f,900f,i*.17f,s);assertTrue(a.stars.all{it.x in 0f..220f&&it.y in 0f..900f})}}
    @Test fun splitScreenBounds(){for(s in S) for(i in 0..100){val a=f(700f,150f,i*.17f,s);assertTrue(a.stars.all{it.x in 0f..700f&&it.y in 0f..150f});assertTrue(a.center.y-a.radius>=0f&&a.center.y+a.radius<=150f)}}
    @Test fun positiveFiniteRadius(){for(s in S)for(i in 0..200){val a=f(100f,100f,i*.1f,s);assertTrue(a.radius.isFinite()&&a.radius>0f&&a.orbitX>0&&a.orbitY>0)}}
    @Test fun starsGatherAroundPortraitWhileSpeaking() {
        val start = f(200f, 200f, 0f, S[4])
        val gathered = f(200f, 200f, .56f, S[4])
        assertTrue(gathered.orbitX < start.orbitX)
        assertTrue(gathered.orbitY < start.orbitY)
        assertEquals(start.radius, gathered.radius)
        assertTrue(gathered.orbitY > gathered.radius)
    }

    @Test fun surroundingStarsClearPortraitAndViewEdges() {
        for ((width, height) in listOf(200f to 200f, 700f to 150f, 140f to 260f)) {
            for (state in S) for (step in 0..100) {
                val frame = f(width, height, step * .079f, state)
                val margin = maxOf(.5f, frame.radius * .066f)
                for (star in frame.stars) {
                    assertTrue(star.x in margin..(width - margin))
                    assertTrue(star.y in margin..(height - margin))
                    val dx = star.x - frame.center.x
                    val dy = star.y - frame.center.y
                    assertTrue(dx * dx + dy * dy > (frame.radius + margin) * (frame.radius + margin))
                }
            }
        }
    }
}
