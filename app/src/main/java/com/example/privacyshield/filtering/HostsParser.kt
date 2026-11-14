package com.example.privacyshield.filtering

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object HostsParser {

    private val IGNORED_DOMAINS = setOf(
        "localhost",
        "localhost.localdomain",
        "local",
        "broadcasthost",
        "ip6-localhost",
        "ip6-loopback",
        "0.0.0.0",
        "127.0.0.1",
        "::1"
    )

    fun parse(inputStream: InputStream): List<String> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val domains = LinkedHashSet<String>()

        reader.useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith('#') || trimmed.startsWith('!') || trimmed.startsWith(';')) {
                    continue
                }

                val withoutComment = when {
                    trimmed.contains('#') -> trimmed.substringBefore('#').trim()
                    trimmed.contains('!') -> trimmed.substringBefore('!').trim()
                    else -> trimmed
                }
                if (withoutComment.isEmpty()) continue

                if (withoutComment.startsWith("||") && withoutComment.contains('^')) {
                    val rawDomain = withoutComment.removePrefix("||").substringBefore('^').trim()
                    val normalized = DomainName.normalize(rawDomain)
                    if (normalized != null && !IGNORED_DOMAINS.contains(normalized)) {
                        domains.add(normalized)
                    }
                    continue
                }

                val tokens = withoutComment.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (tokens.isEmpty()) continue

                val firstToken = tokens[0]
                if (firstToken == "127.0.0.1" || firstToken == "0.0.0.0" || firstToken == "::1") {

                    for (i in 1 until tokens.size) {
                        val token = tokens[i]
                        val normalized = DomainName.normalize(token)
                        if (normalized != null && !IGNORED_DOMAINS.contains(normalized)) {
                            domains.add(normalized)
                        }
                    }
                } else if (tokens.size == 1) {

                    val normalized = DomainName.normalize(firstToken)
                    if (normalized != null && !IGNORED_DOMAINS.contains(normalized)) {
                        domains.add(normalized)
                    }
                }
            }
        }

        return domains.toList()
    }
}

