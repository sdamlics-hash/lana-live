package ai.dreibus.lanalive

import org.json.JSONObject

/** User-requested portable copy contains only project facts; never keys or endpoints. */
internal object LanaProjectMemoryBackup {
    const val FORMAT="LANA_PROJECT_MEMORY"
    fun export(raw:String, timestamp:Long=System.currentTimeMillis()):ByteArray {
        val valid=LanaProjectMemory.validate(raw)
        return JSONObject().put("format",FORMAT).put("version",2)
            .put("exportedAt",timestamp).put("records",JSONObject(valid))
            .toString().toByteArray(Charsets.UTF_8)
    }
    fun import(bytes:ByteArray):String {
        require(bytes.size<=LanaProjectMemory.MAX_BYTES+4096)
        val root=JSONObject(String(bytes,Charsets.UTF_8))
        require(root.keys().asSequence().toSet()==setOf("format","version","exportedAt","records"))
        require(root.getString("format")==FORMAT && root.getInt("version")==2)
        require(root.getLong("exportedAt")>0)
        return LanaProjectMemory.validate(root.getJSONObject("records").toString())
    }
}
