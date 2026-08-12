package io.github.magisk317.mipush.runtime.store.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.magisk317.mipush.runtime.store.db.converters.DateConverter
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.entities.DeletedEvent
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication

// version 7: EVENT/REGISTERED_APPLICATION 增加 user_id，身份从 package-only
// 收敛为 (user_id, package)。旧数据由 DatabaseUtils 在打开数据库后归属当前 user。
// version 6: EVENT 表新增 search_text 列(UI 对齐的可搜索快照)。
// 升级路径 MIGRATION_5_6 以 ALTER TABLE ADD COLUMN 落地,老行 search_text 为 NULL,
// 查询侧回退到 dev_info 兼容,历史事件保留。
// version 8: DELETED_EVENT stores runtime-owned event tombstones for undo.
// version 9: DELETED_EVENT records deletion time for bounded retention.
@Database(entities = [Event::class, DeletedEvent::class, RegisteredApplication::class], version = 9, exportSchema = true)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun registeredApplicationDao(): RegisteredApplicationDao
}
