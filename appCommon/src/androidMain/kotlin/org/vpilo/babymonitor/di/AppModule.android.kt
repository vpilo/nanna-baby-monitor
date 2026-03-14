package org.vpilo.babymonitor.di

import org.koin.core.module.Module
import org.vpilo.babymonitor.android.service.di.androidServiceKoinModule

actual val appPlatformModule: Module = androidServiceKoinModule
