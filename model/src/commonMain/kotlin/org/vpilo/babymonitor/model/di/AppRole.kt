package org.vpilo.babymonitor.model.di

import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.QualifierValue

enum class AppRole(override val value: QualifierValue) : Qualifier {
    CAMERA("camera"),
    MONITOR("monitor"),
}
