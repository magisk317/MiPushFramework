package com.xiaomi.mipush.sdk.aidl

import android.graphics.Bitmap
import android.os.Build
import android.os.Parcel
import android.os.Parcelable

/**
 * Result returned by a target application's MiPush extension service.
 *
 * Defaults and Parcel order match stock XMSF 7.4.67-C. The old runtime skipped this result contract
 * entirely and always posted the unmodified notification.
 */
class RemoteNotificationContent(
    val isShowNotification: Boolean = true,
    val title: String? = "",
    val body: String? = "",
    val image: Bitmap? = null,
    val badgeOperateType: Int = -1,
    val badgeNum: Int = -1,
    val clickType: Int = -1,
    val clickUrl: String? = "",
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        isShowNotification = parcel.readByte().toInt() != 0,
        title = parcel.readString(),
        body = parcel.readString(),
        image = parcel.readBitmapCompat(),
        badgeOperateType = parcel.readInt(),
        badgeNum = parcel.readInt(),
        clickType = parcel.readInt(),
        clickUrl = parcel.readString(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByte(if (isShowNotification) 1 else 0)
        parcel.writeString(title)
        parcel.writeString(body)
        parcel.writeParcelable(image, flags)
        parcel.writeInt(badgeOperateType)
        parcel.writeInt(badgeNum)
        parcel.writeInt(clickType)
        parcel.writeString(clickUrl)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<RemoteNotificationContent> =
            object : Parcelable.Creator<RemoteNotificationContent> {
                override fun createFromParcel(parcel: Parcel) = RemoteNotificationContent(parcel)

                override fun newArray(size: Int): Array<RemoteNotificationContent?> = arrayOfNulls(size)
            }
    }
}

private fun Parcel.readBitmapCompat(): Bitmap? {
    return if (Build.VERSION.SDK_INT >= 33) {
        readParcelable(Bitmap::class.java.classLoader, Bitmap::class.java)
    } else {
        @Suppress("DEPRECATION")
        readParcelable(Bitmap::class.java.classLoader)
    }
}
