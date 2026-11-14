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
}
