package io.github.magisk317.mipush.runtime.store.kmp

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 只包含可移植持久化字段的生产存储行。
 *
 * 这些实体复刻并拥有 xmsf v9 的表结构；Android-only 的
 * thrift、Context、PackageManager 和 SharedPreferences 逻辑继续留在 xmsf adapter 层。
 */
@Entity(
    tableName = "EVENT",
    indices = [
        Index(value = ["user_id", "pkg"], name = "index_EVENT_user_id_pkg"),
        Index(value = ["user_id", "date"], name = "index_EVENT_user_id_date"),
    ],
)
data class RuntimeEventRow(
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
    @ColumnInfo(name = "pkg")
    val pkg: String,
    @ColumnInfo(name = "user_id", defaultValue = "0")
    val userId: Int = 0,
    val type: Int,
    val date: Long,
    val result: Int,
    @ColumnInfo(name = "dev_info")
    val info: String? = null,
    @ColumnInfo(name = "search_text")
    val searchText: String? = null,
    val payload: ByteArray? = null,
    @ColumnInfo(name = "reg_sec")
    val regSec: String? = null,
)

@Entity(
    tableName = "DELETED_EVENT",
    primaryKeys = ["id", "user_id"],
    indices = [
        Index(value = ["user_id", "date"], name = "index_DELETED_EVENT_user_id_date"),
    ],
)
data class RuntimeDeletedEventRow(
    val id: Long,
    @ColumnInfo(name = "user_id")
    val userId: Int,
    val pkg: String,
    val type: Int,
    val date: Long,
    val result: Int,
    @ColumnInfo(name = "dev_info")
    val info: String? = null,
    @ColumnInfo(name = "search_text")
    val searchText: String? = null,
    val payload: ByteArray? = null,
    @ColumnInfo(name = "reg_sec")
    val regSec: String? = null,
    @ColumnInfo(name = "deleted_at", defaultValue = "0")
    val deletedAt: Long = 0,
)

@Entity(
    tableName = "REGISTERED_APPLICATION",
    indices = [
        Index(value = ["user_id", "pkg"], name = "index_REGISTERED_APPLICATION_user_id_pkg", unique = true),
    ],
)
data class RuntimeRegisteredApplicationRow(
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
    @ColumnInfo(name = "pkg")
    val packageName: String,
    @ColumnInfo(name = "user_id", defaultValue = "0")
    val userId: Int = 0,
    val type: Int,
    @ColumnInfo(name = "notification_on_register")
    val notificationOnRegister: Boolean,
    @ColumnInfo(name = "blocked", defaultValue = "0")
    val blocked: Boolean = false,
    @ColumnInfo(name = "island_enabled", defaultValue = "1")
    val islandEnabled: Boolean = true,
    @ColumnInfo(name = "island_focus_notification", defaultValue = "0")
    val islandFocusNotification: Boolean = false,
    @ColumnInfo(name = "registered_type")
    val registeredType: Int,
    @ColumnInfo(name = "app_name")
    val appName: String,
)
