package io.github.bijojp.thingspot.domain

import java.text.Normalizer
import java.util.Locale

private val WHITESPACE = Regex("\\s+")
private val DIACRITICS = Regex("\\p{Mn}+")

/** A name as stored: whitespace collapsed, trimmed, cut to [maxLength]; null when nothing is left. */
fun cleanName(raw: String, maxLength: Int): String? =
    raw.replace(WHITESPACE, " ").trim().take(maxLength).trimEnd().ifEmpty { null }

/** Notes as stored: trimmed and cut to [Limits.NOTES]. Line breaks are kept. */
fun cleanNotes(raw: String): String = raw.trim().take(Limits.NOTES).trimEnd()

/** Text folded for matching: lower case, accents removed, whitespace collapsed ("Café" → "cafe"). */
internal fun fold(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .replace(WHITESPACE, " ")
        .trim()
