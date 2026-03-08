package org.vpilo.babymonitor.model

/**
 * Repository for raw camera frames.
 *
 * This repository provides uncompressed camera frames.
 */
interface CameraFrameRepository {
    val frames: CameraFrameFlow
}
