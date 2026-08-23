package io.github.magisk317.mipush.runtime.store.kmp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RuntimeEventDao {
    @Insert
    suspend fun insert(event: RuntimeEventRow): Long

    @Query("SELECT * FROM EVENT WHERE user_id = :userId ORDER BY date DESC")
    suspend fun findByUser(userId: Int): List<RuntimeEventRow>
}

@Dao
interface RuntimeDeletedEventDao {
    @Insert
    suspend fun insert(event: RuntimeDeletedEventRow)

    @Query("SELECT * FROM DELETED_EVENT WHERE user_id = :userId ORDER BY deleted_at DESC")
    suspend fun findByUser(userId: Int): List<RuntimeDeletedEventRow>
}

@Dao
interface RuntimeRegisteredApplicationDao {
    @Insert
    suspend fun insert(application: RuntimeRegisteredApplicationRow): Long

    @Query("SELECT * FROM REGISTERED_APPLICATION WHERE user_id = :userId ORDER BY pkg")
    suspend fun findByUser(userId: Int): List<RuntimeRegisteredApplicationRow>
}
