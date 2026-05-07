package io.github.magisk317.mipush.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.magisk317.mipush.runtime.store.db.AppDatabase
import io.github.magisk317.mipush.runtime.store.db.AppDatabaseMigrations
import io.github.magisk317.mipush.runtime.store.db.EventDao
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDao
import io.github.magisk317.mipush.data.dataStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "db"
        )
        .addMigrations(*AppDatabaseMigrations.ALL)
        .build()
    }

    @Provides
    fun provideEventDao(database: AppDatabase): EventDao {
        return database.eventDao()
    }

    @Provides
    fun provideRegisteredApplicationDao(database: AppDatabase): RegisteredApplicationDao {
        return database.registeredApplicationDao()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}
