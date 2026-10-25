package ai.dreibus.lanalive

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Encrypted, device-only memory: independent from legacy notes and ChatGPT history. */
internal class LanaProjectMemoryStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir,"lana-project-memory-v2.enc"))
    private val alias = "lana.project.memory.v2"
    private fun existingKey(): SecretKey? = KeyStore.getInstance("AndroidKeyStore").run {
        load(null); getKey(alias,null) as? SecretKey
    }
    private fun createKey(): SecretKey = existingKey() ?: KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    fun read(): String {
        val encrypted=try { file.openRead().use { input ->
            val result=java.io.ByteArrayOutputStream()
            val buf=ByteArray(8192)
            while(true) { val n=input.read(buf); if(n<0)break
                require(result.size()+n<=LanaProjectMemory.MAX_BYTES+128)
                result.write(buf,0,n)
            }
            result.toByteArray()
        } } catch (_: java.io.FileNotFoundException) { return LanaProjectMemory.EMPTY }
        val key=existingKey() ?: throw IllegalStateException("Project memory key unavailable")
        return LanaProjectMemory.validate(String(LanaConnectionCipher.decrypt(encrypted,key),Charsets.UTF_8))
    }
    fun write(raw:String):Boolean {
        var output:java.io.FileOutputStream?=null
        return try {
            val clear=LanaProjectMemory.validate(raw).toByteArray(Charsets.UTF_8)
            val data=LanaConnectionCipher.encrypt(clear,createKey())
            require(data.size<=LanaProjectMemory.MAX_BYTES+128)
            output=file.startWrite()
            output.write(data)
            file.finishWrite(output)
            output=null
            true
        } catch (_:Exception) { output?.let { file.failWrite(it) }; false }
    }
}
/** Single-process serialization prevents Activity/Service read-modify-write collisions. */
internal object LanaProjectMemoryGateway {
    @Synchronized fun handle(context:Context, words:String):String? {
        if(!LanaProjectMemory.matches(words)) return null
        return try {
            val store=LanaProjectMemoryStore(context.applicationContext)
            LanaProjectMemory(store.read(),store::write).handle(words)
        } catch (_:Exception) { "Локальная память проектов недоступна. Ничего не изменено." }
    }
    @Synchronized fun snapshot(context:Context):String = LanaProjectMemoryStore(context.applicationContext).read()
    @Synchronized fun restore(context:Context, raw:String):Boolean {
        val validated=LanaProjectMemory.validate(raw)
        return LanaProjectMemoryStore(context.applicationContext).write(validated)
    }
}
