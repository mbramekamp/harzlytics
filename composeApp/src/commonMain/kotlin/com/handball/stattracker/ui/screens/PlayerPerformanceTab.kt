package com.handball.stattracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.*
import com.handball.stattracker.repository.HandballRepository
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class PerformanceGraphMetric(val label: String, val forGoalkeeper: Boolean?) {
    PERFORMANCE_INDEX("Performance-Index", null),
    GOALS_AND_SHOTS("Tore & Würfe", false),
    SHOT_ACCURACY("Wurfquote (%)", false),
    ASSISTS_AND_DEFENSE("Assists & Abwehr", false),
    MISTAKES_AND_PENALTIES("Techn. Fehler & 2Min", false),
    SAVES_AND_CONCEDED("Paraden & Gegentore", true),
    SAVE_PERCENTAGE("Fangquote (%)", true),
    KEEPER_SPECIALS("7m-Paraden & Gegenstoß", true)
}

@Composable
fun PlayerPerformanceTabContent(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    player: Player,
    seasonFilterId: Int?,
    isWideScreen: Boolean,
    onSelectPlayer: (Int) -> Unit,
    onNavigateToGame: (Int) -> Unit
) {
    if (player.role == PlayerRole.COACH) {
        CoachPlayerRankingDashboard(
            repository = repository,
            snapshot = snapshot,
            seasonFilterId = seasonFilterId,
            onSelectPlayer = onSelectPlayer
        )
        return
    }

    val stats = remember(snapshot, player.playerId, seasonFilterId) {
        repository.computePlayerAggregatedStats(player.playerId, seasonFilterId, snapshot)
    }
    val performanceScore = remember(player.role, stats) {
        repository.computePerformanceScore(player.role, stats)
    }
    val series = remember(snapshot, player.playerId, seasonFilterId) {
        repository.getPlayerPerformanceSeries(player.playerId, seasonFilterId, snapshot)
    }
    val comparisons = remember(snapshot, player.playerId, seasonFilterId) {
        repository.computePositionMetricComparisons(player.playerId, seasonFilterId, snapshot)
    }
    val radarAxes = remember(snapshot, player.playerId, seasonFilterId) {
        repository.computeRadarAxesForPlayer(player.playerId, seasonFilterId, snapshot)
    }
    val positionRankings = remember(snapshot, player.positionId, seasonFilterId) {
        repository.computePlayerRankings(
            positionIdFilter = player.positionId,
            seasonIdFilter = seasonFilterId,
            sortCriterion = RankingSortCriterion.PERFORMANCE_INDEX,
            state = snapshot
        )
    }

    val positionName = snapshot.positions.find { it.positionId == player.positionId }?.positionName ?: player.role.label
    val myRankInPosition = positionRankings.find { it.player.playerId == player.playerId }?.rank ?: 1
    val totalInPosition = positionRankings.size.coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // 1. Top Performance Summary Card (Performance-Index Gauge + Positions-Rang)
        PerformanceSummaryHeroCard(
            player = player,
            positionName = positionName,
            performanceScore = performanceScore,
            rankInPosition = myRankInPosition,
            totalInPosition = totalInPosition,
            stats = stats
        )

        // 2. Graphen: Chronologische Formkurve + 6-Achsen-Radar-Diagramm
        if (isWideScreen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(1.35f)) {
                    GameProgressionChartCard(
                        role = player.role,
                        series = series,
                        comparisons = comparisons,
                        onNavigateToGame = onNavigateToGame
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    PositionRadarChartCard(
                        playerName = player.fullName,
                        positionName = positionName,
                        axes = radarAxes
                    )
                }
            }
        } else {
            GameProgressionChartCard(
                role = player.role,
                series = series,
                comparisons = comparisons,
                onNavigateToGame = onNavigateToGame
            )
            PositionRadarChartCard(
                playerName = player.fullName,
                positionName = positionName,
                axes = radarAxes
            )
        }

        // 3. Einzelwerte im direkten Positionsvergleich
        PositionMetricsComparisonSection(
            playerName = player.fullName,
            positionName = positionName,
            comparisons = comparisons
        )

        // 4. Trainer-Spieler-Ranking (Standardmäßig auf die Position des Spielers gefiltert, umschaltbar auf alle Positionen)
        TrainerPlayerRankingSection(
            repository = repository,
            snapshot = snapshot,
            highlightedPlayerId = player.playerId,
            initialPositionIdFilter = player.positionId,
            seasonFilterId = seasonFilterId,
            onSelectPlayer = onSelectPlayer
        )
    }
}

@Composable
private fun PerformanceSummaryHeroCard(
    player: Player,
    positionName: String,
    performanceScore: Double,
    rankInPosition: Int,
    totalInPosition: Int,
    stats: PlayerAggregatedStats
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Kreisförmiger Performance-Index-Tacho
            PerformanceScoreGauge(score = performanceScore)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Handball Performance-Index",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (rankInPosition == 1) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (rankInPosition == 1) Icons.Default.EmojiEvents else Icons.Default.Leaderboard,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (rankInPosition == 1) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                            Text(
                                text = "Platz #$rankInPosition von $totalInPosition auf $positionName",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (rankInPosition == 1) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                        }
                    }
                }

                Text(
                    text = if (player.role == PlayerRole.FIELD_PLAYER) {
                        "Gewichteter Effizienz-Index aus Toren/Spiel, Wurfquote, Assists, Abwehraktionen (Steals/Blocks) und gezogenen 7m abzüglich technischer Fehler & Zeitstrafen."
                    } else {
                        "Gewichteter Torwart-Index aus Fangquote, Paraden pro Spiel, 7m-Abwehrquote und Gegenstoß-Passquote abzüglich Strafen."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))

                // Kurz-KPIs pro Spiel
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (player.role == PlayerRole.FIELD_PLAYER) {
                        MiniKpiPill("Ø Tore/Spiel", formatOneDecimal(stats.goalsPerGame))
                        MiniKpiPill("Wurfquote", "${stats.shotAccuracyPercent}%")
                        MiniKpiPill("Ø Assists/Spiel", formatOneDecimal(stats.assistsPerGame))
                        MiniKpiPill("Ø Steals+Blocks", formatOneDecimal(stats.defensiveActionsPerGame))
                        MiniKpiPill("Ø Techn. Fehler", formatOneDecimal(stats.technicalMistakesPerGame))
                    } else {
                        MiniKpiPill("Ø Paraden/Spiel", formatOneDecimal(stats.savesPerGame))
                        MiniKpiPill("Fangquote", "${stats.savePercentage}%")
                        MiniKpiPill("7m-Quote", "${stats.penaltySavePercentage}%")
                        MiniKpiPill("Gegenstoß-Pass", "${stats.fastBreakPassPercentage}%")
                        MiniKpiPill("Ø Gegentore", formatOneDecimal(stats.goalsConcededPerGame))
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceScoreGauge(score: Double) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    val gaugeColor = when {
        score >= 75.0 -> tertiaryColor
        score >= 50.0 -> primaryColor
        else -> secondaryColor
    }

    Box(
        modifier = Modifier.size(104.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            val strokeWidth = 10.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            val sweep = ((score.toFloat() / 100f).coerceIn(0f, 1f)) * 270f
            drawArc(
                color = gaugeColor,
                startAngle = 135f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatOneDecimal(score),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = gaugeColor
            )
            Text(
                text = "/ 100 Pkt",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MiniKpiPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun GameProgressionChartCard(
    role: PlayerRole,
    series: List<PlayerGamePerformancePoint>,
    comparisons: List<PositionMetricComparison>,
    onNavigateToGame: (Int) -> Unit
) {
    val availableMetrics = remember(role) {
        PerformanceGraphMetric.entries.filter {
            it.forGoalkeeper == null || (it.forGoalkeeper == (role == PlayerRole.GOALKEEPER))
        }
    }
    var selectedMetric by remember(role) { mutableStateOf(PerformanceGraphMetric.PERFORMANCE_INDEX) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)

    // Positions-Durchschnitt als Referenzlinie im Graphen
    val posAvgReference: Double? = remember(selectedMetric, comparisons) {
        when (selectedMetric) {
            PerformanceGraphMetric.PERFORMANCE_INDEX -> comparisons.find { it.metricKey == "perf_index" }?.positionAverage
            PerformanceGraphMetric.GOALS_AND_SHOTS -> comparisons.find { it.metricKey == "goals_pg" }?.positionAverage
            PerformanceGraphMetric.SHOT_ACCURACY -> comparisons.find { it.metricKey == "shot_acc" }?.positionAverage
            PerformanceGraphMetric.ASSISTS_AND_DEFENSE -> comparisons.find { it.metricKey == "assists_pg" }?.positionAverage
            PerformanceGraphMetric.MISTAKES_AND_PENALTIES -> comparisons.find { it.metricKey == "mistakes_pg" }?.positionAverage
            PerformanceGraphMetric.SAVES_AND_CONCEDED -> comparisons.find { it.metricKey == "saves_pg" }?.positionAverage
            PerformanceGraphMetric.SAVE_PERCENTAGE -> comparisons.find { it.metricKey == "save_pct" }?.positionAverage
            PerformanceGraphMetric.KEEPER_SPECIALS -> null
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Leistungsentwicklung pro Spiel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Chronologischer Verlauf über ${series.size} Spiele im Vergleich zum Positions-Durchschnitt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Metrik-Auswahl-Chips
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableMetrics.forEach { metric ->
                    FilterChip(
                        selected = selectedMetric == metric,
                        onClick = { selectedMetric = metric },
                        label = { Text(metric.label) }
                    )
                }
            }

            if (series.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(210.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Noch keine Spieldaten für diesen Zeitraum vorhanden.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Datenreihen extrahieren (Primärwert + optionaler Sekundärwert)
                val primaryValues = series.map { pt ->
                    when (selectedMetric) {
                        PerformanceGraphMetric.PERFORMANCE_INDEX -> pt.performanceScore.toFloat()
                        PerformanceGraphMetric.GOALS_AND_SHOTS -> pt.goals.toFloat()
                        PerformanceGraphMetric.SHOT_ACCURACY -> pt.shotAccuracyPercent.toFloat()
                        PerformanceGraphMetric.ASSISTS_AND_DEFENSE -> pt.assists.toFloat()
                        PerformanceGraphMetric.MISTAKES_AND_PENALTIES -> pt.technicalMistakes.toFloat()
                        PerformanceGraphMetric.SAVES_AND_CONCEDED -> pt.saves.toFloat()
                        PerformanceGraphMetric.SAVE_PERCENTAGE -> pt.savePercentage.toFloat()
                        PerformanceGraphMetric.KEEPER_SPECIALS -> pt.penaltiesSaved.toFloat()
                    }
                }
                val secondaryValues: List<Float>? = when (selectedMetric) {
                    PerformanceGraphMetric.GOALS_AND_SHOTS -> series.map { it.shots.toFloat() }
                    PerformanceGraphMetric.ASSISTS_AND_DEFENSE -> series.map { it.defensiveActions.toFloat() }
                    PerformanceGraphMetric.MISTAKES_AND_PENALTIES -> series.map { it.twoMinuteSuspensions.toFloat() }
                    PerformanceGraphMetric.SAVES_AND_CONCEDED -> series.map { it.goalsConceded.toFloat() }
                    PerformanceGraphMetric.KEEPER_SPECIALS -> series.map { it.fastBreakPassesSuccess.toFloat() }
                    else -> null
                }

                val rawMax = max(
                    primaryValues.maxOrNull() ?: 10f,
                    secondaryValues?.maxOrNull() ?: 0f
                )
                val maxY = when (selectedMetric) {
                    PerformanceGraphMetric.PERFORMANCE_INDEX,
                    PerformanceGraphMetric.SHOT_ACCURACY,
                    PerformanceGraphMetric.SAVE_PERCENTAGE -> 100f
                    else -> max(rawMax * 1.2f, 5f)
                }

                // Legende
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val primaryLegend = when (selectedMetric) {
                        PerformanceGraphMetric.PERFORMANCE_INDEX -> "Performance-Index"
                        PerformanceGraphMetric.GOALS_AND_SHOTS -> "Tore"
                        PerformanceGraphMetric.SHOT_ACCURACY -> "Wurfquote %"
                        PerformanceGraphMetric.ASSISTS_AND_DEFENSE -> "Assists"
                        PerformanceGraphMetric.MISTAKES_AND_PENALTIES -> "Techn. Fehler"
                        PerformanceGraphMetric.SAVES_AND_CONCEDED -> "Paraden"
                        PerformanceGraphMetric.SAVE_PERCENTAGE -> "Fangquote %"
                        PerformanceGraphMetric.KEEPER_SPECIALS -> "7m-Paraden"
                    }
                    ChartLegendItem(color = primaryColor, label = primaryLegend)

                    if (secondaryValues != null) {
                        val secLegend = when (selectedMetric) {
                            PerformanceGraphMetric.GOALS_AND_SHOTS -> "Würfe gesamt"
                            PerformanceGraphMetric.ASSISTS_AND_DEFENSE -> "Steals + Blocks"
                            PerformanceGraphMetric.MISTAKES_AND_PENALTIES -> "2-Minuten-Strafen"
                            PerformanceGraphMetric.SAVES_AND_CONCEDED -> "Gegentore"
                            PerformanceGraphMetric.KEEPER_SPECIALS -> "Gegenstoßpässe"
                            else -> ""
                        }
                        ChartLegendItem(color = secondaryColor, label = secLegend)
                    }

                    if (posAvgReference != null) {
                        ChartLegendItem(
                            color = tertiaryColor,
                            label = "Ø Position (${formatOneDecimal(posAvgReference)})"
                        )
                    }
                }

                // Canvas-Graph
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(195.dp)
                        .padding(top = 6.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val leftPad = 28.dp.toPx()
                        val rightPad = 16.dp.toPx()
                        val topPad = 14.dp.toPx()
                        val bottomPad = 18.dp.toPx()
                        val chartW = (size.width - leftPad - rightPad).coerceAtLeast(10f)
                        val chartH = (size.height - topPad - bottomPad).coerceAtLeast(10f)

                        // 4 Horizontale Gitterlinien
                        for (i in 0..4) {
                            val ratio = i / 4f
                            val y = topPad + chartH * (1f - ratio)
                            drawLine(
                                color = gridColor,
                                start = Offset(leftPad, y),
                                end = Offset(leftPad + chartW, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // Gestrichelte Referenzlinie für den Positions-Durchschnitt
                        if (posAvgReference != null) {
                            val refRatio = (posAvgReference.toFloat() / maxY).coerceIn(0f, 1f)
                            val refY = topPad + chartH * (1f - refRatio)
                            drawLine(
                                color = tertiaryColor,
                                start = Offset(leftPad, refY),
                                end = Offset(leftPad + chartW, refY),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                            )
                        }

                        val count = series.size
                        val stepX = chartW / count.toFloat()

                        if (secondaryValues != null) {
                            // Gruppiertes Säulendiagramm für Primär- und Sekundärwert
                            val barWidth = (stepX * 0.28f).coerceIn(8.dp.toPx(), 26.dp.toPx())
                            for (i in 0 until count) {
                                val centerX = leftPad + stepX * (i + 0.5f)
                                val pRatio = (primaryValues[i] / maxY).coerceIn(0f, 1f)
                                val sRatio = (secondaryValues[i] / maxY).coerceIn(0f, 1f)

                                val pH = (chartH * pRatio).coerceAtLeast(4f)
                                val sH = (chartH * sRatio).coerceAtLeast(4f)

                                // Primär-Balken (links)
                                drawRoundRect(
                                    color = primaryColor,
                                    topLeft = Offset(centerX - barWidth - 2.dp.toPx(), topPad + chartH - pH),
                                    size = Size(barWidth, pH),
                                    cornerRadius = CornerRadius(6f, 6f)
                                )
                                // Sekundär-Balken (rechts)
                                drawRoundRect(
                                    color = secondaryColor.copy(alpha = 0.78f),
                                    topLeft = Offset(centerX + 2.dp.toPx(), topPad + chartH - sH),
                                    size = Size(barWidth, sH),
                                    cornerRadius = CornerRadius(6f, 6f)
                                )
                            }
                        } else {
                            // Liniendiagramm mit Flächenfüllung und Datenpunkten
                            val points = (0 until count).map { i ->
                                val x = leftPad + stepX * (i + 0.5f)
                                val ratio = (primaryValues[i] / maxY).coerceIn(0f, 1f)
                                val y = topPad + chartH * (1f - ratio)
                                Offset(x, y)
                            }

                            if (points.size >= 2) {
                                val fillPath = Path().apply {
                                    moveTo(points.first().x, topPad + chartH)
                                    points.forEach { lineTo(it.x, it.y) }
                                    lineTo(points.last().x, topPad + chartH)
                                    close()
                                }
                                drawPath(
                                    path = fillPath,
                                    color = primaryColor.copy(alpha = 0.16f)
                                )

                                for (i in 0 until points.size - 1) {
                                    drawLine(
                                        color = primaryColor,
                                        start = points[i],
                                        end = points[i + 1],
                                        strokeWidth = 3.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            } else if (points.size == 1) {
                                val pt = points.first()
                                drawRoundRect(
                                    color = primaryColor.copy(alpha = 0.35f),
                                    topLeft = Offset(pt.x - 16.dp.toPx(), pt.y),
                                    size = Size(32.dp.toPx(), topPad + chartH - pt.y),
                                    cornerRadius = CornerRadius(8f, 8f)
                                )
                            }

                            points.forEach { pt ->
                                drawCircle(
                                    color = primaryColor,
                                    radius = 6.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 3.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }
                }

                // X-Achsen-Spiel-Karten unterhalb des Graphen mit exakten Zahlenwerten (klickbar zum Öffnen des Spiels)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    series.forEachIndexed { idx, pt ->
                        val valText = when (selectedMetric) {
                            PerformanceGraphMetric.PERFORMANCE_INDEX -> "${formatOneDecimal(pt.performanceScore)} Pkt"
                            PerformanceGraphMetric.GOALS_AND_SHOTS -> "${pt.goals}/${pt.shots} Tore"
                            PerformanceGraphMetric.SHOT_ACCURACY -> "${pt.shotAccuracyPercent}%"
                            PerformanceGraphMetric.ASSISTS_AND_DEFENSE -> "${pt.assists} A / ${pt.defensiveActions} Abw"
                            PerformanceGraphMetric.MISTAKES_AND_PENALTIES -> "${pt.technicalMistakes} TF / ${pt.twoMinuteSuspensions}×2m"
                            PerformanceGraphMetric.SAVES_AND_CONCEDED -> "${pt.saves} Par / ${pt.goalsConceded} GT"
                            PerformanceGraphMetric.SAVE_PERCENTAGE -> "${pt.savePercentage}%"
                            PerformanceGraphMetric.KEEPER_SPECIALS -> "${pt.penaltiesSaved} 7m / ${pt.fastBreakPassesSuccess} Pässe"
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToGame(pt.gameId) },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = valText,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = pt.shortLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartLegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PositionRadarChartCard(
    playerName: String,
    positionName: String,
    axes: List<RadarMetricAxis>
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Stärkenprofil vs. Position",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "6-Achsen-Vergleich: $playerName ggü. Ø $positionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChartLegendItem(color = primaryColor, label = playerName)
                ChartLegendItem(color = secondaryColor, label = "Ø $positionName")
            }

            if (axes.size >= 3) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(180.dp)) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val radius = (minOf(size.width, size.height) / 2f) * 0.82f
                        val n = axes.size

                        fun pointAt(index: Int, norm: Float): Offset {
                            val angle = -PI / 2.0 + (2.0 * PI * index / n)
                            return Offset(
                                x = (cx + radius * norm * cos(angle)).toFloat(),
                                y = (cy + radius * norm * sin(angle)).toFloat()
                            )
                        }

                        // Konzentrische Sechseck-Ringe (25%, 50%, 75%, 100%)
                        listOf(0.25f, 0.5f, 0.75f, 1.0f).forEach { level ->
                            val ringPath = Path().apply {
                                for (i in 0 until n) {
                                    val pt = pointAt(i, level)
                                    if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                                }
                                close()
                            }
                            drawPath(
                                path = ringPath,
                                color = gridColor,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }

                        // Speichen vom Zentrum zu den Ecken
                        for (i in 0 until n) {
                            val outer = pointAt(i, 1.0f)
                            drawLine(
                                color = gridColor,
                                start = Offset(cx, cy),
                                end = outer,
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // Polygon: Positions-Durchschnitt (gestrichelt)
                        val avgPath = Path().apply {
                            axes.forEachIndexed { i, axis ->
                                val pt = pointAt(i, axis.positionAvgNormalized)
                                if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                            }
                            close()
                        }
                        drawPath(
                            path = avgPath,
                            color = secondaryColor.copy(alpha = 0.18f)
                        )
                        drawPath(
                            path = avgPath,
                            color = secondaryColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                            )
                        )

                        // Polygon: Ausgewählter Spieler (gefüllt)
                        val playerPath = Path().apply {
                            axes.forEachIndexed { i, axis ->
                                val pt = pointAt(i, axis.playerNormalized)
                                if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                            }
                            close()
                        }
                        drawPath(
                            path = playerPath,
                            color = primaryColor.copy(alpha = 0.28f)
                        )
                        drawPath(
                            path = playerPath,
                            color = primaryColor,
                            style = Stroke(width = 2.5.dp.toPx())
                        )

                        axes.forEachIndexed { i, axis ->
                            val pt = pointAt(i, axis.playerNormalized)
                            drawCircle(
                                color = primaryColor,
                                radius = 4.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }

                // Kompakte Achsen-Werte-Übersicht (2×3 Grid)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    axes.chunked(3).forEach { rowAxes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowAxes.forEach { ax ->
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = ax.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${ax.playerFormatted} (Ø ${ax.avgFormatted})",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionMetricsComparisonSection(
    playerName: String,
    positionName: String,
    comparisons: List<PositionMetricComparison>
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val peerCount = comparisons.firstOrNull()?.totalPlayersInPosition ?: 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Einzelwerte im Positionsvergleich ($positionName)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Jeder Wert von $playerName im direkten Vergleich mit allen $peerCount Spielern auf $positionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            comparisons.forEach { comp ->
                PositionMetricComparisonBarRow(comp)
            }
        }
    }
}

@Composable
private fun PositionMetricComparisonBarRow(comp: PositionMetricComparison) {
    val isBetterOrEqualAvg = if (comp.higherIsBetter) {
        comp.playerValue >= comp.positionAverage
    } else {
        comp.playerValue <= comp.positionAverage
    }

    val diff = comp.playerValue - comp.positionAverage
    val diffSign = if (diff > 0) "+" else ""
    val unitSuffix = if (comp.unit.isNotEmpty()) " ${comp.unit}" else ""

    val statusColor = when {
        comp.rankInPosition == 1 -> MaterialTheme.colorScheme.tertiary
        isBetterOrEqualAvg -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = comp.metricLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!comp.higherIsBetter) {
                        Text(
                            text = "(niedriger = besser)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Differenz zum Positions-Durchschnitt
                    Text(
                        text = "$diffSign${formatMetricValue(diff, comp.unit)}$unitSuffix ggü. Ø",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    // Rang-Badge auf der Position
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "Platz #${comp.rankInPosition}/${comp.totalPlayersInPosition}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            // Vergleichsbalken: Spieler vs. Ø Position vs. Bestwert Position
            val maxScale = maxOf(
                comp.playerValue,
                comp.positionAverage,
                comp.positionBest,
                if (comp.unit == "%") 100.0 else 1.0
            ) * 1.05

            val playerFraction = (comp.playerValue / maxScale).coerceIn(0.02, 1.0).toFloat()
            val avgFraction = (comp.positionAverage / maxScale).coerceIn(0.02, 1.0).toFloat()
            val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
            val avgLineColor = MaterialTheme.colorScheme.onSurface

            Box(
                modifier = Modifier.fillMaxWidth().height(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Hintergrund-Track
                    drawRoundRect(
                        color = trackColor,
                        size = size,
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                    // Spieler-Balken
                    drawRoundRect(
                        color = statusColor,
                        size = Size(size.width * playerFraction, size.height),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                    // Vertikale Markierung für den Positions-Durchschnitt
                    val avgX = size.width * avgFraction
                    drawLine(
                        color = avgLineColor,
                        start = Offset(avgX, 0f),
                        end = Offset(avgX, size.height),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Spieler: ${formatMetricValue(comp.playerValue, comp.unit)}$unitSuffix",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Text(
                    text = "Ø Position: ${formatMetricValue(comp.positionAverage, comp.unit)}$unitSuffix",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Bestwert: ${formatMetricValue(comp.positionBest, comp.unit)}$unitSuffix (${comp.bestPlayerName})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CoachPlayerRankingDashboard(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    seasonFilterId: Int?,
    onSelectPlayer: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // 1. Top-Performer pro Position (Alle 7 Handball-Positionen im Überblick für den Trainer)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Top-Spieler je Position (Startsieben-Empfehlung)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Jeweils der #1 gerankte Spieler pro Position nach aktuellem Performance-Index",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    snapshot.positions.forEach { pos ->
                        val topForPos = repository.computePlayerRankings(
                            positionIdFilter = pos.positionId,
                            seasonIdFilter = seasonFilterId,
                            sortCriterion = RankingSortCriterion.PERFORMANCE_INDEX,
                            state = snapshot
                        ).firstOrNull()

                        if (topForPos != null) {
                            Surface(
                                modifier = Modifier
                                    .width(185.dp)
                                    .clickable { onSelectPlayer(topForPos.player.playerId) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = pos.positionName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "#${topForPos.player.jerseyNumberHomeKit ?: "-"} ${topForPos.player.fullName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Index: ${formatOneDecimal(topForPos.performanceScore)} Pkt",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Interaktives Spieler-Ranking für den Trainer
        TrainerPlayerRankingSection(
            repository = repository,
            snapshot = snapshot,
            highlightedPlayerId = null,
            initialPositionIdFilter = null,
            seasonFilterId = seasonFilterId,
            onSelectPlayer = onSelectPlayer
        )
    }
}

@Composable
fun TrainerPlayerRankingSection(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    highlightedPlayerId: Int?,
    initialPositionIdFilter: Int?,
    seasonFilterId: Int?,
    onSelectPlayer: (Int) -> Unit
) {
    var selectedPositionId by remember(initialPositionIdFilter) { mutableStateOf(initialPositionIdFilter) }
    var sortCriterion by remember { mutableStateOf(RankingSortCriterion.PERFORMANCE_INDEX) }

    val rankings = remember(snapshot, selectedPositionId, seasonFilterId, sortCriterion) {
        repository.computePlayerRankings(
            positionIdFilter = selectedPositionId,
            seasonIdFilter = seasonFilterId,
            sortCriterion = sortCriterion,
            state = snapshot
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Leaderboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Trainer-Ansicht: Spieler-Ranking nach Position",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Vergleiche und sortiere Spieler einer Position oder des gesamten Kaders nach Leistungs-Kriterien",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Positions-Filter
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Position:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                FilterChip(
                    selected = selectedPositionId == null,
                    onClick = { selectedPositionId = null },
                    label = { Text("Alle Positionen") }
                )
                snapshot.positions.forEach { pos ->
                    FilterChip(
                        selected = selectedPositionId == pos.positionId,
                        onClick = { selectedPositionId = pos.positionId },
                        label = { Text(pos.positionName) }
                    )
                }
            }

            // Sortier-Kriterium
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sortierung:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                RankingSortCriterion.entries.forEach { crit ->
                    FilterChip(
                        selected = sortCriterion == crit,
                        onClick = { sortCriterion = crit },
                        label = { Text(crit.label) }
                    )
                }
            }

            val maxScore = (rankings.maxOfOrNull { it.performanceScore } ?: 100.0).coerceAtLeast(1.0)

            rankings.forEach { entry ->
                val isHighlighted = entry.player.playerId == highlightedPlayerId
                val containerColor = when {
                    isHighlighted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    entry.rank == 1 -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = containerColor,
                    border = if (isHighlighted) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPlayer(entry.player.playerId) }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Rang-Badge (#1, #2, #3...)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when (entry.rank) {
                                        1 -> MaterialTheme.colorScheme.tertiary
                                        2 -> MaterialTheme.colorScheme.primary
                                        3 -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                ) {
                                    Text(
                                        text = "#${entry.rank}",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }

                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "#${entry.player.jerseyNumberHomeKit ?: "-"} ${entry.player.fullName}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isHighlighted) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            ) {
                                                Text(
                                                    text = "Aktuell geöffnet",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${entry.positionName} • ${entry.stats.gamesAttended} Spiele",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Performance-Index rechts
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${formatOneDecimal(entry.performanceScore)} Pkt",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Performance-Index",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Fortschrittsbalken relativ zum Bestwert
                        LinearProgressIndicator(
                            progress = { (entry.performanceScore / maxScore).toFloat().coerceIn(0.05f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = if (entry.rank == 1) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )

                        // Detail-Statistikzeile zum schnellen Vergleich
                        val detailLine = if (entry.player.role == PlayerRole.FIELD_PLAYER) {
                            "Ø Tore: ${formatOneDecimal(entry.stats.goalsPerGame)} (${entry.stats.totalGoals}/${entry.stats.totalShots}) • Quote: ${entry.stats.shotAccuracyPercent}% • Ø Assists: ${formatOneDecimal(entry.stats.assistsPerGame)} • Ø Steals+Blocks: ${formatOneDecimal(entry.stats.defensiveActionsPerGame)} • Ø TF: ${formatOneDecimal(entry.stats.technicalMistakesPerGame)}"
                        } else {
                            "Ø Paraden: ${formatOneDecimal(entry.stats.savesPerGame)} (${entry.stats.totalSaves} ges.) • Fangquote: ${entry.stats.savePercentage}% • 7m-Quote: ${entry.stats.penaltySavePercentage}% • Gegenstoß: ${entry.stats.fastBreakPassPercentage}% • Ø Gegentore: ${formatOneDecimal(entry.stats.goalsConcededPerGame)}"
                        }
                        Text(
                            text = detailLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatOneDecimal(value: Double): String {
    val rounded = (value * 10.0).roundToInt() / 10.0
    val intPart = rounded.toInt()
    val decPart = kotlin.math.abs(((rounded - intPart) * 10).roundToInt())
    return "$intPart.$decPart"
}

private fun formatMetricValue(value: Double, unit: String): String {
    return if (unit == "%") {
        "${value.roundToInt()}"
    } else {
        formatOneDecimal(value)
    }
}
