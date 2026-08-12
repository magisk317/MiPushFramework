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
    @Query("SELECT * FROM REGISTERED_APPLICATION WHERE pkg = :pkg AND user_id = :userId LIMIT 1")
    suspend fun getByPackageName(pkg: String, userId: Int): RegisteredApplication?

    @Query("SELECT * FROM REGISTERED_APPLICATION WHERE user_id = :userId")
    suspend fun getAll(userId: Int): List<RegisteredApplication>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(application: RegisteredApplication): Long

    @Insert
    suspend fun insert(application: RegisteredApplication): Long

    @Update
    suspend fun update(application: RegisteredApplication): Int

    @Delete
    suspend fun delete(application: RegisteredApplication)

    @Query("UPDATE REGISTERED_APPLICATION SET blocked = :blocked WHERE id = :id AND user_id = :userId")
    suspend fun updateBlocked(id: Long, blocked: Boolean, userId: Int): Int

    @Query("SELECT blocked FROM REGISTERED_APPLICATION WHERE pkg = :pkg AND user_id = :userId LIMIT 1")
    suspend fun isBlocked(pkg: String, userId: Int): Boolean?

    @Query("SELECT island_enabled FROM REGISTERED_APPLICATION WHERE pkg = :pkg AND user_id = :userId LIMIT 1")
    suspend fun isIslandEnabled(pkg: String, userId: Int): Boolean?

    @Query("SELECT island_focus_notification FROM REGISTERED_APPLICATION WHERE pkg = :pkg AND user_id = :userId LIMIT 1")
    suspend fun isIslandFocusNotificationEnabled(pkg: String, userId: Int): Boolean?
}
