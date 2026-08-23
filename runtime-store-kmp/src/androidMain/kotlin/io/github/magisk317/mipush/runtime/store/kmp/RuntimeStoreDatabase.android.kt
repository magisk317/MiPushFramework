package io.github.magisk317.mipush.runtime.store.kmp

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Creates the Android Room KMP shadow database with an explicit application context.
 *
 * Room KSP generates the platform `actual RuntimeStoreDatabaseConstructor`; this adapter only
 * supplies the Android builder and remains intentionally disconnected from xmsf production code.
 */
fun configureRuntimeStoreKmp(context: Context): RuntimeStoreDatabase =
    Room.databaseBuilder<RuntimeStoreDatabase>(
        context = context.applicationContext,
        name = "runtime-store-kmp-shadow.db",
    )
        .setDriver(BundledSQLiteDriver())
        .build()
