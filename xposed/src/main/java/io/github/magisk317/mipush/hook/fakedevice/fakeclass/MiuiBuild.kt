package miui.os

object Build {
    @JvmField
    val IS_STABLE_VERSION: Boolean = true

    @JvmField
    val IS_DEVELOPMENT_VERSION: Boolean = false

    @JvmField
    val IS_ALPHA_BUILD: Boolean = false

    @JvmField
    val IS_GLOBAL_BUILD: Boolean = false

    @JvmField
    val IS_INTERNATIONAL_BUILD: Boolean = false

    @JvmStatic
    fun getRegion(): String = "CN"

    @JvmStatic
    fun getCustVariant(): String = "cn_chinatelecom"
}
