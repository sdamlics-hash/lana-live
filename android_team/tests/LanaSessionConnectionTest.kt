package ai.dreibus.lanalive
import org.junit.Assert.*
import org.junit.Test
class LanaSessionConnectionTest {
    @Test fun restoresOnlyTheSameEndpointAndHonorsCurrentCredential() {
        assertEquals("saved",LanaSessionConnection.resolve(" https://same ","","https://same" to "saved").second)
        assertEquals("current",LanaSessionConnection.resolve("https://same","current","https://same" to "saved").second)
        for (url in listOf("https://different","https://same.evil","https://same/path",""))
            assertEquals("",LanaSessionConnection.resolve(url,"","https://same" to "saved").second)
        assertEquals("",LanaSessionConnection.resolve("https://same","",null).second)
    }
}
