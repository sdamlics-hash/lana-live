package ai.dreibus.lanalive

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.json.JSONObject

/** Device-only, bounded and encrypted; never copied into exported notes or backups. */
internal class LanaDialogueStorage(context: Context) {
    private val base = File(context.noBackupFilesDir, "lana-dialogue.enc")
    private val file = AtomicFile(base)
    private val alias = "lana.dialogue.aes.v1"
    private fun hash(scope: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(scope.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun existingKey(): SecretKey? = KeyStore.getInstance("AndroidKeyStore").run {
        load(null); getKey(alias, null) as? SecretKey
    }
    private fun keyForSave(): SecretKey = existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        generateKey()
    }
    fun clear(): Boolean = try {
        file.delete()
        !base.exists() && !File(base.path + ".bak").exists() && !File(base.path + ".new").exists()
    } catch (_: Exception) { false }
    fun read(scope: String): String? {
        val stream = try { file.openRead() } catch (_: java.io.FileNotFoundException) { return null }
        val bytes = stream.use { input ->
            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer); if (n < 0) break
                require(output.size() + n <= 150000); output.write(buffer, 0, n)
            }
            output.toByteArray()
        }
        val key = existingKey() ?: throw IllegalStateException("Dialogue key unavailable")
        val root = JSONObject(String(LanaConnectionCipher.decrypt(bytes, key), Charsets.UTF_8))
        require(root.getInt("version") == 1)
        if (root.getString("scope") != hash(scope)) return null
        return root.getJSONArray("turns").toString()
    }
    fun write(scope: String, raw: String): Boolean {
        var stream: java.io.FileOutputStream? = null
        return try {
            require(raw.length <= 110000)
            val payload = JSONObject().put("version", 1).put("scope", hash(scope)).put("turns", org.json.JSONArray(raw)).toString()
            val bytes = LanaConnectionCipher.encrypt(payload.toByteArray(Charsets.UTF_8), keyForSave())
            require(bytes.size <= 150000)
            val output = file.startWrite(); stream = output
            output.write(bytes); file.finishWrite(output); stream = null
            true
        } catch (_: Exception) { stream?.let { file.failWrite(it) }; false }
    }
}
