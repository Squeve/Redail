package com.squeve.redail

// Phone number helpers with no Android dependency, so they can be unit tested.

internal fun digitsKey(n: String) = n.filter { it.isDigit() }.takeLast(9)

internal fun isValidNumber(n: String): Boolean {
    val digits = n.count { it.isDigit() }
    return digits in 3..15
}

/** Local numbers (one leading 0) get the chosen country code. */
internal fun normalizeNumber(n: String, countryCode: String): String {
    val cc = countryCode.filter { it.isDigit() }
    return if (cc.isNotEmpty() && n.startsWith("0") && !n.startsWith("00")) "+" + cc + n.drop(1) else n
}

private val PHONE_RE = Regex("""(?<![\w+])\+?\d[\d\s().\-]{5,}\d""")

/** Pulls phone-like numbers out of any text (shared text, clipboard, a CSV file). */
internal fun extractNumbers(text: String): List<String> =
    PHONE_RE.findAll(text)
        .map { m -> m.value.filter { it.isDigit() || it == '+' } }
        .filter { n -> n.count { it.isDigit() } in 7..15 }
        .distinct()
        .toList()
