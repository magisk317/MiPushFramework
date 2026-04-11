package io.github.magisk317.mipush.common.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal

/**
 * Created by Trumeet on 2017/12/29.
 * 操作 Provider 的 Util
 */
class DatabaseUtils(private val uri: Uri, private val resolver: ContentResolver) {

    companion object {
        const val KEY_ID = "_id"

        @JvmStatic
        fun order(column: String, order: String): String {
            return "$column $order"
        }

        @JvmStatic
        fun limitAndOffset(limit: Int?, offset: Int?): String {
            if (limit == null && offset == null) return ""
            val builder = StringBuilder()
            if (limit != null) {
                builder.append(" LIMIT ")
                builder.append(limit)
            }
            if (offset != null) {
                builder.append(" OFFSET ")
                builder.append(offset)
            }
            return builder.toString()
        }
    }

    fun insert(values: ContentValues?): Uri? {
        return resolver.insert(uri, values!!)
    }

        fun query(
            cancellationSignal: CancellationSignal?,
            selection: String?,
            selectionArgs: Array<String?>?,
            sortOrder: String?
        ): Cursor? {
        return resolver.query(
            uri, null, selection, selectionArgs, sortOrder,
            cancellationSignal
        )
    }

    fun delete(where: String?, selectionArgs: Array<String?>?): Int {
        return resolver.delete(uri, where, selectionArgs)
    }

    fun update(
        values: ContentValues?,
        where: String?,
        selectionArgs: Array<String?>?
    ): Int {
        return resolver.update(uri, values, where, selectionArgs)
    }

    /**
     * Convert cursor to your own object
     */
    interface Converter<T> {
        fun convert(cursor: Cursor): T
    }

    fun <T> queryAndConvert(
        cancellationSignal: CancellationSignal?,
        selection: String?,
        selectionArgs: Array<String?>?,
        sortOrder: String?,
        converter: Converter<T>
    ): List<T> {
        val cursor = query(cancellationSignal, selection, selectionArgs, sortOrder)
            ?: return ArrayList(0)
        val list = ArrayList<T>()
        while (cursor.moveToNext()) {
            list.add(converter.convert(cursor))
        }
        cursor.close()
        return list
    }
}
