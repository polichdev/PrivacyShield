package com.example.privacyshield.filtering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainRulesTest {
    @Test fun normalizesCaseRootDotAndIdn() {
        assertEquals("example.com", DomainName.normalize("Example.COM."))
        assertEquals("xn--e1afmkfd.xn--p1ai", DomainName.normalize("пример.рф"))
    }

    @Test fun rejectsInvalidNames() {
        listOf("", ".", "example..com", " example.com", "bad_name.com", "a".repeat(64) + ".com", "example.com..").forEach {
            assertNull(it, DomainName.normalize(it))
        }
    }

    @Test fun allowOverridesUserAndSubscriptionBlocks() {
        val matcher = DomainRuleMatcher(listOf(
            DomainRule("subscription", "example.com", RuleSource.SUBSCRIPTION, RuleMatch.INCLUDE_SUBDOMAINS),
            DomainRule("block", "safe.example.com", RuleSource.USER_BLOCK, RuleMatch.EXACT),
            DomainRule("allow", "safe.example.com", RuleSource.USER_ALLOW, RuleMatch.EXACT),
        ))
        assertEquals(FilterDecision.Allow, matcher.evaluate("SAFE.example.com."))
        assertEquals(FilterDecision.Block("subscription"), matcher.evaluate("ads.example.com"))
        assertEquals(FilterDecision.Allow, matcher.evaluate("notexample.com"))
        assertEquals(FilterDecision.InvalidDomain, matcher.evaluate("bad..name"))
    }

    @Test fun exactRulesDoNotMatchSubdomainsAndUserBlockPrecedesSubscription() {
        val matcher = DomainRuleMatcher(listOf(
            DomainRule("sub", "example.com", RuleSource.SUBSCRIPTION, RuleMatch.INCLUDE_SUBDOMAINS),
            DomainRule("user", "example.com", RuleSource.USER_BLOCK, RuleMatch.EXACT),
        ))
        assertEquals(FilterDecision.Block("user"), matcher.evaluate("example.com"))
        assertEquals(FilterDecision.Block("sub"), matcher.evaluate("www.example.com"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnDuplicateRuleId() {
        DomainRuleMatcher(listOf(
            DomainRule("dup-id", "example.com", RuleSource.USER_BLOCK, RuleMatch.EXACT),
            DomainRule("dup-id", "other.com", RuleSource.USER_ALLOW, RuleMatch.EXACT)
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnBlankRuleId() {
        DomainRuleMatcher(listOf(
            DomainRule("  ", "example.com", RuleSource.USER_BLOCK, RuleMatch.EXACT)
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnInvalidRuleDomain() {
        DomainRuleMatcher(listOf(
            DomainRule("rule-1", "bad..domain", RuleSource.USER_BLOCK, RuleMatch.EXACT)
        ))
    }

    @Test
    fun defaultsToAllowWhenNoRulesMatch() {
        val matcher = DomainRuleMatcher(emptyList())
        assertEquals(FilterDecision.Allow, matcher.evaluate("anything.org"))
    }

    @Test
    fun compactTrieMatchesSubdomainsAndAdultAdNetworks() {
        val matcher = DomainRuleMatcher(listOf(
            DomainRule("tj-1", "trafficjunky.net", RuleSource.USER_BLOCK, RuleMatch.INCLUDE_SUBDOMAINS),
            DomainRule("exo-1", "exoclick.com", RuleSource.USER_BLOCK, RuleMatch.INCLUDE_SUBDOMAINS),
            DomainRule("allow-safe", "safe.trafficjunky.net", RuleSource.USER_ALLOW, RuleMatch.EXACT)
        ))

        assertEquals(FilterDecision.Block("tj-1"), matcher.evaluate("trafficjunky.net"))
        assertEquals(FilterDecision.Block("tj-1"), matcher.evaluate("delivery.trafficjunky.net"))
        assertEquals(FilterDecision.Block("tj-1"), matcher.evaluate("ads.sub.trafficjunky.net"))
        assertEquals(FilterDecision.Block("exo-1"), matcher.evaluate("ads.exoclick.com"))
        assertEquals(FilterDecision.Allow, matcher.evaluate("safe.trafficjunky.net"))
        assertEquals(FilterDecision.Allow, matcher.evaluate("pornhub.com"))
    }
}
