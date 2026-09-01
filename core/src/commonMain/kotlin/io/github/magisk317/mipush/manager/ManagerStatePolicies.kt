package io.github.magisk317.mipush.manager

/** Stateless manager page policies shared by UI and non-UI clients. */
object ManagerStatePolicies {
    fun acceptsPage(pageIndex: Int, pageCount: Int, settledPage: Int, isAnimating: Boolean): Boolean =
        pageIndex in 0 until pageCount && !isAnimating && pageIndex == settledPage

    fun <T> newestFirst(items: Iterable<T>, timestamp: (T) -> Long): List<T> =
        items.sortedWith(compareByDescending<T> { timestamp(it) })

    fun isValidSnapshot(createdAtMillis: Long, expiresAtMillis: Long, nowMillis: Long, complete: Boolean): Boolean =
        complete && createdAtMillis >= 0L && expiresAtMillis >= createdAtMillis &&
            nowMillis in createdAtMillis..expiresAtMillis
}
