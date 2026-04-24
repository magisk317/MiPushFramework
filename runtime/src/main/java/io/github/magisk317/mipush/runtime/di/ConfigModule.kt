package io.github.magisk317.mipush.runtime.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.magisk317.mipush.common.configurations.IConfigProvider
import io.github.magisk317.mipush.runtime.app.ConfigCenter
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ConfigModule {

    @Binds
    @Singleton
    abstract fun bindConfigProvider(configCenter: ConfigCenter): IConfigProvider
}
