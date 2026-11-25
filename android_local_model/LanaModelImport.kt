package ai.dreibus.lanalive

import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException
import java.security.MessageDigest

/** Stage, verify and commit a pinned local model. Never replace an existing model. */
internal object LanaModelImport {
    const val NAME = "Qwen3-0.6B.litertlm"
    const val SHA256 = "c5f39b4191926786c3e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108"
    const val MAX_BYTES = 750_000_000L
    const val SOURCE = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/e6687177e00897731e7716267eebd4eba0dde7c2/Qwen3-0.6B.litertlm"

    @Synchronized fun install(input: InputStream, directory: File, expected: String = SHA256,
                              limit: Long = MAX_BYTES, cancelled: () -> Boolean = { false }): File {
        require(expected.matches(Regex("[0-9a-f]{64}")) && limit > 0)
        require(directory.isDirectory || directory.mkdirs()) { "Каталог модели недоступен" }
        val target = File(directory, NAME)
        check(!target.exists()) { "Модель уже существует; замена запрещена" }
        val stage = File.createTempFile(".lana-model-", ".part", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            stage.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    if (cancelled()) throw InterruptedIOException("Импорт остановлен")
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n == 0) throw java.io.IOException("Пустой блок данных")
                    if (n.toLong() > limit - count) throw java.io.IOException("Файл превышает допустимый размер")
                    count += n
                    digest.update(buf, 0, n)
                    output.write(buf, 0, n)
                }
                output.fd.sync()
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            check(count > 0 && actual == expected) { "Контрольная сумма модели не совпадает" }
            if (cancelled()) throw InterruptedIOException("Импорт остановлен")
            check(!target.exists()) { "Модель уже существует" }
            check(stage.renameTo(target)) { "Не удалось сохранить модель" }
            return target
        } finally {
            if (stage.exists()) stage.delete()
        }
    }
}
