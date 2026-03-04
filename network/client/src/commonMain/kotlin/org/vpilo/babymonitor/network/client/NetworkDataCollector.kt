package org.vpilo.babymonitor.network.client

import org.vpilo.babymonitor.model.MutableFrameFlow
import org.vpilo.babymonitor.model.MutableSampleFlow
import org.vpilo.babymonitor.model.makeMutableFrameFlow
import org.vpilo.babymonitor.model.makeMutableSampleFlow

internal object NetworkDataCollector {
    val frameCollector: MutableFrameFlow = makeMutableFrameFlow()

    val sampleCollector: MutableSampleFlow = makeMutableSampleFlow()
}
