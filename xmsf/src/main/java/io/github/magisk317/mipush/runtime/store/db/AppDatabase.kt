package io.github.magisk317.mipush.runtime.store.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.magisk317.mipush.runtime.store.db.converters.DateConverter
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication

// version 6: EVENT 表新增 search_text 列(UI 对齐的可搜索快照)。
// 升级路径 MIGRATION_5_6 以 ALTER TABLE ADD COLUMN 落地,老行 search_text 为 NULL,
// 查询侧回退到 dev_info 兼容,历史事件保留。
@Database(entities = [Event::class, RegisteredApplication::class], version = 6, exportSchema = true)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun registeredApplicationDao(): RegisteredApplicationDao
}
