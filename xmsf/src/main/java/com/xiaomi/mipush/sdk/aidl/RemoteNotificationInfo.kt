package com.xiaomi.mipush.sdk.aidl

import android.os.Parcel
import android.os.Parcelable

/**
 * Wire model used by the target application's MiPush extension service.
 *
 * Stock XMSF 7.4.67-C writes these ten fields in this exact order. MiPushFramework previously had
 * no extension Binder path, so compatible target services could never inspect or alter a push.
 */
class RemoteNotificationInfo(
    val type: Int = 0,
    val token: String? = null,
    val title: String? = null,
    val body: String? = null,
    val image: String? = null,
    val notifyId: Long = 0L,
    val clickType: Int = 0,
    val clickUrl: String? = null,
    val extraData: String? = null,
    val msgId: String? = null,
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        type = parcel.readInt(),
        token = parcel.readString(),
        title = parcel.readString(),
        body = parcel.readString(),
        image = parcel.readString(),
        notifyId = parcel.readLong(),
        clickType = parcel.readInt(),
        clickUrl = parcel.readString(),
        extraData = parcel.readString(),
        msgId = parcel.readString(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(type)
        parcel.writeString(token)
        parcel.writeString(title)
        parcel.writeString(body)
        parcel.writeString(image)
        parcel.writeLong(notifyId)
        parcel.writeInt(clickType)
        parcel.writeString(clickUrl)
        parcel.writeString(extraData)
        parcel.writeString(msgId)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<RemoteNotificationInfo> =
            object : Parcelable.Creator<RemoteNotificationInfo> {
                override fun createFromParcel(parcel: Parcel) = RemoteNotificationInfo(parcel)

                override fun newArray(size: Int): Array<RemoteNotificationInfo?> = arrayOfNulls(size)
            }
    }
}
