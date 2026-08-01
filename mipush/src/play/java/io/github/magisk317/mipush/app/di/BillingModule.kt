package io.github.magisk317.mipush.app.di

import io.github.magisk317.mipush.app.billing.PlayBillingProvider
import io.github.magisk317.mipush.manager.billing.BillingProvider
import io.github.magisk317.uikit.billing.BillingInitializer
import io.github.magisk317.uikit.billing.BillingManager
import io.github.magisk317.uikit.shell.AppInitializer
import org.koin.dsl.bind
import org.koin.dsl.module

val billingModule = module {
    single { BillingManager(get()) }
    single<BillingProvider> { PlayBillingProvider(get()) }
    single { BillingInitializer(get()) } bind AppInitializer::class
}
