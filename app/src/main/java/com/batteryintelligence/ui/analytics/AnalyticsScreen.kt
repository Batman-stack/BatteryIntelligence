package com.batteryintelligence.ui.analytics

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteryintelligence.domain.model.AppUsageDetail
import com.batteryintelligence.domain.model.AppUsageSortMode
import com.batteryintelligence.domain.model.BatteryAttributionState
import com.batteryintelligence.domain.model.UsageTimelineBlock
import com.batteryintelligence.ui.components.DailyRollupColumnChart
import com.batteryintelligence.ui.components.MetricTile
import com.batteryintelligence.ui.components.StatusPill
import com.batteryintelligence.ui.components.TactileCard
import com.batteryintelligence.ui.components.UnitFormatters
import com.batteryintelligence.ui.theme.LocalBatterySemanticColors
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class UsageViewSection(val label: String) {
    RANKING("App Ranking"),
    TIMELINE("24h Timeline")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val hasPermission by viewModel.hasUsagePermission.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingUsage.collectAsStateWithLifecycle()
    val report by viewModel.rollingReport.collectAsStateWithLifecycle()
    val sortedApps by viewModel.sortedApps.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val selectedApp by viewModel.selectedApp.collectAsStateWithLifecycle()
    val overnightLogs by viewModel.overnightLogs.collectAsStateWithLifecycle()
    val dailyRollups by viewModel.dailyRollups.collectAsStateWithLifecycle()

    var activeSection by remember { mutableStateOf(UsageViewSection.RANKING) }
    var showMethodology by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val semantic = LocalBatterySemanticColors.current
    val timeFmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshUsageStats()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val rollupLabels = remember(dailyRollups) {
        dailyRollups.takeLast(7).map { it.dateIso.takeLast(5) }
    }
    val rollupEnergyWh = remember(dailyRollups) {
        dailyRollups.takeLast(7).map { it.totalEnergyAddedWh }
    }

    val hourlyLabels = remember(report) {
        val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
        report?.hourlyBuckets?.mapIndexed { idx, bucket ->
            if (idx % 4 == 0) fmt.format(Date(bucket.hourStartMs)).take(5) else ""
        }.orEmpty()
    }
    val hourlyMinutes = remember(report) {
        report?.hourlyBuckets?.map { it.totalForegroundMs / 60_000.0 }.orEmpty()
    }

    if (selectedApp != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectAppForDetail(null) },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AppUsageDetailSheetContent(
                app = selectedApp!!,
                timeFmt = timeFmt,
                iconProvider = viewModel::getAppIcon,
                onClose = { viewModel.selectAppForDetail(null) }
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Rolling 24-Hour App Usage Intelligence Header & Permission Lifecycle
        item {
            TactileCard(
                containerColor = if (hasPermission) semantic.mintSurface else MaterialTheme.colorScheme.surface,
                borderColor = if (hasPermission) semantic.mintBorder else semantic.cardBorder,
                elevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "24-Hour App Usage Intelligence",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (hasPermission) semantic.emeraldText else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Rolling 24h window • 100% local on-device",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (hasPermission) {
                        OutlinedButton(onClick = { viewModel.refreshUsageStats() }) {
                            Text(if (isLoading) "Syncing..." else "Refresh", color = semantic.emeraldText)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (!hasPermission) {
                    Text(
                        text = "Grant Android Usage Access so Battery Intelligence can reconstruct rolling 24-hour foreground sessions, interactive timelines, and transparent battery attribution. All data stays strictly offline on your phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = semantic.emeraldStroke,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Open Usage Access Settings")
                    }
                } else {
                    val currentReport = report
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTile(
                            label = "Tracked Foreground",
                            value = currentReport?.let { UnitFormatters.formatDurationCompact(it.totalTrackedForegroundMs) } ?: "--",
                            subtitle = "${currentReport?.apps?.size ?: 0} active apps",
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.background
                        )
                        MetricTile(
                            label = "Detected Sessions",
                            value = "${currentReport?.totalDetectedSessions ?: 0}",
                            subtitle = "Foreground sessions",
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val state = currentReport?.attributionState ?: BatteryAttributionState.UNAVAILABLE
                        StatusPill(
                            text = "Battery Attribution: ${state.displayLabel} (${currentReport?.attributionConfidence?.displayLabel ?: "Low"})",
                            textColor = if (state == BatteryAttributionState.UNAVAILABLE) semantic.warmText else semantic.emeraldText,
                            containerColor = if (state == BatteryAttributionState.UNAVAILABLE) semantic.warmContainer else MaterialTheme.colorScheme.background
                        )
                        TextButton(onClick = { showMethodology = !showMethodology }) {
                            Text(
                                text = if (showMethodology) "Hide Info" else "How Estimated",
                                color = semantic.emeraldText
                            )
                        }
                    }
                    if (showMethodology && currentReport != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentReport.methodologyNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Hourly Usage Intensity Bar Chart (Rolling 24h)
        if (hasPermission && report != null) {
            item {
                TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Text(
                        text = "Hourly Foreground Intensity (Rolling 24h)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DailyRollupColumnChart(
                        labels = hourlyLabels,
                        values = hourlyMinutes,
                        barColor = semantic.emeraldStroke,
                        unitSuffix = "m",
                        emptyMessage = "No foreground app activity detected in the past 24 hours."
                    )
                }
            }

            // 3. Section Toggle (App Ranking vs Interactive 24h Timeline) + 5 Sort Modes
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UsageViewSection.entries.forEach { sec ->
                            FilterChip(
                                selected = activeSection == sec,
                                onClick = { activeSection = sec },
                                label = { Text(sec.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = semantic.mintSurface,
                                    selectedLabelColor = semantic.emeraldText
                                )
                            )
                        }
                    }

                    if (activeSection == UsageViewSection.RANKING) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AppUsageSortMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = sortMode == mode,
                                    onClick = { viewModel.selectSortMode(mode) },
                                    label = { Text(mode.displayLabel) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = semantic.mintSurface,
                                        selectedLabelColor = semantic.emeraldText
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (activeSection == UsageViewSection.RANKING) {
                if (sortedApps.isEmpty()) {
                    item {
                        TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                            Text(
                                text = "No foreground app sessions reconstructed in the last 24 hours.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(sortedApps, key = { it.packageName }) { app ->
                        AppUsageRankCard(
                            app = app,
                            timeFmt = timeFmt,
                            iconProvider = viewModel::getAppIcon,
                            onClick = { viewModel.selectAppForDetail(app) }
                        )
                    }
                }
            } else {
                val blocks = report?.timelineBlocks.orEmpty().reversed()
                if (blocks.isEmpty()) {
                    item {
                        TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                            Text(
                                text = "No timeline blocks recorded in the past 24 hours.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(blocks.take(60)) { block ->
                        when (block) {
                            is UsageTimelineBlock.AppSessionBlock -> {
                                val matchingApp = report?.apps?.find { it.packageName == block.session.packageName }
                                TactileCard(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    onClick = { matchingApp?.let { viewModel.selectAppForDetail(it) } }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        AppIconBadge(
                                            packageName = block.session.packageName,
                                            appName = block.session.appName,
                                            iconProvider = viewModel::getAppIcon
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = block.session.appName,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${timeFmt.format(Date(block.startTimeMs))} → ${timeFmt.format(Date(block.endTimeMs))} • ${block.session.closeReason}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusPill(
                                            text = UnitFormatters.formatDurationCompact(block.durationMs),
                                            textColor = semantic.emeraldText,
                                            containerColor = semantic.mintSurface
                                        )
                                    }
                                }
                            }

                            is UsageTimelineBlock.InactivityGapBlock -> {
                                TactileCard(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    elevation = 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = block.label,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${timeFmt.format(Date(block.startTimeMs))} → ${timeFmt.format(Date(block.endTimeMs))}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = semantic.mutedCaption
                                            )
                                        }
                                        Text(
                                            text = UnitFormatters.formatDurationCompact(block.durationMs),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Daily Charging Energy Rollups
        item {
            TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                Text(
                    text = "Daily Charging Energy (Last 7 Days)",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                DailyRollupColumnChart(
                    labels = rollupLabels,
                    values = rollupEnergyWh,
                    barColor = semantic.emeraldStroke,
                    unitSuffix = " Wh",
                    emptyMessage = "Complete a charging session to view daily energy rollups."
                )
            }
        }

        // 5. Overnight Drain Logs (V1/V2 Retained)
        item {
            Text(
                text = "Overnight Drain Logs",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (overnightLogs.isEmpty()) {
            item {
                TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Text(
                        text = "No overnight drain logs recorded yet (evaluates 22:00–07:00 windows).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(overnightLogs, key = { it.nightDateIso }) { log ->
                TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = log.nightDateIso,
                            style = MaterialTheme.typography.titleSmall
                        )
                        StatusPill(
                            text = if (log.wasPluggedIn) "Plugged In" else "Standby",
                            textColor = if (log.wasPluggedIn) semantic.emeraldText else semantic.warmText,
                            containerColor = if (log.wasPluggedIn) semantic.mintSurface else semantic.warmContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${log.startLevelPercent ?: "?"}% → ${log.endLevelPercent ?: "?"}% • " +
                            (log.drainRatePercentPerHour?.let { String.format(Locale.US, "%.2f%%/hr", it) } ?: "--"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun AppUsageRankCard(
    app: AppUsageDetail,
    timeFmt: DateFormat,
    iconProvider: (String) -> android.graphics.drawable.Drawable?,
    onClick: () -> Unit
) {
    val semantic = LocalBatterySemanticColors.current
    TactileCard(
        containerColor = MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppIconBadge(
                packageName = app.packageName,
                appName = app.appName,
                iconProvider = iconProvider
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "#${app.dailyRank} ${app.appName}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = UnitFormatters.formatDurationCompact(app.totalForegroundMs),
                        style = MaterialTheme.typography.titleSmall,
                        color = semantic.emeraldText
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${app.detectedSessionCount} detected sessions • Last ${timeFmt.format(Date(app.lastUsedMs))} • ${String.format(Locale.US, "%.1f%% share", app.usageSharePercent)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (app.usageSharePercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = semantic.emeraldStroke,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Avg ${UnitFormatters.formatDurationCompact(app.averageSessionMs)} • Max ${UnitFormatters.formatDurationCompact(app.longestSessionMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = semantic.mutedCaption
                    )
                    val attr = app.batteryAttribution
                    StatusPill(
                        text = attr.estimatedRangeLabel ?: attr.state.displayLabel,
                        textColor = if (attr.state == BatteryAttributionState.UNAVAILABLE) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            semantic.emeraldText
                        },
                        containerColor = if (attr.state == BatteryAttributionState.UNAVAILABLE) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            semantic.mintSurface
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIconBadge(
    packageName: String,
    appName: String,
    iconProvider: (String) -> android.graphics.drawable.Drawable?
) {
    val semantic = LocalBatterySemanticColors.current
    val bitmap = remember(packageName) {
        runCatching {
            iconProvider(packageName)?.toBitmap(width = 72, height = 72)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "$appName icon",
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(semantic.mintSurface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = appName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = semantic.emeraldText
            )
        }
    }
}

@Composable
private fun AppUsageDetailSheetContent(
    app: AppUsageDetail,
    timeFmt: DateFormat,
    iconProvider: (String) -> android.graphics.drawable.Drawable?,
    onClose: () -> Unit
) {
    val semantic = LocalBatterySemanticColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIconBadge(app.packageName, app.appName, iconProvider)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.appName, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${app.packageName} • Rank #${app.dailyRank}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                label = "Total 24h Time",
                value = UnitFormatters.formatDurationCompact(app.totalForegroundMs),
                subtitle = String.format(Locale.US, "%.1f%% of tracked", app.usageSharePercent),
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Detected Sessions",
                value = "${app.detectedSessionCount}",
                subtitle = "Avg ${UnitFormatters.formatDurationCompact(app.averageSessionMs)}",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                label = "First / Last Used",
                value = "${timeFmt.format(Date(app.firstUsedMs))} – ${timeFmt.format(Date(app.lastUsedMs))}",
                subtitle = "Longest: ${UnitFormatters.formatDurationCompact(app.longestSessionMs)}",
                modifier = Modifier.weight(1f)
            )
        }

        TactileCard(
            containerColor = semantic.mintSurface,
            borderColor = semantic.mintBorder,
            elevation = 0.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Battery Attribution (${app.batteryAttribution.state.displayLabel})",
                    style = MaterialTheme.typography.titleSmall,
                    color = semantic.emeraldText
                )
                StatusPill(
                    text = app.batteryAttribution.estimatedRangeLabel ?: "Not Available",
                    textColor = semantic.emeraldText,
                    containerColor = MaterialTheme.colorScheme.background
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = app.batteryAttribution.methodologySummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "Recent Detected Foreground Sessions (${app.sessions.size})",
            style = MaterialTheme.typography.titleSmall
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            app.sessions.takeLast(6).reversed().forEach { s ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${timeFmt.format(Date(s.startTimeMs))} → ${timeFmt.format(Date(s.endTimeMs))} (${s.closeReason})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = UnitFormatters.formatDurationCompact(s.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = semantic.emeraldText
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onClose) {
                Text("Close", color = semantic.emeraldText)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
