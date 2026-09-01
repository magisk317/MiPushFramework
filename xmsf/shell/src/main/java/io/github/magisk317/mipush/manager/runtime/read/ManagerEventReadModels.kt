package io.github.magisk317.mipush.manager.runtime.read

data class ManagerEventReadQuery(
    val schemaVersion: Int = 1,
    val lastId: Long? = null,
    val pageSize: Int = 100,
    val packageName: String = "",
    val query: String = "",
    val userId: Int = -1,
)

data class ManagerEventReadSummary(
    val id: Long,
    val userId: Int,
    val packageName: String,
    val configOptions: List<String>,
    val channel: String,
    val receiveDateMs: Long,
    val title: String,
    val content: String,
    val appName: String?,
    val type: Int,
    val result: Int,
    val info: String?,
    val payload: ByteArray?,
    val regSec: String?,
)

data class ManagerEventReadPage(
    val items: List<ManagerEventReadSummary>,
)
