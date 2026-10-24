package ai.dreibus.lanalive

/** Explicit local preferences only; never inferred from a model answer or stored transcript. */
internal data class LanaDialogueStyle(
    val concise: Boolean = true, val warm: Boolean = true, val humor: Boolean = true
) {
    fun encode(): String = listOf("1",concise.bit(),warm.bit(),humor.bit()).joinToString("|")
    private fun Boolean.bit() = if (this) "1" else "0"
    fun instruction(): String =
        (if (warm) "Говори тепло и просто. " else "Говори спокойно и прямо. ") +
        (if (concise) "Обычно 1–3 коротких предложения. " else "Объясняй подробно, когда вопрос этого требует. ") +
        (if (humor) "Уместен лёгкий юмор. " else "Без шуток. ")
    companion object {
        fun decode(raw: String): LanaDialogueStyle {
            val parts = raw.split('|')
            require(parts.size == 4 && parts[0] == "1" && parts.drop(1).all { it == "0" || it == "1" })
            return LanaDialogueStyle(parts[1] == "1",parts[2] == "1",parts[3] == "1")
        }
    }
}

internal class LanaDialogueStyleStore(private val read: () -> String?, private val write: (String) -> Boolean) {
    data class Loaded(val style: LanaDialogueStyle, val readable: Boolean)
    fun load(): Loaded = try {
        Loaded(read()?.let { LanaDialogueStyle.decode(it) } ?: LanaDialogueStyle(),true)
    } catch (_: Exception) { Loaded(LanaDialogueStyle(),false) }
    fun save(style: LanaDialogueStyle): Boolean = try { write(style.encode()) } catch (_: Exception) { false }
}
