package io.github.magisk317.mipush.manager.xmsfdelivery

import java.util.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XmsfVersionComparatorTest {

    @Test
    fun `numeric cores compare numerically, not lexicographically`() {
        assertTrue(XmsfVersionComparator.isNewer("1.0.10", "1.0.9"))
        assertTrue(XmsfVersionComparator.isNewer("1.10.0", "1.9.0"))
        assertTrue(XmsfVersionComparator.isNewer("2.0.0", "1.99.99"))
        assertFalse(XmsfVersionComparator.isNewer("1.0.9", "1.0.10"))
    }

    @Test
    fun `pre-release suffix loses to the clean release of the same core`() {
        assertTrue(XmsfVersionComparator.isNewer("1.0.9", "1.0.9-20261016_235959"))
        assertFalse(XmsfVersionComparator.isNewer("1.0.9-20261016_235959", "1.0.9"))
    }

    @Test
    fun `newer core outranks an older clean release despite the suffix`() {
        assertTrue(XmsfVersionComparator.isNewer("1.0.10-20261016_000000", "1.0.9"))
        assertFalse(XmsfVersionComparator.isNewer("1.0.9", "1.0.10-20261016_000000"))
    }

    @Test
    fun `registry max picks the clean release over its own ci children`() {
        val versions = listOf(
            "1.0.8-20261014_222819",
            "1.0.9",
            "1.0.9-20261016_120000",
            "1.0.10",
        )
        assertEquals("1.0.10", versions.sortedWith(XmsfVersionComparator).last())
    }

    @Test
    fun `registry max over only pre-releases picks the newest core`() {
        val versions = listOf("1.0.9-20261016_120000", "1.0.8-20261014_222819", "1.0.9-20261015_080000")
        assertEquals("1.0.9-20261016_120000", versions.sortedWith(XmsfVersionComparator).last())
    }

    @Test
    fun `identical versions are never newer`() {
        assertFalse(XmsfVersionComparator.isNewer("1.0.9", "1.0.9"))
        assertFalse(XmsfVersionComparator.isNewer("1.0.9-20261016_235959", "1.0.9-20261016_235959"))
    }

    @Test
    fun `missing trailing parts count as zero`() {
        assertEquals(0, XmsfVersionComparator.compare("1.0", "1.0.0"))
        assertTrue(XmsfVersionComparator.isNewer("1.0.1", "1.0"))
        assertFalse(XmsfVersionComparator.isNewer("1.0", "1.0.0.1"))
    }

    @Test
    fun `blank and garbage versions keep a deterministic order`() {
        assertTrue(XmsfVersionComparator.isNewer("1.0.9", ""))
        assertFalse(XmsfVersionComparator.isNewer("", "1.0.9"))
        assertTrue(XmsfVersionComparator.compare("abc", "abd") < 0)
        assertTrue(XmsfVersionComparator.compare("abd", "abc") > 0)
        assertEquals(0, XmsfVersionComparator.compare("", ""))
    }

    @Test
    fun `ordering stays antisymmetric across a shuffled corpus`() {
        // Property (seeded, 500 rounds): swapping the operands must always
        // flip the sign, so the comparator defines a total order that
        // sortedWith / maxWith can rely on.
        val corpus = listOf(
            "1.0.8-20261014_222819", "1.0.9", "1.0.9-20261016_120000", "1.0.10",
            "0.9.9", "2.0.0", "1.0", "1.0.0", "", "nightly", "1.0.9-beta",
        )
        val random = Random(20261016L)
        repeat(500) {
            val a = corpus[random.nextInt(corpus.size)]
            val b = corpus[random.nextInt(corpus.size)]
            val forward = Integer.signum(XmsfVersionComparator.compare(a, b))
            val backward = Integer.signum(XmsfVersionComparator.compare(b, a))
            assertEquals(-forward, backward, "compare($a, $b) is not antisymmetric")
        }
    }
}
