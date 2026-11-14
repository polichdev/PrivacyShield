package com.example.privacyshield.filtering

import java.net.IDN
import java.util.Locale

/** DNS hostnames are normalized to ASCII without a trailing root dot. */
object DomainName {
    fun normalize(input: String): String? {
        if (input.isEmpty() || input != input.trim()) return null
        val name = input.removeSuffix(".")
        if (name.isEmpty()) return null
        val ascii = try {
            IDN.toASCII(name, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (ascii.length > 253 || ascii.split('.').any { it.isEmpty() || it.length > 63 }) return null
        return ascii
    }
}

enum class RuleSource { USER_ALLOW, USER_BLOCK, SUBSCRIPTION }
enum class RuleMatch { EXACT, INCLUDE_SUBDOMAINS }

data class DomainRule(val id: String, val domain: String, val source: RuleSource, val match: RuleMatch)

sealed interface FilterDecision {
    data object Allow : FilterDecision
    data class Block(val ruleId: String) : FilterDecision
    data object InvalidDomain : FilterDecision
}

/** Immutable index; invalid or duplicate rule IDs fail at construction. */
class DomainRuleMatcher(rules: List<DomainRule>) {
    private val rules = rules.map { rule ->
        require(rule.id.isNotBlank()) { "Rule ID must not be blank" }
        rule.copy(domain = requireNotNull(DomainName.normalize(rule.domain)) { "Invalid rule domain" })
    }.also { normalized ->
        require(normalized.map { it.id }.toSet().size == normalized.size) { "Duplicate rule ID" }
    }.sortedWith(compareBy<DomainRule>({ it.source.ordinal }, { it.id }))

    fun evaluate(domain: String): FilterDecision {
        val normalized = DomainName.normalize(domain) ?: return FilterDecision.InvalidDomain
        val match = rules.firstOrNull { rule ->
            normalized == rule.domain ||
                (rule.match == RuleMatch.INCLUDE_SUBDOMAINS && normalized.endsWith(".${rule.domain}"))
        } ?: return FilterDecision.Allow
        return if (match.source == RuleSource.USER_ALLOW) FilterDecision.Allow else FilterDecision.Block(match.id)
    }
}
