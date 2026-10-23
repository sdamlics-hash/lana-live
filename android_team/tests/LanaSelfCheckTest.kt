package ai.dreibus.lanalive

import org.junit.Assert.*
import org.junit.Test

class LanaSelfCheckTest {
    @Test fun allComponentsPassWithWorkingStorage() {
        val report = LanaSelfCheck.run { it }
        assertEquals(5, report.lines().count { it.startsWith("OK ·") })
        assertFalse(report.contains("ОШИБКА"))
        assertTrue(report.contains("не проверялись"))
    }
    @Test fun brokenStorageIsReportedAndOtherChecksContinue() {
        val report = LanaSelfCheck.run { throw java.io.IOException("synthetic") }
        assertEquals(2, report.lines().count { it.startsWith("ОШИБКА ·") })
        assertEquals(3, report.lines().count { it.startsWith("OK ·") })
        assertFalse(report.contains("synthetic"))
    }
}
