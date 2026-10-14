package com.cylldby.blocker.util

/**
 * Pure DNS blocking logic — no Android dependencies, fully unit-testable.
 */
object DnsBlocker {

    // Core adult keywords to block unconditionally at the DNS layer
    private val ADULT_KEYWORDS = listOf(
        "porn", "xxx", "nsfw", "adult", "sex", "hentai", "xhamster", "xvideos", "pornhub",
        "redtube", "youporn", "brazzers", "chaturbate", "stripchat", "onlyfans", "cam4",
        "livejasmin", "erome", "erotic", "nude", "hardcore", "rule34", "beeg", "tubegalore"
    )

    fun isDomainBlocked(domain: String, blockedDomains: Set<String>): Boolean {
        val lower = domain.lowercase()
        if (ADULT_KEYWORDS.any { lower.contains(it) }) return true
        return blockedDomains.any { blocked -> lower == blocked || lower.endsWith(".$blocked") }
    }

    fun parseDnsQueryDomain(dns: ByteArray): String? {
        if (dns.size < 12) return null
        var i = 12
        val sb = StringBuilder()
        while (i < dns.size) {
            val len = dns[i].toInt() and 0xFF
            if (len == 0) break
            if (sb.isNotEmpty()) sb.append('.')
            i++
            if (i + len > dns.size) return null
            sb.append(String(dns, i, len))
            i += len
        }
        return sb.takeIf { it.isNotEmpty() }?.toString()
    }

    fun buildNxdomainResponse(query: ByteArray): ByteArray {
        val r = query.copyOf()
        r[2] = (r[2].toInt() or 0x80).toByte()  // QR = response
        r[3] = (r[3].toInt() or 0x03).toByte()  // RCODE = NXDOMAIN
        return r
    }

    fun normalizeInputDomain(raw: String): String =
        raw.trim().lowercase()
            .removePrefix("https://").removePrefix("http://").removePrefix("www.")
}
