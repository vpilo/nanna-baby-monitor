package org.vpilo.babymonitor.presentation.preview

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.vpilo.babymonitor.presentation.AppPreviewTheme

fun makePlaceholderCameraFrame(
    width: Int = 1280,
    height: Int = 720,
    rotation: Int = 0,
    isDarkMode: Boolean = false,
): ImageBitmap {
    require(width > 0) { "width must be > 0" }
    require(height > 0) { "height must be > 0" }

    val image = ImageBitmap(width, height)
    val canvas = Canvas(image)
    val palette = if (isDarkMode) DarkPalette else LightPalette

    // Logical drawing space is the upright (display) orientation, so the scene is laid out the same
    // way a viewer sees it after rotating the frame. The buffer itself stays width x height.
    val shouldRotateCanvas = rotation == 90 || rotation == 270
    val w = if (shouldRotateCanvas) height.toFloat() else width.toFloat()
    val h = if (shouldRotateCanvas) width.toFloat() else height.toFloat()

    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(width.toFloat(), height.toFloat()),
    ) {
        // Bake the inverse rotation around the buffer center so a consumer applying +rotation
        // recovers the upright scene, centered and filling the buffer.
        translate(left = width / 2f, top = height / 2f) {
            rotate(degrees = -rotation.toFloat(), pivot = Offset.Zero) {
                translate(left = -w / 2f, top = -h / 2f) {
                    drawPlaceholderScene(w, h, palette)
                }
            }
        }
    }

    return image
}

@Suppress("LongMethod")
private fun DrawScope.drawPlaceholderScene(
    w: Float,
    h: Float,
    palette: PlaceholderPalette,
) {
    val unit = minOf(w, h)
    val horizon = h * 0.62f
    val cribLeft = w * 0.12f
    val cribTop = h * 0.48f
    val cribWidth = w * 0.46f
    val cribHeight = h * 0.22f
    val railStroke = maxOf(unit * 0.010f, 1f)
    val thinStroke = maxOf(unit * 0.004f, 1f)

    // Back wall / upper room tone
    drawRect(
        brush =
            Brush.verticalGradient(
                colors = listOf(palette.wallTop, palette.wallBottom),
                startY = 0f,
                endY = horizon,
            ),
        size = Size(w, horizon),
    )

    // Floor / lower room tone
    drawRect(
        brush =
            Brush.verticalGradient(
                colors = listOf(palette.floorTop, palette.floorBottom),
                startY = horizon,
                endY = h,
            ),
        topLeft = Offset(0f, horizon),
        size = Size(w, h - horizon),
    )

    // Window in the background
    val windowLeft = w * 0.64f
    val windowTop = h * 0.12f
    val windowWidth = w * 0.24f
    val windowHeight = h * 0.24f
    val windowPadding = unit * 0.018f
    val windowRadius = CornerRadius(unit * 0.025f, unit * 0.025f)

    drawRoundRect(
        color = palette.windowFrame,
        topLeft = Offset(windowLeft, windowTop),
        size = Size(windowWidth, windowHeight),
        cornerRadius = windowRadius,
    )

    drawRoundRect(
        brush =
            Brush.verticalGradient(
                colors = listOf(palette.windowGlowTop, palette.windowGlowBottom),
            ),
        topLeft = Offset(windowLeft + windowPadding, windowTop + windowPadding),
        size = Size(windowWidth - 2 * windowPadding, windowHeight - 2 * windowPadding),
        cornerRadius = CornerRadius(unit * 0.018f, unit * 0.018f),
    )

    // Sun / moon
    drawCircle(
        color = palette.windowOrb,
        radius = unit * 0.035f,
        center = Offset(windowLeft + windowWidth * 0.72f, windowTop + windowHeight * 0.30f),
    )

    // Window crossbars
    drawLine(
        color = palette.windowFrame.copy(alpha = 0.7f),
        start = Offset(windowLeft + windowWidth / 2f, windowTop + windowPadding),
        end = Offset(windowLeft + windowWidth / 2f, windowTop + windowHeight - windowPadding),
        strokeWidth = thinStroke,
    )
    drawLine(
        color = palette.windowFrame.copy(alpha = 0.7f),
        start = Offset(windowLeft + windowPadding, windowTop + windowHeight / 2f),
        end = Offset(windowLeft + windowWidth - windowPadding, windowTop + windowHeight / 2f),
        strokeWidth = thinStroke,
    )

    // Soft light cone from window
    drawRect(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        palette.lightBloom,
                        Color.Transparent,
                    ),
                center = Offset(windowLeft + windowWidth * 0.45f, windowTop + windowHeight * 0.65f),
                radius = w * 0.42f,
            ),
        size = Size(w, h),
    )

    // Mattress / blanket mass
    drawRoundRect(
        color = palette.mattress,
        topLeft = Offset(cribLeft + w * 0.03f, cribTop + h * 0.11f),
        size = Size(cribWidth - w * 0.06f, h * 0.06f),
        cornerRadius = CornerRadius(unit * 0.018f, unit * 0.018f),
    )

    // Crib rails
    drawLine(
        color = palette.crib,
        start = Offset(cribLeft, cribTop),
        end = Offset(cribLeft + cribWidth, cribTop),
        strokeWidth = railStroke,
    )
    drawLine(
        color = palette.crib,
        start = Offset(cribLeft, cribTop + cribHeight),
        end = Offset(cribLeft + cribWidth, cribTop + cribHeight),
        strokeWidth = railStroke,
    )
    drawLine(
        color = palette.crib,
        start = Offset(cribLeft, cribTop),
        end = Offset(cribLeft, cribTop + cribHeight),
        strokeWidth = railStroke,
    )
    drawLine(
        color = palette.crib,
        start = Offset(cribLeft + cribWidth, cribTop),
        end = Offset(cribLeft + cribWidth, cribTop + cribHeight),
        strokeWidth = railStroke,
    )

    val barCount = 9
    repeat(barCount) { index ->
        val x = cribLeft + (cribWidth / (barCount + 1)) * (index + 1)
        drawLine(
            color = palette.crib.copy(alpha = 0.95f),
            start = Offset(x, cribTop + h * 0.015f),
            end = Offset(x, cribTop + cribHeight - h * 0.015f),
            strokeWidth = thinStroke,
        )
    }

    // Hanging mobile
    val mobileAnchorX = cribLeft + cribWidth * 0.72f
    val mobileAnchorY = cribTop - h * 0.10f
    val mobileBarY = cribTop + h * 0.02f

    drawLine(
        color = palette.mobile,
        start = Offset(mobileAnchorX, mobileAnchorY),
        end = Offset(mobileAnchorX, mobileBarY),
        strokeWidth = thinStroke,
    )
    drawLine(
        color = palette.mobile,
        start = Offset(mobileAnchorX - w * 0.07f, mobileBarY),
        end = Offset(mobileAnchorX + w * 0.07f, mobileBarY),
        strokeWidth = thinStroke,
    )

    val hangingY = cribTop + h * 0.09f
    drawLine(
        color = palette.mobile,
        start = Offset(mobileAnchorX - w * 0.05f, mobileBarY),
        end = Offset(mobileAnchorX - w * 0.05f, hangingY),
        strokeWidth = thinStroke,
    )
    drawLine(
        color = palette.mobile,
        start = Offset(mobileAnchorX, mobileBarY),
        end = Offset(mobileAnchorX, hangingY + h * 0.015f),
        strokeWidth = thinStroke,
    )
    drawLine(
        color = palette.mobile,
        start = Offset(mobileAnchorX + w * 0.05f, mobileBarY),
        end = Offset(mobileAnchorX + w * 0.05f, hangingY),
        strokeWidth = thinStroke,
    )

    drawCircle(
        color = palette.mobileAccent1,
        radius = unit * 0.015f,
        center = Offset(mobileAnchorX - w * 0.05f, hangingY + unit * 0.01f),
    )
    drawCircle(
        color = palette.mobileAccent2,
        radius = unit * 0.013f,
        center = Offset(mobileAnchorX, hangingY + h * 0.025f),
    )
    drawCircle(
        color = palette.mobileAccent3,
        radius = unit * 0.014f,
        center = Offset(mobileAnchorX + w * 0.05f, hangingY + unit * 0.01f),
    )

    // Soft foreground blur blob to make it feel less flat
    drawRect(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        palette.foregroundBlur,
                        Color.Transparent,
                    ),
                center = Offset(w * 0.83f, h * 0.78f),
                radius = w * 0.22f,
            ),
        size = Size(w, h),
    )

    // Very subtle camera scan lines
    val scanGap = maxOf(h / 90f, 4f)
    var y = 0f
    while (y < h) {
        drawLine(
            color = palette.scanLine,
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 1f,
        )
        y += scanGap
    }

    // Lens vignette
    drawRect(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.Transparent,
                        Color.Transparent,
                        palette.vignette,
                    ),
                center = Offset(w / 2f, h / 2f),
                radius = maxOf(w, h) * 0.78f,
            ),
        size = Size(w, h),
    )
}

private data class PlaceholderPalette(
    val wallTop: Color,
    val wallBottom: Color,
    val floorTop: Color,
    val floorBottom: Color,
    val windowFrame: Color,
    val windowGlowTop: Color,
    val windowGlowBottom: Color,
    val windowOrb: Color,
    val lightBloom: Color,
    val crib: Color,
    val mattress: Color,
    val mobile: Color,
    val mobileAccent1: Color,
    val mobileAccent2: Color,
    val mobileAccent3: Color,
    val foregroundBlur: Color,
    val scanLine: Color,
    val vignette: Color,
)

private val LightPalette =
    PlaceholderPalette(
        wallTop = Color(0xFFE7EEF3),
        wallBottom = Color(0xFFD9E3EA),
        floorTop = Color(0xFFCBBDAF),
        floorBottom = Color(0xFFB29E8B),
        windowFrame = Color(0xFF9AA7B3),
        windowGlowTop = Color(0xFFBFE3FF),
        windowGlowBottom = Color(0xFFF8F1D3),
        windowOrb = Color(0xFFFFF2B8),
        lightBloom = Color(0x33FFF4CF),
        crib = Color(0xFF8C6F5A),
        mattress = Color(0xFFEFE7DE),
        mobile = Color(0xFF7C8A96),
        mobileAccent1 = Color(0xFFD08C8C),
        mobileAccent2 = Color(0xFF8CB7D0),
        mobileAccent3 = Color(0xFFB9C98A),
        foregroundBlur = Color(0x22FFFFFF),
        scanLine = Color(0x0D000000),
        vignette = Color(0x55000000),
    )

private val DarkPalette =
    PlaceholderPalette(
        wallTop = Color(0xFF1D2430),
        wallBottom = Color(0xFF222C39),
        floorTop = Color(0xFF2E2A2A),
        floorBottom = Color(0xFF211D1D),
        windowFrame = Color(0xFF4E5A66),
        windowGlowTop = Color(0xFF314C74),
        windowGlowBottom = Color(0xFF1B2A3E),
        windowOrb = Color(0xFFF2ECD4),
        lightBloom = Color(0x223D5F8A),
        crib = Color(0xFF9D876F),
        mattress = Color(0xFF5A5D6B),
        mobile = Color(0xFF8895A3),
        mobileAccent1 = Color(0xFF9A7EAF),
        mobileAccent2 = Color(0xFF6C9DB3),
        mobileAccent3 = Color(0xFFA6B07A),
        foregroundBlur = Color(0x18C7D7FF),
        scanLine = Color(0x14FFFFFF),
        vignette = Color(0xAA000000),
    )

@Preview
@Composable
private fun CameraPlaceholderLightPreview() =
    AppPreviewTheme(useDarkTheme = false) {
        Image(
            bitmap = makePlaceholderCameraFrame(isDarkMode = false),
            contentDescription = null,
        )
    }

@Preview
@Composable
private fun CameraPlaceholderDarkPreview() =
    AppPreviewTheme(useDarkTheme = true) {
        Image(
            bitmap = makePlaceholderCameraFrame(isDarkMode = true),
            contentDescription = null,
        )
    }
