package ai.dreibus.lanalive

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LanaUpdateManifestTest {
    private fun valid() = JSONObject().put("schema", 1).put("package", LanaUpdateManifest.PACKAGE)
        .put("versionCode", 1025).put("apkUrl", "https://github.com/sdamlics-hash/lana-live/releases/download/lana-stable-1025/LANA-STABLE-VOICE.apk")
        .put("sha256", "a".repeat(64)).put("size", 5000000)
    @Test fun parsesVersionSizeAndDigest() {
        val manifest = LanaUpdateManifest.parse(valid().toString())
        assertEquals(1025L, manifest.version); assertEquals(5000000L, manifest.size)
    }
    @Test fun rejectsOtherPackagesSourcesMalformedDigestsAndOversize() {
        for ((key, value) in listOf("package" to "other.app", "schema" to 2, "size" to 0,
            "size" to LanaUpdateManifest.MAX_SIZE + 1, "sha256" to "abc", "versionCode" to 1,
            "apkUrl" to "https://example.com/app.apk", "apkUrl" to "http://github.com/app.apk",
            "apkUrl" to "https://github.com/sdamlics-hash/lana-live/releases/download/lana-stable-1025/LANA-STABLE-VOICE.apk?token=x")) {
            try { LanaUpdateManifest.parse(valid().put(key, value).toString()); fail("Must reject $key") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
