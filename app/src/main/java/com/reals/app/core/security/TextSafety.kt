package com.reals.app.core.security

object TextSafety {
    private val invisibleControlChars = Regex("[\\p{Cntrl}&&[^\\n\\t]]")
    private val bidiOverrideChars = Regex("[\\u202A-\\u202E\\u2066-\\u2069]")
    private val repeatedWhitespace = Regex("[ \\t]+")
    private val repeatedBlankLines = Regex("\\n{3,}")

    fun normalizeSingleLine(value: String, maxLength: Int): String {
        return value
            .stripUnsafeInvisibleChars()
            .replace('\n', ' ')
            .collapseInlineWhitespace()
            .trim()
            .limit(maxLength)
    }

    fun normalizeMultiline(value: String, maxLength: Int): String {
        return value
            .stripUnsafeInvisibleChars()
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .joinToString("\n") { it.collapseInlineWhitespace().trim() }
            .replace(repeatedBlankLines, "\n\n")
            .trim()
            .limit(maxLength)
    }

    fun containsHtmlLikeMarkup(value: String): Boolean {
        return Regex("<\\s*/?\\s*[a-zA-Z][^>]*>").containsMatchIn(value)
    }

    fun safeDisplay(value: String, maxLength: Int = 2_000): String {
        return normalizeMultiline(value, maxLength)
    }

    private fun String.stripUnsafeInvisibleChars(): String {
        return replace(invisibleControlChars, "")
            .replace(bidiOverrideChars, "")
    }

    private fun String.collapseInlineWhitespace(): String = replace(repeatedWhitespace, " ")

    private fun String.limit(maxLength: Int): String {
        if (maxLength <= 0) return ""
        return if (length <= maxLength) this else take(maxLength)
    }
}
