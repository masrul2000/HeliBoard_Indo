// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

/** Turns recognized speech into text: spoken Indonesian punctuation ("koma", "titik", ...) and spacing. */
object VoiceTextFormatter {

    /** characters that should not get a space before them */
    const val NO_SPACE_BEFORE = ".,!?:;)"

    // order matters: longer phrases first
    private val phrases = listOf(
        "paragraf baru" to "\n\n",
        "baris baru" to "\n",
        "tanda tanya" to "?",
        "tanda seru" to "!",
        "titik dua" to ":",
        "titik koma" to ";",
        "tutup kurung" to ")",
        "buka kurung" to "(",
        "koma" to ",",
        "titik" to ".",
    )

    private val spokenPunctuation: List<Pair<Regex, String>> = phrases.map { (phrase, symbol) ->
        val body = phrase.replace(" ", "\\s+")
        val pattern = when (symbol) {
            "\n", "\n\n" -> "\\s*\\b$body\\b\\s*"
            "(" -> "\\b$body\\b\\s*"
            // needs some text before, so e.g. "titik kumpul" at the start stays as words
            else -> "(?<=\\S)\\s*\\b$body\\b"
        }
        Regex(pattern, RegexOption.IGNORE_CASE) to symbol
    }

    private val spaceAfterPunctuation = Regex("([.,!?:;)])(?=[\\p{L}\\p{N}(])")
    private val spaceBeforeOpenParen = Regex("(?<=[\\p{L}\\p{N}])\\(")
    private val sentenceStart = Regex("([.!?]\\s+|\\n)(\\p{Ll})")
    private val doubleSpaces = Regex("[ \\t]{2,}")

    fun format(raw: String, spokenPunctuationEnabled: Boolean): String {
        var text = raw.trim()
        if (text.isEmpty()) return text
        if (spokenPunctuationEnabled) {
            // a phrase that is only a punctuation word (said after a pause)
            val normalized = text.lowercase().replace(Regex("\\s+"), " ").trimEnd('.', ',', '!', '?')
            phrases.firstOrNull { it.first == normalized }?.let { return it.second }
            for ((regex, symbol) in spokenPunctuation) {
                text = regex.replace(text) { symbol }
            }
            text = spaceAfterPunctuation.replace(text, "$1 ")
            text = spaceBeforeOpenParen.replace(text, " (")
            text = sentenceStart.replace(text) { it.groupValues[1] + it.groupValues[2].uppercase() }
        }
        text = doubleSpaces.replace(text, " ")
        // trim spaces but keep intentional new lines
        return text.trim(' ', '\t')
    }
}
