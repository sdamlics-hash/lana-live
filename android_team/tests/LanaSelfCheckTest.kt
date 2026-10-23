package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test

class LanaSelfCheckTest {
    @Test fun allComponentsPassWithWorkingStorage() {
        val report = LanaSelfCheck.run { it }
        assertEquals(7, report.lines().count { it.startsWith("OK ·") })
        assertFalse(report.contains("ОШИБКА"))
        assertTrue(report.contains("не проверялись"))
    }
    @Test fun brokenStorageIsReportedAndOtherChecksContinue() {
        val report = LanaSelfCheck.run { throw java.io.IOException("synthetic") }
        assertEquals(3, report.lines().count { it.startsWith("ОШИБКА ·") })
        assertEquals(4, report.lines().count { it.startsWith("OK ·") })
        assertFalse(report.contains("synthetic"))
    }
}
