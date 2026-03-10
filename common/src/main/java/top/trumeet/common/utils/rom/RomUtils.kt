package top.trumeet.common.utils.rom

import androidx.annotation.IntDef
import androidx.annotation.RestrictTo
import top.trumeet.common.utils.rom.h2os.H2OSChecker
import top.trumeet.common.utils.rom.miui.MiuiChecker

/**
 * Created by Trumeet on 2018/4/22.
 */
object RomUtils {
    // AOSP 及类原生（除 CM / Los）
    const val ROM_AOSP = 0
    // MIUI 官方（国际/中国版）、官改
    const val ROM_MIUI = 1
    // 氢 OS
    const val ROM_H2OS = 2
    // CyanogenMod
    const val ROM_CYANOGEN_MOD = 3
    // LineageOS
    const val ROM_LINEAGE_OS = 4
    // 未知（没有这个 case）
    const val ROM_UNKNOWN = -1

    @IntDef(
        ROM_UNKNOWN,
        ROM_AOSP,
        ROM_MIUI,
        ROM_H2OS,
        ROM_CYANOGEN_MOD,
        ROM_LINEAGE_OS
    )
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Retention(AnnotationRetention.SOURCE)
    annotation class RomType

    /**
     * 判断 ROM
     */
    @JvmStatic
    @RomType
    fun getOs(): Int {
        // 先看看是不是厂商 ROM。因为类原生都类似。
        if (MiuiChecker().check()) {
            return ROM_MIUI
        }
        if (H2OSChecker().check()) {
            return ROM_H2OS
        }
        if (CyanogenModChecker().check()) {
            return ROM_CYANOGEN_MOD
        }
        if (LineageOSChecker().check()) {
            return ROM_LINEAGE_OS
        }
        return ROM_UNKNOWN
    }

}
