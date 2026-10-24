package ai.dreibus.lanalive

/** Remove decorative emoji from voice only. The original reply stays in history and on screen. */
internal object LanaSpokenText {
    fun clean(text: String): String {
        val out = StringBuilder()
        text.codePoints().forEach { cp ->
            // These characters carry arithmetic meaning, even when displayed as emoji.
            val operator = when (cp) {
                0x2795 -> "+"
                0x2796 -> "−"
                0x2716 -> "×"
                0x2797 -> "÷"
                else -> null
            }
            val decorative = cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF ||
                cp == 0xFE0F || cp == 0xFE0E || cp == 0x200D || cp == 0x20E3
            if (operator != null) out.append(operator)
            else if (!decorative) out.appendCodePoint(cp)
        }
        return out.toString().replace(Regex("[ \\t]+")," ")
            .replace(Regex(" +([.,!?;:])"),"$1").trim()
    }
}
