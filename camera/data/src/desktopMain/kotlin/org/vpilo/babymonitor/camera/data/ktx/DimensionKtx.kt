package org.vpilo.babymonitor.camera.data.ktx

import java.awt.Dimension

internal val Dimension.sizes: String
    get() = "${this.width}x${this.height}"
