package top.trumeet.common.utils.rom.h2os

import top.trumeet.common.utils.rom.RomChecker

class H2OSChecker : RomChecker {
    override fun check(): Boolean {
        return try {
            // 可能是只有 H2OS 才有的类（
            val opFeatures = Class.forName("com.oneplus.sdk.utils.OpFeatures")
            println(opFeatures)
            true
        } catch (e: Throwable) {
            false
        }
    }
}
