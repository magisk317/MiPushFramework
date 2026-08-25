package io.github.magisk317.mipush.runtime.store.kmp

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Creates the Android Room KMP database with the bundled SQLite driver.
 *
 * The default name is the existing production `db` file. Keeping the builder here ensures the
 * Android driver choice does not leak into the common database definition.
 */
fun configureRuntimeStoreKmp(
    context: Context,
    databaseName: String = "db",
): RuntimeStoreDatabase =
    Room.databaseBuilder<RuntimeStoreDatabase>(
        context = context.applicationContext,
        name = databaseName,
    )
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*RuntimeStoreMigrations.ALL)
        .build()
