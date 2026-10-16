package org.vpilo.babymonitor.network.internal.discovery

import org.vpilo.babymonitor.network.model.Constants

// Android NSD requires a "_tcp." suffix.
internal const val DISCOVERY_ANDROID_SERVICE_TYPE = "_${Constants.DISCOVERY_SERVICE_TYPE}._tcp."
