package ai.dreibus.lanalive

import org.json.JSONObject
import java.net.URI

internal data class LanaUpdateManifest(val version: Long, val url: String, val sha256: String, val size: Long) {
    companion object {
        const val PACKAGE = "ai.dreibus.lanalive.stablevoice"
        const val MAX_SIZE = 100L * 1024 * 1024
        fun parse(raw: String): LanaUpdateManifest {
            require(raw.length <= 16000)
            val data = JSONObject(raw)
            require(data.getInt("schema") == 1 && data.getString("package") == PACKAGE)
            val version = data.getLong("versionCode")
            val url = data.getString("apkUrl")
            val uri = URI(url)
            require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.query == null && uri.fragment == null)
            require(uri.path.matches(Regex("/sdamlics-hash/lana-live/releases/download/lana-stable-[0-9]+/LANA-STABLE-VOICE\\.apk")))
            require(uri.path.contains("/lana-stable-$version/"))
            val digest = data.getString("sha256").lowercase(java.util.Locale.ROOT)
            val size = data.getLong("size")
            require(version > 0 && digest.matches(Regex("[a-f0-9]{64}")) && size in 1..MAX_SIZE)
            return LanaUpdateManifest(version, url, digest, size)
        }
    }
}
