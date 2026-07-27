package org.vpilo.babymonitor.data.device

import kotlinx.coroutines.flow.Flow

internal expect fun getIsInternetAvailableFlow(): Flow<Boolean>
