package io.github.bijojp.thingspot.domain

import kotlin.random.Random

/**
 * The code printed on a Label, e.g. K7Q3MX9P. Eight characters from an alphabet without
 * look-alikes (no 0/O, 1/I/L), so a smudged Label can still be read and typed.
 */
@JvmInline
value class LabelCode(val value: String) {
    /** What the QR code holds. Upper case and ':' keep it in QR's compact alphanumeric mode. */
    val qrContent: String get() = QR_PREFIX + value

    /** For people: K7Q3-MX9P. */
    val display: String get() = value.take(4) + "-" + value.drop(4)

    companion object {
        private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
        private const val LENGTH = 8
        private const val QR_PREFIX = "THINGSPOT:L:"

        fun random(random: Random = Random.Default): LabelCode =
            LabelCode(String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] }))

        /** The code in a scanned QR code, or null if it isn't a ThingSpot Label. */
        fun fromQr(text: String): LabelCode? =
            text.trim().takeIf { it.startsWith(QR_PREFIX) }?.removePrefix(QR_PREFIX)?.let(::parse)

        /** A typed or printed code ("k7q3-mx9p" works too), or null if it can't be one. */
        fun parse(text: String): LabelCode? {
            val code = text.uppercase().filter { it != '-' && it != ' ' }
            return if (code.length == LENGTH && code.all { it in ALPHABET }) LabelCode(code) else null
        }
    }
}

/** A Label stuck on a Place. A blank Label has no record at all until it is linked. */
data class Label(val code: LabelCode, val placeId: PlaceId, val createdAt: Long)
