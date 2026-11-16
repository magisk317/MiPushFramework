package io.github.magisk317.mipush.runtime.android

/** Serializes package registration mutations and the final outbound send with local revocation. */
object PackageRegistrationGuard {
    private class PackageState {
        var policyVersion: Long = 0L
    }

    private val states = java.util.concurrent.ConcurrentHashMap<String, PackageState>()

    fun <T> withPackage(packageName: String, userId: Int, block: () -> T): T {
        return synchronized(state(packageName, userId)) { block() }
    }

    /** Distinguishes a later explicit policy choice even when it writes the same value. */
    fun policyVersion(packageName: String, userId: Int): Long =
        withPackage(packageName, userId) { state(packageName, userId).policyVersion }

    fun recordPolicyMutation(packageName: String, userId: Int) {
        withPackage(packageName, userId) { state(packageName, userId).policyVersion += 1L }
    }

    private fun state(packageName: String, userId: Int): PackageState {
        require(userId >= 0)
        return states.computeIfAbsent("$userId:$packageName") { PackageState() }
    }
}
