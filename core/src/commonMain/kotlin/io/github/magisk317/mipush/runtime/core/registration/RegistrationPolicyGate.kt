package io.github.magisk317.mipush.runtime.core.registration

/** Registration authorization is independent of the current server registration status. */
enum class RegistrationPolicyDecision { Allow, Ask, Deny }

object RegistrationPolicyGate {
    private const val ASK = 0
    private const val ALLOW = 2
    private const val ALLOW_ONCE = -1

    fun decide(type: Int, blocked: Boolean): RegistrationPolicyDecision = when {
        blocked -> RegistrationPolicyDecision.Deny
        type == ALLOW || type == ALLOW_ONCE -> RegistrationPolicyDecision.Allow
        type == ASK -> RegistrationPolicyDecision.Ask
        else -> RegistrationPolicyDecision.Deny
    }
}
