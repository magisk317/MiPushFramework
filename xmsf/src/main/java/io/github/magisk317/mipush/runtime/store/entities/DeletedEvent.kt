package io.github.magisk317.mipush.runtime.store.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** Runtime-owned tombstone used by the event-list undo action. */
@Entity(
    tableName = "DELETED_EVENT",
    primaryKeys = ["id", "user_id"],
    indices = [Index(value = ["user_id", "date"])],
)
data class DeletedEvent(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "pkg") val pkg: String,
    @ColumnInfo(name = "type") val type: Int,
    @ColumnInfo(name = "date") val date: Long,
    @ColumnInfo(name = "result") val result: Int,
    @ColumnInfo(name = "dev_info") val info: String?,
    @ColumnInfo(name = "search_text") val searchText: String?,
    @ColumnInfo(name = "payload") val payload: ByteArray?,
    @ColumnInfo(name = "reg_sec") val regSec: String?,
    @ColumnInfo(name = "deleted_at", defaultValue = "0") val deletedAt: Long,
) {
    fun toEvent(): Event = Event(
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

    companion object {
        fun fromEvent(event: Event, deletedAt: Long): DeletedEvent = DeletedEvent(
            id = requireNotNull(event.id),
            userId = event.userId,
            pkg = event.pkg,
            type = event.type,
            date = event.date,
            result = event.result,
            info = event.info,
            searchText = event.searchText,
            payload = event.payload?.clone(),
            regSec = event.regSec,
            deletedAt = deletedAt,
        )
    }
}
