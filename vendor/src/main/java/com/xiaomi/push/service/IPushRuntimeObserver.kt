package com.xiaomi.push.service

/**
 * Interface defined for the foundation layer to notify the product layer.
 * This acts as the bridge for all runtime policy decisions and event reporting.
 *
 * The surface is organized by event domain: transport/lifecycle, registration &
 * channel bookkeeping, message processing, and policy planning. The aggregate
 * stays the single contract the product layer implements, so call sites and the
 * implementation class keep referring to [IPushRuntimeObserver] only.
 */
interface IPushRuntimeObserver :
    IPushRuntimeConnectionObserver,
    IPushRuntimeRegistrationObserver,
    IPushRuntimeMessageObserver,
    IPushRuntimePolicyObserver
