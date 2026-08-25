package io.github.magisk317.mipush.runtime.store.adapter

import io.github.magisk317.mipush.runtime.store.kmp.RuntimeDeletedEventRow
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeEventRow

/**
 * Adapter bridge between [RuntimeDeletedEventRow] and [RuntimeEventRow].
 *
 * Replaces the conversion methods that were previously inlined on the
 * production [DeletedEvent] Room entity.
 */

// ── DeletedEventRow -> EventRow ─────────────────────────────────────────────

/**
 * Converts this tombstone row back into a live event row.
 *
 * The [payload] byte array is defensively cloned so the caller can safely
 * mutate the result without affecting the source.
 */
fun RuntimeDeletedEventRow.toEvent(): RuntimeEventRow = RuntimeEventRow(
    id = id,
    pkg = pkg,
    type = type,
    date = date,
    result = result,
    info = info,
    payload = payload?.clone(),
    regSec = regSec,
    searchText = searchText,
    userId = userId,
)

// ── EventRow -> DeletedEventRow ─────────────────────────────────────────────

/**
 * Creates a tombstone row from a live event row and a deletion timestamp.
 *
 * [RuntimeEventRow.id] must be non-null; an [IllegalArgumentException] is
 * thrown otherwise.
 */
fun RuntimeEventRow.toDeletedEvent(deletedAt: Long): RuntimeDeletedEventRow =
    RuntimeDeletedEventRow(
        id = requireNotNull(id),
        userId = userId,
        pkg = pkg,
        type = type,
        date = date,
        result = result,
        info = info,
        searchText = searchText,
        payload = payload?.clone(),
        regSec = regSec,
        deletedAt = deletedAt,
    )
