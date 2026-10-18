package io.github.magisk317.mipush.manager.xmsfdelivery

/**
 * Ordering for XMSF runtime version names.
 *
 * Registry version names are semver-ish: a dotted numeric core optionally
 * followed by a "-suffix" (CI builds publish names such as
 * `1.0.9-20261016_235959`). A clean release outranks its own pre-release
 * children, while a newer core always outranks an older one regardless of
 * suffixes — so plain string comparison, which ranks "1.0.10" *below*
 * "1.0.9", must never be used to pick the newest runtime.
 */
object XmsfVersionComparator : Comparator<String> {

    override fun compare(a: String, b: String): Int {
        if (a == b) return 0
        val core = compareCore(a.substringBefore('-'), b.substringBefore('-'))
        if (core != 0) return core
        return compareSuffix(a.substringAfter('-', ""), b.substringAfter('-', ""))
    }

    /** Whether [version] is strictly newer than [other]. */
    fun isNewer(version: String, other: String): Boolean = compare(version, other) > 0

    private fun compareCore(a: String, b: String): Int {
        val aParts = a.split('.')
        val bParts = b.split('.')
        for (index in 0 until maxOf(aParts.size, bParts.size)) {
            val aPart = aParts.getOrNull(index).orEmpty()
            val bPart = bParts.getOrNull(index).orEmpty()
            if (!isNumeric(aPart) || !isNumeric(bPart)) {
                // A non-numeric core part cannot be ordered numerically; fall
                // back to a lexicographic compare that keeps the ordering
                // total and antisymmetric.
                return a.compareTo(b)
            }
            // A missing trailing part counts as zero, so "1.0" and "1.0.0"
            // stay equivalent.
            val aValue = aPart.toLongOrNull() ?: 0L
            val bValue = bPart.toLongOrNull() ?: 0L
            if (aValue != bValue) return aValue.compareTo(bValue)
        }
        return 0
    }

    private fun compareSuffix(a: String, b: String): Int = when {
        a.isEmpty() && b.isEmpty() -> 0
        a.isEmpty() -> 1 // 1.0.9 (release) outranks 1.0.9-20261016 (pre-release)
        b.isEmpty() -> -1
        else -> a.compareTo(b)
    }

    private fun isNumeric(part: String): Boolean = part.isEmpty() || part.all { it in '0'..'9' }
}
