package org.vpilo.babymonitor.model.repository

import kotlin.uuid.Uuid

typealias DeviceId = Uuid

fun String.toDeviceId(): DeviceId = Uuid.parse(this)

fun String.toDeviceIdOrNull(): DeviceId? = Uuid.parseOrNull(this)
