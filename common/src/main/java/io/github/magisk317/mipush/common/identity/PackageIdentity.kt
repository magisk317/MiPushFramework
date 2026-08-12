package io.github.magisk317.mipush.common.identity

/** Stable package identity inside one Android user/profile. */
data class PackageIdentity(
    val userId: Int,
    val packageName: String,
) {
    init {
        require(userId >= 0) { "userId must be non-negative" }
        require(packageName.isNotBlank()) { "packageName must not be blank" }
    }
}
