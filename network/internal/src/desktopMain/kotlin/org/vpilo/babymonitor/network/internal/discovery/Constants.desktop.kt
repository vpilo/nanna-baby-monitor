package org.vpilo.babymonitor.network.internal.discovery

import org.vpilo.babymonitor.network.model.Constants

// JmDNS requires the ".local." suffix
internal const val DISCOVERY_DESKTOP_SERVICE_TYPE = "_${Constants.DISCOVERY_SERVICE_TYPE}._tcp.local."
