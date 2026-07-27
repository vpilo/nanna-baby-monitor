package org.vpilo.babymonitor.data.device

import kotlinx.coroutines.flow.Flow

internal expect fun getSignalLevelFlow(): Flow<Int>
