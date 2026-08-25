package io.github.magisk317.mipush.runtime.store.kmp

sealed interface RuntimeQueryArgument {
    data class IntValue(val value: Int) : RuntimeQueryArgument
    data class LongValue(val value: Long) : RuntimeQueryArgument
    data class TextValue(val value: String) : RuntimeQueryArgument
}

data class RuntimeEventQuery(
    val sql: String,
    val arguments: List<RuntimeQueryArgument>,
)

/**
 * Platform-neutral query construction for the EVENT table.
 *
 * The Android adapter binds [RuntimeQueryArgument] values to RoomRawQuery; this policy owns the
 * SQL shape, user scoping, optional filters, paging order, and legacy search fallback.
 */
object RuntimeEventQueryPolicy {
    fun byId(
        lastId: Long?,
        size: Int,
        types: Set<Int>?,
        packageName: String?,
        text: String?,
        userId: Int,
    ): RuntimeEventQuery = buildQuery(
        page = false,
        cursorId = lastId,
        offset = null,
        limit = size,
        types = types,
        packageName = packageName,
        text = text,
        userId = userId,
    )

    fun page(
        offset: Int,
        limit: Int,
        types: Set<Int>?,
        packageName: String?,
        text: String?,
        userId: Int,
    ): RuntimeEventQuery = buildQuery(
        page = true,
        cursorId = null,
        offset = offset,
        limit = limit,
        types = types,
        packageName = packageName,
        text = text,
        userId = userId,
    )

    private fun buildQuery(
        page: Boolean,
        cursorId: Long?,
        offset: Int?,
        limit: Int,
        types: Set<Int>?,
        packageName: String?,
        text: String?,
        userId: Int,
    ): RuntimeEventQuery {
        val sql = StringBuilder("SELECT * FROM EVENT WHERE user_id = ?")
        val arguments = mutableListOf<RuntimeQueryArgument>(
            RuntimeQueryArgument.IntValue(userId.coerceAtLeast(0)),
        )

        cursorId?.let {
            sql.append(" AND id < ?")
            arguments += RuntimeQueryArgument.LongValue(it)
        }
        packageName?.takeIf { it.isNotBlank() }?.let {
            sql.append(" AND pkg = ?")
            arguments += RuntimeQueryArgument.TextValue(it)
        }
        types?.takeIf { it.isNotEmpty() }?.let { values ->
            sql.append(" AND type IN (")
            sql.append(values.sorted().joinToString(",") { "?" })
            sql.append(")")
            arguments += values.sorted().map { RuntimeQueryArgument.IntValue(it) }
        }
        text?.takeIf { it.isNotBlank() }?.let {
            sql.append(
                if (page) {
                    " AND (search_text LIKE ? OR dev_info LIKE ?)"
                } else {
                    " AND (search_text LIKE ? OR (search_text IS NULL AND dev_info LIKE ?))"
                },
            )
            val pattern = "%$it%"
            arguments += RuntimeQueryArgument.TextValue(pattern)
            arguments += RuntimeQueryArgument.TextValue(pattern)
        }

        if (page) {
            sql.append(" ORDER BY date DESC LIMIT ? OFFSET ?")
            arguments += RuntimeQueryArgument.IntValue(limit)
            arguments += RuntimeQueryArgument.IntValue(offset ?: 0)
        } else {
            sql.append(" ORDER BY id DESC LIMIT ?")
            arguments += RuntimeQueryArgument.IntValue(limit)
        }
        return RuntimeEventQuery(sql = sql.toString(), arguments = arguments)
    }
}
