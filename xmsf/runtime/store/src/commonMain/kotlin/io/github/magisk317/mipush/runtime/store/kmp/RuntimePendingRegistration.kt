package io.github.magisk317.mipush.runtime.store.kmp

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Only an intercepted real request creates this row; installed-app catalog entries never do. */
@Entity(tableName = "PENDING_REGISTRATION", primaryKeys = ["user_id", "pkg"])
data class RuntimePendingRegistrationRow(
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "pkg") val packageName: String,
    val payload: ByteArray,
    @ColumnInfo(name = "env_changed") val envChanged: Boolean,
    @ColumnInfo(name = "env_type") val envType: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Dao
interface RuntimePendingRegistrationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(request: RuntimePendingRegistrationRow)

    @Query("SELECT * FROM PENDING_REGISTRATION WHERE pkg = :packageName AND user_id = :userId LIMIT 1")
    suspend fun get(packageName: String, userId: Int): RuntimePendingRegistrationRow?

    @Query("SELECT * FROM PENDING_REGISTRATION WHERE user_id = :userId ORDER BY created_at, pkg")
    suspend fun getAll(userId: Int): List<RuntimePendingRegistrationRow>

    @Query("DELETE FROM PENDING_REGISTRATION WHERE pkg = :packageName AND user_id = :userId")
    suspend fun clear(packageName: String, userId: Int): Int
}
