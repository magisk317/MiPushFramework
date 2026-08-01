package io.github.magisk317.mipush.app.di

import io.github.magisk317.mipush.manager.billing.BillingProvider
import io.github.magisk317.mipush.manager.billing.NoOpBillingProvider
import org.koin.dsl.module

val billingModule = module {
    single<BillingProvider> { NoOpBillingProvider() }
}
