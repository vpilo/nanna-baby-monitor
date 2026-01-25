package org.vpilo.babymonitor.camera.data.ktx

import java.awt.image.BufferedImage

internal val BufferedImage.sizes: String
    get() = "${this.width}x${this.height}"
