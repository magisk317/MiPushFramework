@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package top.trumeet.common.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import androidx.annotation.NonNull
import androidx.annotation.Nullable

/**
 * Created by Trumeet on 2017/12/29.
 * 操作 Provider 的 Util
 */
class DatabaseUtils(@NonNull private val uri: Uri, @NonNull private val resolver: ContentResolver) {

    companion object {
        const val KEY_ID = "_id"

        @JvmStatic
        @NonNull
        fun order(@NonNull column: String, @NonNull order: String): String {
            return "$column $order"
        }

        @JvmStatic
        @NonNull
        fun limitAndOffset(@Nullable limit: Int?, @Nullable offset: Int?): String {
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
        @Nullable cancellationSignal: CancellationSignal?,
        @Nullable selection: String?,
        @Nullable selectionArgs: Array<String?>?,
        @Nullable sortOrder: String?
    ): Cursor? {
        return resolver.query(
            uri, null, selection, selectionArgs, sortOrder,
            cancellationSignal
        )
    }

    fun delete(@Nullable where: String?, @Nullable selectionArgs: Array<String?>?): Int {
        return resolver.delete(uri, where, selectionArgs)
    }

    fun update(
        @Nullable values: ContentValues?,
        @Nullable where: String?,
        @Nullable selectionArgs: Array<String?>?
    ): Int {
        return resolver.update(uri, values, where, selectionArgs)
    }

    /**
     * Convert cursor to your own object
     */
    interface Converter<T> {
        @NonNull
        fun convert(@NonNull cursor: Cursor): T
    }

    @NonNull
    fun <T> queryAndConvert(
        @Nullable cancellationSignal: CancellationSignal?,
        @Nullable selection: String?,
        @Nullable selectionArgs: Array<String?>?,
        @Nullable sortOrder: String?,
        @NonNull converter: Converter<T>
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
