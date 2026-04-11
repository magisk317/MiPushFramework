package io.github.magisk317.mipush.runtime.store.entities

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.IntDef
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import io.github.magisk317.mipush.common.compat.PackageManagerCompatBridge
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date
import kotlin.annotation.AnnotationRetention.SOURCE

/**
 * Created by Trumeet on 2017/8/26.
 * A registered application. Can 3 types: [Type.ALLOW], [Type.DENY]
 * or [Type.ASK].
 * Default is Ask: show a dialog to request push permission.
 * It will auto create using ask type when application register push.
 *
 * This entity will also save application's push permissions.
 *
 * @author Trumeet
 */
@Entity(
    tableName = "REGISTERED_APPLICATION",
    indices = [Index(value = ["pkg"], unique = true)]
)
class RegisteredApplication : Parcelable {

    @IntDef(Type.ASK, Type.ALLOW, Type.DENY, Type.ALLOW_ONCE)
    @Retention(SOURCE)
    @Target(
        AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.TYPE,
        AnnotationTarget.FIELD,
        AnnotationTarget.FUNCTION
    )
    annotation class Type {
        companion object {
            const val ASK = 0
            const val ALLOW = 2
            const val DENY = 3
            const val ALLOW_ONCE = -1
        }
    }

    @IntDef(RegisteredType.NotRegistered, RegisteredType.Registered, RegisteredType.Unregistered)
    @Retention(SOURCE)
    @Target(
        AnnotationTarget.VALUE_PARAMETER,
        AnnotationTarget.TYPE,
        AnnotationTarget.FIELD,
        AnnotationTarget.FUNCTION
    )
    annotation class RegisteredType {
        companion object {
            const val NotRegistered = 0
            const val Registered = 1
            const val Unregistered = 2
        }
    }

    @PrimaryKey(autoGenerate = true)
    var id: Long? = null

    @ColumnInfo(name = "pkg")
    var packageName: String = ""

    @Type
    @ColumnInfo(name = "type")
    var type: Int = Type.ASK

    @ColumnInfo(name = "notification_on_register")
    var notificationOnRegister: Boolean = false

    @RegisteredType
    @ColumnInfo(name = "registered_type")
    var registeredType: Int = RegisteredType.NotRegistered

    @Ignore
    @JvmField
    var existServices: Boolean = false

    @ColumnInfo(name = "app_name")
    @JvmField
    var appName: String = ""

    @Ignore
    @JvmField
    var appNamePinYin: String = ""

    @Ignore
    @JvmField
    var lastReceiveTime: Date = Date(0)

    @Ignore
    @JvmField
    var registrationTypeReason: String = "unknown"

    @Ignore
    constructor(
        id: Long?,
        packageName: String,
        type: Int,
        notificationOnRegister: Boolean,
        registeredType: Int,
        appName: String
    ) {
        this.id = id
        this.packageName = packageName
        this.type = type
        this.notificationOnRegister = notificationOnRegister
        this.registeredType = registeredType
        this.appName = appName
    }

    constructor()

    @Ignore
    protected constructor(parcel: Parcel) {
        id = if (parcel.readByte().toInt() == 0) null else parcel.readLong()
        packageName = parcel.readString() ?: ""
        type = parcel.readInt()
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        if (id == null) {
            parcel.writeByte(0)
        } else {
            parcel.writeByte(1)
            parcel.writeLong(id!!)
        }
        parcel.writeString(packageName)
        parcel.writeInt(type)
    }

    @NonNull
    fun getIcon(context: Context): Drawable {
        val pm = context.packageManager
        return try {
            PackageManagerCompatBridge.getApplicationInfo(
                pm,
                packageName,
                PackageManager.MATCH_UNINSTALLED_PACKAGES
            ).loadIcon(pm)
        } catch (_: PackageManager.NameNotFoundException) {
            ContextCompat.getDrawable(context, android.R.mipmap.sym_def_app_icon)!!
        } catch (_: Resources.NotFoundException) {
            ContextCompat.getDrawable(context, android.R.mipmap.sym_def_app_icon)!!
        }
    }

    fun getUid(context: Context): Int {
        return try {
            PackageManagerCompatBridge.getApplicationInfo(
                context.packageManager,
                packageName,
                PackageManager.MATCH_UNINSTALLED_PACKAGES
            ).uid
        } catch (_: PackageManager.NameNotFoundException) {
            -1
        }
    }

    companion object CREATOR : Parcelable.Creator<RegisteredApplication> {
        override fun createFromParcel(parcel: Parcel): RegisteredApplication = RegisteredApplication(parcel)
        override fun newArray(size: Int): Array<RegisteredApplication?> = arrayOfNulls(size)
    }
}
