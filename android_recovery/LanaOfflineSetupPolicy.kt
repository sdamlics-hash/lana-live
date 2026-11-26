package ai.dreibus.lanalive

internal object LanaOfflineSetupPolicy {
    data class Voice(val name:String,val language:String,val network:Boolean,val missing:Boolean)
    fun available(voices:List<Voice>)=voices.filter{it.name.isNotBlank() && it.language=="ru" && !it.network && !it.missing}.distinctBy{it.name}.sortedBy{it.name}
    fun selected(voices: List<Voice>, saved: String?, system: String? = null): String? {
        val eligible = available(voices)
        return eligible.firstOrNull { it.name == saved }?.name
            ?: eligible.firstOrNull { it.name == system }?.name
            ?: eligible.firstOrNull()?.name
    }
    fun russian(tags:List<String>)=tags.any{it.replace('_','-').substringBefore('-').equals("ru",true)}
    enum class Speech { INSTALLED, PENDING, DOWNLOAD, UNSUPPORTED }
    fun speech(installed:List<String>,pending:List<String>,supported:List<String>)=when {
        russian(installed)->Speech.INSTALLED
        russian(pending)->Speech.PENDING
        russian(supported)->Speech.DOWNLOAD
        else->Speech.UNSUPPORTED
    }
    fun updateAllowed(currentPackage:String,newPackage:String,currentVersion:Long,newVersion:Long,oldSigners:Set<String>,newSigners:Set<String>)=
        currentPackage.isNotBlank() && currentPackage==newPackage && newVersion>currentVersion && oldSigners.isNotEmpty() && oldSigners==newSigners
}
