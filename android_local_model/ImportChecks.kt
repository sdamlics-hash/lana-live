package ai.dreibus.lanalive
import java.io.*
import java.nio.file.Files
import java.security.MessageDigest

fun main() {
    val data = "model test fixture".toByteArray()
    fun hash(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it.toInt() and 255) }
    val expected = hash(data)
    fun rejected(action: () -> Unit) { check(runCatching(action).isFailure) }
    fun clean(dir: File) { check(dir.listFiles().orEmpty().none { it.name.endsWith(".part") }); check(!File(dir,LanaModelImport.NAME).exists()) }
    val tests = listOf<Pair<String,(File)->Unit>>(
        "valid fixture" to { d -> check(LanaModelImport.install(data.inputStream(),d,expected).readBytes().contentEquals(data)) },
        "corruption" to { d -> rejected { LanaModelImport.install("bad".byteInputStream(),d,expected) }; clean(d) },
        "truncated" to { d -> rejected { LanaModelImport.install(data.copyOf(5).inputStream(),d,expected) }; clean(d) },
        "too large" to { d -> rejected { LanaModelImport.install(data.inputStream(),d,expected,5) }; clean(d) },
        "empty" to { d -> rejected { LanaModelImport.install(byteArrayOf().inputStream(),d,hash(byteArrayOf())) }; clean(d) },
        "cancel" to { d -> rejected { LanaModelImport.install(data.inputStream(),d,expected,cancelled={true}) }; clean(d) },
        "no overwrite" to { d -> val f=LanaModelImport.install(data.inputStream(),d,expected); rejected { LanaModelImport.install("other".byteInputStream(),d,hash("other".toByteArray())) }; check(f.readBytes().contentEquals(data)) },
        "read error" to { d -> rejected { LanaModelImport.install(object:InputStream(){ override fun read():Int=throw IOException("fixture") },d,expected) }; clean(d) },
        "zero progress stream" to { d -> rejected { LanaModelImport.install(object:InputStream(){ override fun read():Int=0; override fun read(b:ByteArray,o:Int,l:Int)=0 },d,expected) }; clean(d) },
        "retry after failure" to { d -> rejected { LanaModelImport.install("bad".byteInputStream(),d,expected) }; clean(d); check(LanaModelImport.install(data.inputStream(),d,expected).readBytes().contentEquals(data)) }
    )
    tests.forEachIndexed { i,(name,action) -> val dir=Files.createTempDirectory("lana-import-check").toFile(); try { action(dir); println("PASS ${i+1}: $name") } finally { dir.deleteRecursively() } }
}
