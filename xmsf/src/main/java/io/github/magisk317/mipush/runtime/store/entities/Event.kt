package io.github.magisk317.mipush.runtime.store.entities

import android.text.TextUtils
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.magisk317.mipush.platform.support.XMPushUtils
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.common.utils.Utils
import kotlin.annotation.AnnotationRetention.SOURCE

/**
 * Created by Trumeet on 2017/8/26.
 * App event model
 * @author Trumeet
 */
@Entity(tableName = "EVENT", indices = [Index(value = ["pkg"]), Index(value = ["date"])])
class Event {

    @get:Ignore
    val container: XmPushActionContainer?
        get() = payload?.let { XMPushUtils.packToContainer(it) }

    @androidx.annotation.IntDef(
        Type.Notification,
        Type.Command,
        Type.AckMessage,
        Type.Registration,
        Type.MultiConnectionBroadcast,
        Type.MultiConnectionResult,
        Type.UnRegistration,
        Type.ReportFeedback,
        Type.SetConfig,
        Type.Subscription,
        Type.UnSubscription,
        Type.RegistrationResult,
        Type.SendMessage
    )
    @Retention(SOURCE)
    @Target(
        AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.TYPE,
        AnnotationTarget.FIELD,
        AnnotationTarget.FUNCTION
    )
    annotation class Type {
        companion object {
            // Same to com.xiaomi.xmpush.thrift.ActionType
            const val Subscription = 3
            const val UnSubscription = 4
            const val SendMessage = 0
            const val AckMessage = 6
            const val SetConfig = 7
            const val ReportFeedback = 8
            const val Notification = 9
            const val Command = 10
            const val MultiConnectionBroadcast = 11
            const val MultiConnectionResult = 12

            const val Registration = 2
            const val UnRegistration = 20

            // Custom
            const val RegistrationResult = 21
        }
    }

    @androidx.annotation.IntDef(ResultType.OK, ResultType.DENY_DISABLED, ResultType.DENY_USER)
    @Retention(SOURCE)
    @Target(
        AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.TYPE,
        AnnotationTarget.FIELD,
        AnnotationTarget.FUNCTION
    )
    annotation class ResultType {
        companion object {
            const val OK = 0
            const val DENY_DISABLED = 1
            const val DENY_USER = 2
        }
    }

    @PrimaryKey(autoGenerate = true)
    var id: Long? = null

    @ColumnInfo(name = "pkg")
    var pkg: String = ""

    @ColumnInfo(name = "type")
    @Type
    var type: Int = 0

    @ColumnInfo(name = "date")
    var date: Long = 0

    @ColumnInfo(name = "result")
    @ResultType
    var result: Int = 0

    @ColumnInfo(name = "dev_info")
    var info: String? = null

    @ColumnInfo(name = "payload")
    var payload: ByteArray? = null

    @ColumnInfo(name = "reg_sec")
    var regSec: String? = null
        get() = if (TextUtils.isEmpty(field)) {
            Utils.getRegSec(pkg)
        } else {
            field
        }

    @Ignore
    constructor(
        id: Long?,
        pkg: String,
        type: Int,
        date: Long,
        result: Int,
        info: String?,
        payload: ByteArray?,
        regSec: String?
    ) {
        this.id = id
        this.pkg = pkg
        this.type = type
        this.date = date
        this.result = result
        this.info = info
        this.payload = payload
        this.regSec = regSec
    }

    constructor()
}
