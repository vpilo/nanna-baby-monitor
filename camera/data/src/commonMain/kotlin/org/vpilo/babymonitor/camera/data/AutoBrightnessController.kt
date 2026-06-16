package org.vpilo.babymonitor.camera.data

import kotlin.math.ln

/**
 * Maps measured frame luminance to a smoothed brightness gain for low-light boost.
 *
 * The gain is applied downstream as a shadow-lift gamma (out = in^(1/gain)): a gain of
 * 1.0 is identity, higher gains lift shadows while preserving highlights. Metering is
 * open-loop on the raw (pre-gain) luminance to avoid feedback oscillation.
 */
internal class AutoBrightnessController {
    @Volatile
    private var isEnabled: Boolean = true

    private var smoothedGain: Float = 1f

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    /**
     * Feed the latest raw average luminance (0..1) and advance the smoothed gain.
     * @return the new smoothed gain.
     */
    fun update(rawLuma: Float): Float {
        val target = if (isEnabled) targetGainFor(rawLuma) else 1f
        smoothedGain += (target - smoothedGain) * ALPHA
        return smoothedGain
    }

    // Choose g so that luma^(1/g) ~= TARGET_LUMA, i.e. g = ln(luma) / ln(TARGET_LUMA).
    private fun targetGainFor(luma: Float): Float {
        if (luma <= 0f || luma >= TARGET_LUMA) return 1f
        return (ln(luma) / ln(TARGET_LUMA)).coerceIn(1f, MAX_GAIN)
    }

    private companion object {
        private const val TARGET_LUMA = 0.4f
        private const val MAX_GAIN = 3f
        private const val ALPHA = 0.1f
    }
}
