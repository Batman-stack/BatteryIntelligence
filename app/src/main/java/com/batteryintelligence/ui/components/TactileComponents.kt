package com.batteryintelligence.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.batteryintelligence.domain.model.ChargingConfidence
import com.batteryintelligence.domain.model.ChargingSpeedTier
import com.batteryintelligence.domain.model.MetricSource
import com.batteryintelligence.ui.theme.LocalBatterySemanticColors
import com.batteryintelligence.ui.theme.LocalHapticsEnabled
import com.batteryintelligence.ui.theme.LocalReducedMotion

fun Modifier.tactilePressScale(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource
): Modifier = composed {
    val reducedMotion = LocalReducedMotion.current
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && !reducedMotion && isPressed) 0.975f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tactileScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun TactileCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = LocalBatterySemanticColors.current.cardBorder,
    shape: Shape = RoundedCornerShape(20.dp),
    elevation: Dp = 2.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current

    val cardModifier = if (onClick != null) {
        modifier
            .tactilePressScale(enabled = true, interactionSource = interactionSource)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (hapticsEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick()
                }
            )
    } else {
        modifier
    }

    Surface(
        modifier = cardModifier,
        shape = shape,
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = elevation,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, LocalBatterySemanticColors.current.cardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = valueColor
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalBatterySemanticColors.current.mutedCaption
                )
            }
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    textColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}

@Composable
fun ChargingSpeedBadge(
    tier: ChargingSpeedTier,
    modifier: Modifier = Modifier
) {
    val semantic = LocalBatterySemanticColors.current
    val (bg, fg) = when (tier) {
        ChargingSpeedTier.VERY_FAST,
        ChargingSpeedTier.FAST -> semantic.fastTealContainer to semantic.fastTealText
        ChargingSpeedTier.NORMAL,
        ChargingSpeedTier.SLOW -> semantic.mintSurface to semantic.emeraldText
        ChargingSpeedTier.PAUSED_HOLD -> semantic.warmContainer to semantic.warmText
        ChargingSpeedTier.DISCHARGING -> semantic.alertContainer to semantic.alertText
        ChargingSpeedTier.CALCULATING,
        ChargingSpeedTier.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    StatusPill(text = tier.displayLabel, textColor = fg, containerColor = bg, modifier = modifier)
}

@Composable
fun EtaConfidenceBadge(
    confidence: ChargingConfidence,
    modifier: Modifier = Modifier
) {
    val semantic = LocalBatterySemanticColors.current
    val (bg, fg) = when (confidence) {
        ChargingConfidence.HIGH -> semantic.mintSurface to semantic.emeraldText
        ChargingConfidence.MEDIUM -> semantic.fastTealContainer to semantic.fastTealText
        ChargingConfidence.LOW -> semantic.warmContainer to semantic.warmText
        ChargingConfidence.CALCULATING -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    StatusPill(
        text = "${confidence.displayLabel} Confidence",
        textColor = fg,
        containerColor = bg,
        modifier = modifier
    )
}

@Composable
fun MetricSourceTag(
    source: MetricSource,
    modifier: Modifier = Modifier
) {
    val semantic = LocalBatterySemanticColors.current
    val (bg, fg) = when (source) {
        MetricSource.BATTERY_MANAGER,
        MetricSource.ACTION_BATTERY_CHANGED,
        MetricSource.POWER_PROFILE -> semantic.mintSurface to semantic.emeraldText
        MetricSource.SESSION_DERIVED,
        MetricSource.USER_CONFIGURED -> semantic.fastTealContainer to semantic.fastTealText
        MetricSource.SYSFS -> semantic.warmContainer to semantic.warmText
        MetricSource.UNAVAILABLE -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    StatusPill(text = source.displayLabel, textColor = fg, containerColor = bg, modifier = modifier)
}

@Composable
fun BatterySideDisclosureBanner(
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalBatterySemanticColors.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenDetails),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, semantic.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Wattage measurement info",
                tint = semantic.emeraldText,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Shows net battery-side power (V × I / 1,000,000), not wall-adapter output. Tap for details.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun BatterySidePowerDisclosureDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Battery-Side vs. Wall-Adapter Wattage",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Power (W) = Voltage (mV) × Current (mA) / 1,000,000 measures net power entering the battery cell at the fuel gauge.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Cable resistance, PMIC conversion losses, active screen/CPU load, and thermal throttling reduce battery-side wattage—for example, a 45W–65W wall adapter often delivers 25W–42W into the battery and tapers above 80%.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Android's public BatteryManager API does not expose proprietary charger handshake protocols (such as USB-PD, PPS, Quick Charge, or SuperVOOC), so speed tiers are classified strictly from smoothed electrical power.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", color = LocalBatterySemanticColors.current.emeraldText)
            }
        }
    )
}
