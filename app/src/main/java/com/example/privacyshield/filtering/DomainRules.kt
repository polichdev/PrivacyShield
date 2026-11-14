package com.example.privacyshield.filtering

import java.net.IDN
import java.util.Locale

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

class DomainRuleMatcher(rules: List<DomainRule>) {
    private val normalizedRules = rules.map { rule ->
        require(rule.id.isNotBlank()) { "Rule ID must not be blank" }
        rule.copy(domain = requireNotNull(DomainName.normalize(rule.domain)) { "Invalid rule domain" })
    }.also { normalized ->
        require(normalized.map { it.id }.toSet().size == normalized.size) { "Duplicate rule ID" }
    }.sortedWith(compareBy<DomainRule>({ it.source.ordinal }, { it.id }))

    private val ruleRanks = HashMap<String, Int>(normalizedRules.size * 4 / 3 + 1).apply {
        normalizedRules.forEachIndexed { index, rule -> put(rule.id, index) }
    }

    private class TierMaps {
        val exact = HashMap<String, DomainRule>()
        val wildcard = HashMap<String, DomainRule>()
    }

    private val userAllowTier = TierMaps()
    private val userBlockTier = TierMaps()
    private val subscriptionTier = TierMaps()

    init {
        for (rule in normalizedRules) {
            val tier = when (rule.source) {
                RuleSource.USER_ALLOW -> userAllowTier
                RuleSource.USER_BLOCK -> userBlockTier
                RuleSource.SUBSCRIPTION -> subscriptionTier
            }
            if (rule.match == RuleMatch.EXACT) {
                tier.exact.putIfAbsent(rule.domain, rule)
            } else {
                tier.wildcard.putIfAbsent(rule.domain, rule)
            }
        }
    }

    fun evaluate(domain: String): FilterDecision {
        val normalized = DomainName.normalize(domain) ?: return FilterDecision.InvalidDomain

        val allowMatch = findBestMatch(userAllowTier, normalized)
        if (allowMatch != null) return FilterDecision.Allow

        val blockMatch = findBestMatch(userBlockTier, normalized)
        if (blockMatch != null) return FilterDecision.Block(blockMatch.id)

        val subMatch = findBestMatch(subscriptionTier, normalized)
        if (subMatch != null) return FilterDecision.Block(subMatch.id)

        return FilterDecision.Allow
    }

    private fun findBestMatch(tier: TierMaps, domain: String): DomainRule? {
        var bestRule: DomainRule? = null
        var bestRank = Int.MAX_VALUE

        fun consider(candidate: DomainRule?) {
            if (candidate == null) return
            val rank = ruleRanks[candidate.id] ?: Int.MAX_VALUE
            if (rank < bestRank) {
                bestRank = rank
                bestRule = candidate
            }
        }

        consider(tier.exact[domain])
        consider(tier.wildcard[domain])

        var dotIndex = domain.indexOf('.')
        while (dotIndex != -1 && dotIndex < domain.length - 1) {
            val parent = domain.substring(dotIndex + 1)
            consider(tier.wildcard[parent])
            dotIndex = domain.indexOf('.', dotIndex + 1)
        }

        return bestRule
    }
}

