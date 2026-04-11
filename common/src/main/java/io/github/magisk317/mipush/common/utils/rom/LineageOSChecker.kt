package io.github.magisk317.mipush.common.utils.rom

class LineageOSChecker : RomChecker {
    override fun check(): Boolean {
        return try {
            println("Class: " + Class.forName("org.lineageos.platform.internal.LineageSystemServer"))
            true
        } catch (ignored: ClassNotFoundException) {
            false
        }
    }
}
