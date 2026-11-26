package ai.dreibus.lanalive
import org.junit.Test
import org.junit.Assert.*
class LanaOfflineSetupPolicyTest {
    private val p=LanaOfflineSetupPolicy
    private val anna=pVoice("Anna")
    private val male=pVoice("Aleksandr")
    private fun pVoice(n:String,l:String="ru",net:Boolean=false,missing:Boolean=false)=LanaOfflineSetupPolicy.Voice(n,l,net,missing)
    @Test fun usesInstalledRussianVoiceWithoutSetup(){assertEquals("Aleksandr",p.selected(listOf(male,anna),null))}
    @Test fun prefersEligibleSystemDefault(){assertEquals("Anna",p.selected(listOf(male,anna),null,"Anna"))}
    @Test fun skipsIneligibleSystemDefault(){assertEquals("Anna",p.selected(listOf(male.copy(network=true),anna),null,"Aleksandr"))}
    @Test fun savedVoiceWinsOverSystemDefault(){assertEquals("Anna",p.selected(listOf(male,anna),"Anna","Aleksandr"))}
    @Test fun blank(){assertEquals("Anna",p.selected(listOf(anna),""))}
    @Test fun explicit(){assertEquals("Anna",p.selected(listOf(male,anna),"Anna"))}
    @Test fun missing(){assertNull(p.selected(listOf(anna.copy(missing=true)),"Anna"))}
    @Test fun network(){assertNull(p.selected(listOf(anna.copy(network=true)),"Anna"))}
    @Test fun wrongLanguage(){assertNull(p.selected(listOf(anna.copy(language="en")),"Anna"))}
    @Test fun usesEligibleFallbackWhenSavedVoiceDisappears(){assertEquals("Aleksandr",p.selected(listOf(male),"Anna"))}
    @Test fun duplicate(){assertEquals(1,p.available(listOf(anna,anna)).size)}
    @Test fun sorting(){assertEquals(listOf("Aleksandr","Anna"),p.available(listOf(anna,male)).map{it.name})}
    @Test fun emptyNames(){assertTrue(p.available(listOf(pVoice(""))).isEmpty())}
    @Test fun installed(){assertEquals(p.speech(listOf("ru-RU"),emptyList(),emptyList()),LanaOfflineSetupPolicy.Speech.INSTALLED)}
    @Test fun pending(){assertEquals(p.speech(emptyList(),listOf("ru_RU"),listOf("ru")),LanaOfflineSetupPolicy.Speech.PENDING)}
    @Test fun downloadable(){assertEquals(p.speech(emptyList(),emptyList(),listOf("RU-ru")),LanaOfflineSetupPolicy.Speech.DOWNLOAD)}
    @Test fun unsupported(){assertEquals(p.speech(listOf("en-US"),emptyList(),emptyList()),LanaOfflineSetupPolicy.Speech.UNSUPPORTED)}
    @Test fun installedWins(){assertEquals(p.speech(listOf("ru"),listOf("ru"),listOf("ru")),LanaOfflineSetupPolicy.Speech.INSTALLED)}
    @Test fun validUpdate(){assertTrue(p.updateAllowed("lana","lana",1049,1051,setOf("a"),setOf("a")))}
    @Test fun wrongPackage(){assertFalse(p.updateAllowed("lana","other",1049,1051,setOf("a"),setOf("a")))}
    @Test fun downgrade(){assertFalse(p.updateAllowed("lana","lana",1051,1049,setOf("a"),setOf("a")))}
    @Test fun sameVersion(){assertFalse(p.updateAllowed("lana","lana",1049,1049,setOf("a"),setOf("a")))}
    @Test fun signerMismatch(){assertFalse(p.updateAllowed("lana","lana",1049,1051,setOf("a"),setOf("b")));assertFalse(p.updateAllowed("lana","lana",1049,1051,emptySet(),emptySet()))}
}
