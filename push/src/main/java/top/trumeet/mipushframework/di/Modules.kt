package top.trumeet.mipushframework.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import top.trumeet.mipush.provider.db.AppDatabase
import top.trumeet.mipush.provider.db.AppDatabaseMigrations
import top.trumeet.mipush.provider.db.EventDao
import top.trumeet.mipush.provider.db.RegisteredApplicationDao
import com.magisk317.data.dataStore
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
