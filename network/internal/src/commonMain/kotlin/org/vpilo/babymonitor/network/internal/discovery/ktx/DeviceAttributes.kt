package org.vpilo.babymonitor.network.internal.discovery.ktx

// Change every time attributes change in a toDeviceOrNull() or toAttributes()!
internal const val DEVICE_ATTRIBUTE_SCHEMA_VERSION = "1"

// Names (actually all mDNS fields) must be < 256 chars.
internal const val DEVICE_ATTRIBUTE_NAME = "name"
internal const val DEVICE_ATTRIBUTE_TYPE = "type"
internal const val DEVICE_ATTRIBUTE_VERSION = "ver"

internal const val DEVICE_TYPE_CLIENT = "client"
internal const val DEVICE_TYPE_SERVER = "server"
