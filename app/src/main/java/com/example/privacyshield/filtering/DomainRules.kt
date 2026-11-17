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

class CompactDomainTrie {
    private class Node {
        val children = HashMap<String, Node>()
        var exactRule: DomainRule? = null
        var wildcardRule: DomainRule? = null
        var exactRank: Int = Int.MAX_VALUE
        var wildcardRank: Int = Int.MAX_VALUE
    }

    private val root = Node()

    fun insert(labelsReversed: List<String>, rule: DomainRule, rank: Int) {
        var current = root
        for (label in labelsReversed) {
            current = current.children.getOrPut(label) { Node() }
        }
        if (rule.match == RuleMatch.EXACT) {
            if (rank < current.exactRank) {
                current.exactRule = rule
                current.exactRank = rank
            }
        } else {
            if (rank < current.wildcardRank) {
                current.wildcardRule = rule
                current.wildcardRank = rank
            }
        }
    }

    fun findBestMatch(labelsReversed: List<String>): DomainRule? {
        var bestRule: DomainRule? = null
        var bestRank = Int.MAX_VALUE
        var current = root

        for (i in labelsReversed.indices) {
            val label = labelsReversed[i]
            val next = current.children[label] ?: break
            current = next

            val isLastLabel = (i == labelsReversed.size - 1)
            if (isLastLabel) {
                if (current.exactRule != null && current.exactRank < bestRank) {
                    bestRank = current.exactRank
                    bestRule = current.exactRule
                }
            }
            if (current.wildcardRule != null && current.wildcardRank < bestRank) {
                bestRank = current.wildcardRank
                bestRule = current.wildcardRule
            }
        }

        return bestRule
    }
}

class DomainRuleMatcher(rules: List<DomainRule>) {
    private val userAllowTrie = CompactDomainTrie()
    private val userBlockTrie = CompactDomainTrie()
    private val subscriptionTrie = CompactDomainTrie()

    init {
        val seenIds = HashSet<String>(rules.size)
        for (i in rules.indices) {
            val rule = rules[i]
            require(rule.id.isNotBlank()) { "Rule ID must not be blank" }
            require(seenIds.add(rule.id)) { "Duplicate rule ID" }
            val normalized = requireNotNull(DomainName.normalize(rule.domain)) { "Invalid rule domain" }
            val trie = when (rule.source) {
                RuleSource.USER_ALLOW -> userAllowTrie
                RuleSource.USER_BLOCK -> userBlockTrie
                RuleSource.SUBSCRIPTION -> subscriptionTrie
            }
            val labelsReversed = normalized.split('.').reversed()
            trie.insert(labelsReversed, if (normalized == rule.domain) rule else rule.copy(domain = normalized), i)
        }
    }

    fun evaluate(domain: String): FilterDecision {
        val normalized = DomainName.normalize(domain) ?: return FilterDecision.InvalidDomain
        val labelsReversed = normalized.split('.').reversed()

        val allowMatch = userAllowTrie.findBestMatch(labelsReversed)
        if (allowMatch != null) return FilterDecision.Allow

        val blockMatch = userBlockTrie.findBestMatch(labelsReversed)
        if (blockMatch != null) return FilterDecision.Block(blockMatch.id)

        val subMatch = subscriptionTrie.findBestMatch(labelsReversed)
        if (subMatch != null) return FilterDecision.Block(subMatch.id)

        return FilterDecision.Allow
    }
}
