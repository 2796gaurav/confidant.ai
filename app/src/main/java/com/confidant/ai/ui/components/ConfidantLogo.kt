package com.confidant.ai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confidant.ai.ui.theme.DeepIndigoMain
import com.confidant.ai.ui.theme.ElectricLimeMain
import com.confidant.ai.ui.theme.FrostWhitePrimary

/**
 * CONFIDANT AI LOGO - "C AI" SIMPLE & POWERFUL
 * 
 * Design Philosophy:
 * - Simple: Just "C AI" text - clear and memorable
 * - Powerful: Bold typography with accent colors
 * - Centered: Perfect alignment and balance
 * - Consistent: Uses app theme colors throughout
 * 
 * Color Scheme:
 * - C: Frost White (Primary text)
 * - AI: Electric Lime (Active intelligence)
 * - Ring: Deep Indigo (Trust & security)
 * - Dot: Electric Lime (Pulsing core)
 */

@Composable
fun ConfidantLogo(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    val textMeasurer = rememberTextMeasurer()
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val canvasWidth = this.size.width
            val canvasHeight = this.size.height
            val centerX = canvasWidth / 2f
            val centerY = canvasHeight / 2f
            
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // BACKGROUND GLOW (Deep Indigo)
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        DeepIndigoMain.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = canvasWidth * 0.5f
                ),
                center = Offset(centerX, centerY),
                radius = canvasWidth * 0.5f
            )
            
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // OUTER RING (Deep Indigo)
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            drawCircle(
                color = DeepIndigoMain.copy(alpha = 0.6f),
                center = Offset(centerX, centerY),
                radius = canvasWidth * 0.42f,
                style = Stroke(width = 2.5f)
            )
            
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // "C" LETTER - Bold and Centered with visible gap
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            val radius = canvasWidth * 0.25f
            val thickness = canvasWidth * 0.08f
            val gapAngle = 70f // degrees - increased for more visible gap
            val innerRadius = radius - thickness
            
            // Calculate start and end points for the gap
            val startAngle = gapAngle / 2
            val endAngle = 360f - gapAngle / 2
            
            // Outer arc start point
            val outerStartX = centerX + radius * kotlin.math.cos(Math.toRadians(startAngle.toDouble())).toFloat()
            val outerStartY = centerY + radius * kotlin.math.sin(Math.toRadians(startAngle.toDouble())).toFloat()
            
            // Outer arc end point
            val outerEndX = centerX + radius * kotlin.math.cos(Math.toRadians(endAngle.toDouble())).toFloat()
            val outerEndY = centerY + radius * kotlin.math.sin(Math.toRadians(endAngle.toDouble())).toFloat()
            
            // Inner arc start point
            val innerStartX = centerX + innerRadius * kotlin.math.cos(Math.toRadians(startAngle.toDouble())).toFloat()
            val innerStartY = centerY + innerRadius * kotlin.math.sin(Math.toRadians(startAngle.toDouble())).toFloat()
            
            // Inner arc end point
            val innerEndX = centerX + innerRadius * kotlin.math.cos(Math.toRadians(endAngle.toDouble())).toFloat()
            val innerEndY = centerY + innerRadius * kotlin.math.sin(Math.toRadians(endAngle.toDouble())).toFloat()
            
            val cPath = Path().apply {
                // Start at outer arc start point
                moveTo(outerStartX, outerStartY)
                
                // Draw outer arc (clockwise from start to end)
                arcTo(
                    rect = Rect(
                        left = centerX - radius,
                        top = centerY - radius,
                        right = centerX + radius,
                        bottom = centerY + radius
                    ),
                    startAngleDegrees = startAngle,
                    sweepAngleDegrees = 360f - gapAngle,
                    forceMoveTo = false
                )
                
                // Connect to inner arc end point
                lineTo(innerEndX, innerEndY)
                
                // Draw inner arc (counter-clockwise from end to start)
                arcTo(
                    rect = Rect(
                        left = centerX - innerRadius,
                        top = centerY - innerRadius,
                        right = centerX + innerRadius,
                        bottom = centerY + innerRadius
                    ),
                    startAngleDegrees = endAngle,
                    sweepAngleDegrees = -(360f - gapAngle),
                    forceMoveTo = false
                )
                
                // Close the path - this connects inner start to outer start, creating the gap
                close()
            }
            
            drawPath(
                path = cPath,
                color = FrostWhitePrimary.copy(alpha = 0.95f)
            )
            
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // "AI" TEXT - Compact and Modern (Electric Lime)
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            val aiTextStyle = TextStyle(
                color = ElectricLimeMain.copy(alpha = 0.95f),
                fontSize = (canvasWidth * 0.12f).sp,
                fontWeight = FontWeight.Bold
            )
            
            val aiTextLayout = textMeasurer.measure(
                text = "AI",
                style = aiTextStyle
            )
            
            drawText(
                textLayoutResult = aiTextLayout,
                topLeft = Offset(
                    x = centerX - aiTextLayout.size.width / 2f,
                    y = centerY + canvasHeight * 0.28f
                )
            )
            
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // CENTER DOT - Pulsing Core (Electric Lime)
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            val dotRadius = if (animate) canvasWidth * 0.04f * pulseScale else canvasWidth * 0.04f
            val dotAlpha = if (animate) pulseAlpha else 0.9f
            
            // Outer glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ElectricLimeMain.copy(alpha = dotAlpha * 0.4f),
                        ElectricLimeMain.copy(alpha = 0f)
                    ),
                    center = Offset(centerX, centerY),
                    radius = dotRadius * 2.5f
                ),
                center = Offset(centerX, centerY),
                radius = dotRadius * 2.5f
            )
            
            // Core dot
            drawCircle(
                color = ElectricLimeMain.copy(alpha = dotAlpha),
                center = Offset(centerX, centerY),
                radius = dotRadius
            )
        }
    }
}

/**
 * Simplified logo for small sizes (notifications, list items, etc.)
 */
@Composable
fun ConfidantLogoSimple(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tintColor: Color = FrostWhitePrimary
) {
    val textMeasurer = rememberTextMeasurer()
    
    Canvas(modifier = modifier.size(size)) {
        val canvasWidth = this.size.width
        val canvasHeight = this.size.height
        val centerX = canvasWidth / 2f
        val centerY = canvasHeight / 2f
        
        // Simple "C" outline with visible gap
        val radius = canvasWidth * 0.35f
        val thickness = canvasWidth * 0.12f
        val gapAngle = 75f
        val innerRadius = radius - thickness
        
        // Calculate start and end points for the gap
        val startAngle = gapAngle / 2
        val endAngle = 360f - gapAngle / 2
        
        // Outer arc start point
        val outerStartX = centerX + radius * kotlin.math.cos(Math.toRadians(startAngle.toDouble())).toFloat()
        val outerStartY = centerY + radius * kotlin.math.sin(Math.toRadians(startAngle.toDouble())).toFloat()
        
        // Outer arc end point
        val outerEndX = centerX + radius * kotlin.math.cos(Math.toRadians(endAngle.toDouble())).toFloat()
        val outerEndY = centerY + radius * kotlin.math.sin(Math.toRadians(endAngle.toDouble())).toFloat()
        
        // Inner arc start point
        val innerStartX = centerX + innerRadius * kotlin.math.cos(Math.toRadians(startAngle.toDouble())).toFloat()
        val innerStartY = centerY + innerRadius * kotlin.math.sin(Math.toRadians(startAngle.toDouble())).toFloat()
        
        // Inner arc end point
        val innerEndX = centerX + innerRadius * kotlin.math.cos(Math.toRadians(endAngle.toDouble())).toFloat()
        val innerEndY = centerY + innerRadius * kotlin.math.sin(Math.toRadians(endAngle.toDouble())).toFloat()
        
        val cPath = Path().apply {
            // Start at outer arc start point
            moveTo(outerStartX, outerStartY)
            
            // Draw outer arc
            arcTo(
                rect = Rect(
                    left = centerX - radius,
                    top = centerY - radius,
                    right = centerX + radius,
                    bottom = centerY + radius
                ),
                startAngleDegrees = startAngle,
                sweepAngleDegrees = 360f - gapAngle,
                forceMoveTo = false
            )
            
            // Connect to inner arc end point
            lineTo(innerEndX, innerEndY)
            
            // Draw inner arc
            arcTo(
                rect = Rect(
                    left = centerX - innerRadius,
                    top = centerY - innerRadius,
                    right = centerX + innerRadius,
                    bottom = centerY + innerRadius
                ),
                startAngleDegrees = endAngle,
                sweepAngleDegrees = -(360f - gapAngle),
                forceMoveTo = false
            )
            
            // Connect back to start
            lineTo(outerStartX, outerStartY)
        }
        
        drawPath(
            path = cPath,
            color = tintColor
        )
        
        // "AI" text
        val aiTextStyle = TextStyle(
            color = ElectricLimeMain,
            fontSize = (canvasWidth * 0.18f).sp,
            fontWeight = FontWeight.Bold
        )
        
        val aiTextLayout = textMeasurer.measure(
            text = "AI",
            style = aiTextStyle
        )
        
        drawText(
            textLayoutResult = aiTextLayout,
            topLeft = Offset(
                x = centerX - aiTextLayout.size.width / 2f,
                y = centerY + canvasHeight * 0.35f
            )
        )
        
        // Center dot
        drawCircle(
            color = ElectricLimeMain,
            center = Offset(centerX, centerY),
            radius = canvasWidth * 0.06f
        )
    }
}

/**
 * Monochrome version for notifications (Android system requirement)
 */
@Composable
fun ConfidantLogoMonochrome(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    val textMeasurer = rememberTextMeasurer()
    
    Canvas(modifier = modifier.size(size)) {
        val canvasWidth = this.size.width
        val canvasHeight = this.size.height
        val centerX = canvasWidth / 2f
        val centerY = canvasHeight / 2f
        
        // "C" letter with visible gap
        val radius = canvasWidth * 0.32f
        val thickness = canvasWidth * 0.11f
        val gapAngle = 80f
        val innerRadius = radius - thickness
        
        // Calculate start and end points for the gap
        val startAngle = gapAngle / 2
        val endAngle = 360f - gapAngle / 2
        
        // Outer arc start point
        val outerStartX = centerX + radius * kotlin.math.cos(Math.toRadians(startAngle.toDouble())).toFloat()
        val outerStartY = centerY + (radius * 0.9f) * kotlin.math.sin(Math.toRadians(startAngle.toDouble())).toFloat()
        
        // Outer arc end point
        val outerEndX = centerX + radius * kotlin.math.cos(Math.toRadians(endAngle.toDouble())).toFloat()
        val outerEndY = centerY + (radius * 0.9f) * kotlin.math.sin(Math.toRadians(endAngle.toDouble())).toFloat()
        
        // Inner arc start point
        val innerStartX = centerX + innerRadius * kotlin.math.cos(Math.toRadians(startAngle.toDouble())).toFloat()
        val innerStartY = centerY + (innerRadius * 0.9f) * kotlin.math.sin(Math.toRadians(startAngle.toDouble())).toFloat()
        
        // Inner arc end point
        val innerEndX = centerX + innerRadius * kotlin.math.cos(Math.toRadians(endAngle.toDouble())).toFloat()
        val innerEndY = centerY + (innerRadius * 0.9f) * kotlin.math.sin(Math.toRadians(endAngle.toDouble())).toFloat()
        
        val cPath = Path().apply {
            // Start at outer arc start point
            moveTo(outerStartX, outerStartY)
            
            // Draw outer arc
            arcTo(
                rect = Rect(
                    left = centerX - radius,
                    top = centerY - radius * 0.9f,
                    right = centerX + radius,
                    bottom = centerY + radius * 0.9f
                ),
                startAngleDegrees = startAngle,
                sweepAngleDegrees = 360f - gapAngle,
                forceMoveTo = false
            )
            
            // Connect to inner arc end point
            lineTo(innerEndX, innerEndY)
            
            // Draw inner arc
            arcTo(
                rect = Rect(
                    left = centerX - innerRadius,
                    top = centerY - innerRadius * 0.9f,
                    right = centerX + innerRadius,
                    bottom = centerY + innerRadius * 0.9f
                ),
                startAngleDegrees = endAngle,
                sweepAngleDegrees = -(360f - gapAngle),
                forceMoveTo = false
            )
            
            // Connect back to start
            lineTo(outerStartX, outerStartY)
        }
        
        drawPath(
            path = cPath,
            color = Color.White
        )
        
        // "AI" text - smaller for notification icon
        val aiTextStyle = TextStyle(
            color = Color.White,
            fontSize = (canvasWidth * 0.22f).sp,
            fontWeight = FontWeight.Bold
        )
        
        val aiTextLayout = textMeasurer.measure(
            text = "AI",
            style = aiTextStyle
        )
        
        drawText(
            textLayoutResult = aiTextLayout,
            topLeft = Offset(
                x = centerX - aiTextLayout.size.width / 2f,
                y = centerY + canvasHeight * 0.42f
            )
        )
        
        // Center dot
        drawCircle(
            color = Color.White,
            center = Offset(centerX, centerY - canvasHeight * 0.02f),
            radius = canvasWidth * 0.055f
        )
    }
}
