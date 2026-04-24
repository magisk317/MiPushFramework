package io.github.magisk317.mipush.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.magisk317.mipush.navigation.PushNavigatorImpl
import io.github.magisk317.mipush.runtime.core.navigation.PushNavigator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationModule {

    @Binds
    @Singleton
    abstract fun bindPushNavigator(
        pushNavigatorImpl: PushNavigatorImpl
    ): PushNavigator
}
