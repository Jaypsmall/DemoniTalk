
package com.example.demonitalk

import java.text.Normalizer
import java.util.regex.Pattern

object VoiceTextNormalizer {
    private val diacriticsPattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
    private val whitespacePattern = Regex("\\s+")

    private val demoniAliases = listOf(
        "demonio",
        "de money",
        "de moni",
        "d money",
        "demoni",
        "demonio",
        "demony",
        "demon y",
        "demon",
        "maquina",
        "puta",
        "puto"
    ).sortedByDescending { it.length }

    private val wakeWords = listOf("hermano", "amigo", "maquina")

    data class WakeWordMatch(
        val wakeWord: String,
        val remainingText: String
    )

    fun normalize(text: String): String {
        val nfdNormalizedString = Normalizer.normalize(text, Normalizer.Form.NFD)
        val withoutDiacritics = diacriticsPattern.matcher(nfdNormalizedString).replaceAll("")
        val cleanText = withoutDiacritics
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(whitespacePattern, " ")

        return demoniAliases.fold(cleanText) { current, alias ->
            current.replace(Regex("(?<!\\S)${Regex.escape(alias)}(?!\\S)"), "maquina")
        }.replace(whitespacePattern, " ").trim()
    }

    fun findWakeWord(text: String): WakeWordMatch? {
        val normalizedText = normalize(text)

        val bestMatch = wakeWords.mapNotNull { wakeWord ->
            val match = Regex("(?<!\\S)(${Regex.escape(wakeWord)})(?!\\S)")
                .find(normalizedText)
                ?: return@mapNotNull null

            val groupRange = match.groups[1]?.range ?: return@mapNotNull null
            WakeCandidate(wakeWord, groupRange.first, groupRange.last + 1)
        }.minWithOrNull(compareBy<WakeCandidate> { it.start }.thenByDescending { it.end })

        return bestMatch?.let {
            WakeWordMatch(
                wakeWord = it.word,
                remainingText = normalizedText.substring(it.end).trim()
            )
        }
    }

    private data class WakeCandidate(
        val word: String,
        val start: Int,
        val end: Int
    )
}
