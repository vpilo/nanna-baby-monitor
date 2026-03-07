package org.vpilo.babymonitor.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.vpilo.babymonitor.android.service.di.androidServiceKoinModule

actual val appPlatformModule: Module = androidServiceKoinModule
