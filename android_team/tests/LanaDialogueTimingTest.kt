package ai.dreibus.lanalive
import org.junit.Assert.*
import org.junit.Test

class LanaDialogueTimingTest {
    @Test fun separatesLocalQueueFromRequestIncludingNetworkAndServer() {
        assertEquals("dialogue queue_ms=125 request_ms=5800 local_pairs=3 result=ok",
            LanaDialogueTiming(100,225,6025,3,true).report())
    }
    @Test fun errorAndEmptyHistoryAreNotReportedAsSuccessfulMemory() {
        assertEquals("dialogue queue_ms=0 request_ms=32000 local_pairs=0 result=error",
            LanaDialogueTiming(10,10,32010,0,false).report())
    }
    @Test fun rejectsNonMonotonicTimesAndInvalidContextCounts() {
        for (args in listOf(listOf(-1L,0L,0L),listOf(10L,9L,11L),listOf(10L,11L,9L))) {
            try { LanaDialogueTiming(args[0],args[1],args[2],0,true); fail() } catch (_: IllegalArgumentException) {}
        }
        try { LanaDialogueTiming(0,0,0,9,true); fail() } catch (_: IllegalArgumentException) {}
    }
}
