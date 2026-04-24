package io.github.magisk317.mipush.runtime.core.store.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.magisk317.mipush.runtime.core.store.entities.RegisteredApplication

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
}
