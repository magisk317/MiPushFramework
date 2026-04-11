package io.github.magisk317.mipush.common.utils.rom

class CyanogenModChecker : RomChecker {
    override fun check(): Boolean {
        return try {
            println("Class: " + Class.forName("org.cyanogenmod.platform.internal.CMSystemServer"))
            true
        } catch (ignored: ClassNotFoundException) {
            false
        }
    }
}
