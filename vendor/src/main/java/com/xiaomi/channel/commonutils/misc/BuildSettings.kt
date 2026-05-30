package com.xiaomi.channel.commonutils.misc

object BuildSettings {
    const val BETA = "BETA"
    const val DEBUG = "DEBUG"
    const val LOGABLE = "LOGABLE"
    const val TEST = "TEST"
    const val YY = "YY"
    const val Official = 1
    const val SandBox = 2
    const val OneBox = 3

    val ReleaseChannel: String
    val IsDefaultChannel: Boolean
    val IsDebugBuild: Boolean
    val IsLogableBuild: Boolean
    val IsForYYBuild: Boolean
    var IsTestBuild: Boolean = false
    val IsBetaBuild: Boolean
    val IsRCBuild: Boolean

    private var envType: Int

    init {
        val str = if (DebugSwitch.sDebugServerHost) "ONEBOX" else "@SHIP.TO.2A2FE0D7@"
        ReleaseChannel = str
        val zContains = str.contains("2A2FE0D7")
        IsDefaultChannel = zContains
        IsDebugBuild = zContains || DEBUG.equals(str, ignoreCase = true)
        IsLogableBuild = LOGABLE.equals(str, ignoreCase = true)
        IsForYYBuild = str.contains(YY)
        IsTestBuild = str.equals(TEST, ignoreCase = true)
        IsBetaBuild = BETA.equals(str, ignoreCase = true)
        IsRCBuild = str.startsWith("RC")
        envType = when {
            str.equals("SANDBOX", ignoreCase = true) -> 2
            str.equals("ONEBOX", ignoreCase = true) -> 3
            else -> 1
        }
    }

    @JvmStatic
    fun IsOneBoxBuild(): Boolean = envType == 3

    @JvmStatic
    fun IsSandBoxBuild(): Boolean = envType == 2

    @JvmStatic
    fun getEnvType(): Int = envType

    @JvmStatic
    fun setEnvType(type: Int) {
        envType = type
    }
}
