package io.github.magisk317.mipush.runtime.store.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication

@Dao
interface RegisteredApplicationDao {
    @Query("SELECT * FROM REGISTERED_APPLICATION WHERE pkg = :pkg LIMIT 1")
    suspend fun getByPackageName(pkg: String): RegisteredApplication?

    @Query("SELECT * FROM REGISTERED_APPLICATION")
    suspend fun getAll(): List<RegisteredApplication>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(application: RegisteredApplication): Long

    @Insert
    suspend fun insert(application: RegisteredApplication): Long

    @Update
    suspend fun update(application: RegisteredApplication): Int

    @Delete
    suspend fun delete(application: RegisteredApplication)

    @Query("UPDATE REGISTERED_APPLICATION SET notification_on_register = :enabled")
    suspend fun updateAllNotificationOnRegister(enabled: Boolean): Int

    @Query("UPDATE REGISTERED_APPLICATION SET blocked = :blocked WHERE id = :id")
    suspend fun updateBlocked(id: Long, blocked: Boolean): Int

    @Query("SELECT blocked FROM REGISTERED_APPLICATION WHERE pkg = :pkg LIMIT 1")
    suspend fun isBlocked(pkg: String): Boolean
}
