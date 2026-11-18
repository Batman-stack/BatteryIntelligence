package com.batteryintelligence.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.batteryintelligence.ui.theme.LocalBatterySemanticColors
import com.batteryintelligence.ui.theme.LocalReducedMotion
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import java.util.Locale

@Composable
fun TelemetryLineChart(
    xValues: List<Number>,
    yValues: List<Number>,
    lineColor: Color,
    unitSuffix: String,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    secondaryYValues: List<Number>? = null,
    secondaryColor: Color = LocalBatterySemanticColors.current.warmStroke,
    xFormatter: CartesianValueFormatter = CartesianValueFormatter.Default
) {
    if (xValues.size < 2 || yValues.size < 2 || xValues.size != yValues.size) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val reducedMotion = LocalReducedMotion.current
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(xValues, yValues, secondaryYValues) {
        modelProducer.runTransaction {
            lineSeries {
                series(x = xValues, y = yValues)
                if (secondaryYValues != null && secondaryYValues.size == xValues.size) {
                    series(x = xValues, y = secondaryYValues)
                }
            }
        }
    }

    val lines = buildList {
        add(
            LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
                areaFill = LineCartesianLayer.AreaFill.single(fill(lineColor.copy(alpha = 0.14f)))
            )
        )
        if (secondaryYValues != null && secondaryYValues.size == xValues.size) {
            add(
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(fill(secondaryColor))
                )
            )
        }
    }

    val yFormatter = remember(unitSuffix) {
        CartesianValueFormatter { _, value, _ ->
            String.format(Locale.US, "%.1f%s", value, unitSuffix)
        }
    }

    val markerLabel = rememberTextComponent(
        color = MaterialTheme.colorScheme.onSurface
    )
    val marker = rememberDefaultCartesianMarker(
        label = markerLabel,
        guideline = rememberLineComponent(
            fill = fill(LocalBatterySemanticColors.current.cardBorder),
            thickness = 1.dp
        )
    )

    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(lines)
                ),
                startAxis = VerticalAxis.rememberStart(
                    valueFormatter = yFormatter,
                    guideline = rememberLineComponent(
                        fill = fill(LocalBatterySemanticColors.current.cardBorder),
                        thickness = 0.75.dp
                    )
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = xFormatter,
                    guideline = null
                ),
                marker = marker
            ),
            modelProducer = modelProducer,
            animationSpec = if (reducedMotion) null else tween(durationMillis = 320),
            animateIn = !reducedMotion,
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
        )
    }
}

@Composable
fun DailyRollupColumnChart(
    labels: List<String>,
    values: List<Number>,
    barColor: Color,
    unitSuffix: String,
    emptyMessage: String,
    modifier: Modifier = Modifier
) {
    if (labels.isEmpty() || values.isEmpty() || labels.size != values.size) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(170.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val reducedMotion = LocalReducedMotion.current
    val modelProducer = remember { CartesianChartModelProducer() }
    val xIndices = remember(labels.size) { labels.indices.toList() }

    LaunchedEffect(xIndices, values) {
        modelProducer.runTransaction {
            columnSeries {
                series(x = xIndices, y = values)
            }
        }
    }

    val xFormatter = remember(labels) {
        CartesianValueFormatter { _, value, _ ->
            labels.getOrNull(value.toInt()) ?: ""
        }
    }
    val yFormatter = remember(unitSuffix) {
        CartesianValueFormatter { _, value, _ ->
            String.format(Locale.US, "%.1f%s", value, unitSuffix)
        }
    }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        rememberLineComponent(
                            fill = fill(barColor),
                            thickness = 14.dp
                        )
                    )
                ),
                startAxis = VerticalAxis.rememberStart(
                    valueFormatter = yFormatter,
                    guideline = rememberLineComponent(
                        fill = fill(LocalBatterySemanticColors.current.cardBorder),
                        thickness = 0.75.dp
                    )
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = xFormatter,
                    guideline = null
                )
            ),
            modelProducer = modelProducer,
            animationSpec = if (reducedMotion) null else tween(durationMillis = 320),
            modifier = modifier
                .fillMaxWidth()
                .height(190.dp)
        )
    }
}
