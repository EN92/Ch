package com.ch.track

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Slider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ch.track.domain.RecordStatus
import com.ch.track.map.MultiMapTrackView
import com.ch.track.map.MapVendor
import com.ch.track.service.RecordForegroundService
import com.ch.track.ui.RecordViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: RecordViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TrackApp(viewModel) }
    }
}

@Composable
private fun TrackApp(viewModel: RecordViewModel) {
    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("TraceMaster MVP") }) }) { innerPadding ->
            Dashboard(innerPadding, viewModel)
        }
    }
}


private fun speedSparkline(values: List<Float>): String {
    if (values.isEmpty()) return "-"
    val blocks = listOf("▁","▂","▃","▄","▅","▆","▇","█")
    val max = values.maxOrNull() ?: 1f
    return values.takeLast(12).joinToString("") { v ->
        val idx = ((v / max) * (blocks.size - 1)).toInt().coerceIn(0, blocks.size - 1)
        blocks[idx]
    }
}


@Composable
private fun StatsPanel(historyKm: List<Float>, paceSec: List<Int>, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1E24))) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("📊 完整可视化统计页", color = Color.White, style = MaterialTheme.typography.titleMedium)
            if (historyKm.isEmpty()) {
                Text("暂无统计数据", color = Color(0xFFB8C0CC))
            } else {
                Text("里程分布（最近会话）", color = Color(0xFFB8C0CC))
                Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                    val maxV = historyKm.maxOrNull()?.coerceAtLeast(0.1f) ?: 1f
                    val barW = size.width / historyKm.size
                    historyKm.forEachIndexed { i, v ->
                        val h = (v / maxV) * size.height
                        drawRect(
                            color = Color(0xFF4FC3F7),
                            topLeft = Offset(i * barW + 6f, size.height - h),
                            size = androidx.compose.ui.geometry.Size(barW - 12f, h)
                        )
                    }
                }
                Text("配速分布（s/km）", color = Color(0xFFB8C0CC))
                Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                    val maxP = paceSec.maxOrNull()?.coerceAtLeast(1) ?: 1
                    val barW = size.width / paceSec.size
                    paceSec.forEachIndexed { i, p ->
                        val h = (p.toFloat() / maxP) * size.height
                        drawRect(
                            color = Color(0xFFFFB74D),
                            topLeft = Offset(i * barW + 6f, size.height - h),
                            size = androidx.compose.ui.geometry.Size(barW - 12f, h)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Dashboard(innerPadding: PaddingValues, viewModel: RecordViewModel) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val sampling by viewModel.sampling.collectAsStateWithLifecycle()
    val pointCount by viewModel.pointCount.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val filter by viewModel.historyFilter.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val countdown by viewModel.countdown.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {}
    )

    fun ensurePermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions += Manifest.permission.POST_NOTIFICATIONS
        val denied = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        return if (denied.isEmpty()) true else {
            permissionLauncher.launch(denied.toTypedArray())
            false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF101114)).padding(innerPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1E24))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("录制状态：$status", style = MaterialTheme.typography.titleMedium)
                Text("采样间隔：${sampling.intervalMs / 1000}s")
                Text("模式：${if (settings.powerSave) "省电" else "标准"} | 单位：${settings.unit} | 自动暂停：${if (settings.autoPause) "开" else "关"}")
                Text("地图供应商：${settings.mapVendor} | 云同步：${if (settings.cloudSync) "开" else "关"} | AI：${if (settings.aiInsight) "开" else "关"}")
                Text("运动类型：${settings.activityType} · 点位数：$pointCount")
                Text(viewModel.summary())
                Text(viewModel.liveSummary())
                if (countdown > 0) Text("倒计时: ${countdown}s", color = Color(0xFFFFD54F))
                Text(viewModel.batteryScoreHint())
                Text(viewModel.weeklySummary())
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1E24))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("提示：$message")
                Text("已实现：权限请求、前台服务、真实GPS、本地存储、GPX导出")
                Text("指标：开始${metrics.startCount} 完成${metrics.finishCount} 分享${metrics.shareCount}")
                Text(viewModel.pdcaSelfCheck())
                Text(viewModel.splitPreview())
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1E24))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🩺 自检报告", color = Color.White, style = MaterialTheme.typography.titleMedium)
                val score = viewModel.selfCheckScore()
                val levelColor = when {
                    score >= 90 -> Color(0xFF66BB6A)
                    score >= 75 -> Color(0xFFFFD54F)
                    score >= 60 -> Color(0xFFFFB74D)
                    else -> Color(0xFFEF5350)
                }
                Text("健康分: $score / 100（${viewModel.selfCheckLevel()}）", color = levelColor)
                viewModel.selfCheckItems().forEach { item ->
                    Text(item, color = Color(0xFFB8C0CC))
                }
                Text("✨ UI 细节优化建议", color = Color.White, style = MaterialTheme.typography.titleMedium)
                viewModel.uiDetailTips().forEachIndexed { i, tip ->
                    Text("${i + 1}. $tip", color = Color(0xFFB8C0CC))
                }
            }
        }


        StatsPanel(
            historyKm = history.take(7).map { it.distanceMeters / 1000f },
            paceSec = history.take(7).map { it.avgPaceSecPerKm },
            modifier = Modifier.fillMaxWidth()
        )

        Button(modifier = Modifier.fillMaxWidth().height(80.dp), onClick = {
            if (status == RecordStatus.RECORDING) {
                viewModel.startOrPause()
                context.startService(Intent(context, RecordForegroundService::class.java).apply { action = RecordForegroundService.ACTION_STOP })
            } else if (ensurePermissions()) {
                viewModel.startWithCountdown(3)
                ContextCompat.startForegroundService(context, Intent(context, RecordForegroundService::class.java))
            }
        }) {
            Text(if (status == RecordStatus.RECORDING) "暂停记录" else "开始记录")
        }
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            viewModel.stop()
            context.startService(Intent(context, RecordForegroundService::class.java).apply { action = RecordForegroundService.ACTION_STOP })
        }) { Text("结束并保存") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.togglePowerMode() }) { Text("切换省电/标准") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.quickMark() }) { Text("快速打点") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.toggleUnit() }) { Text("切换 km/mile") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.toggleAutoPause() }) { Text("切换自动暂停") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.cycleActivityType() }) { Text("切换运动类型") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.cycleMapVendor() }) { Text("切换地图API") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.toggleCloudSync() }) { Text("P2-切换云同步") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.toggleAiInsight() }) { Text("P2-切换AI洞察") }
        Text("平滑系数: %.2f".format(settings.smoothFactor), color = Color.White)
        Slider(value = settings.smoothFactor, onValueChange = { viewModel.updateSmoothFactor(it) }, valueRange = 0.1f..0.5f)
        Text("突变速度阈值: %.1f m/s".format(settings.maxJumpSpeed), color = Color.White)
        Slider(value = settings.maxJumpSpeed, onValueChange = { viewModel.updateMaxJumpSpeed(it) }, valueRange = 4f..25f)

        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, viewModel.shareText())
            }
            context.startActivity(Intent.createChooser(intent, "分享运动成果"))
        }) { Text("一键分享") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            val gpx = viewModel.exportLastGpx()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, gpx)
            }
            context.startActivity(Intent.createChooser(intent, "导出 GPX"))
        }) { Text("导出 GPX") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.clearHistory() }) { Text("清空历史") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            val msg = viewModel.exportLastGpxToFile()
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }) { Text("导出GPX到文件") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.resumeDraftIfAny() }) { Text("恢复草稿提示") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.cycleHistoryFilter() }) { Text("历史筛选: ${filter ?: "全部"}") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.toggleFavoritesOnly() }) { Text(if (favoritesOnly) "仅看收藏: 开" else "仅看收藏: 关") }

        Text("🏃 历史记录", color = Color.White, style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(viewModel.filteredHistory.take(3)) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("轨迹摘要", color = Color(0xFFB8C0CC))
                        Text(text = "${item.activityType} · ${item.id.take(8)} · %.2fkm".format(item.distanceMeters / 1000f))
                        Button(onClick = { viewModel.selectSession(item.id) }) { Text("查看详情") }
                        Button(onClick = { viewModel.toggleFavorite(item.id) }) { Text(if (item.isFavorite) "取消收藏" else "收藏") }
                    }
                }
            }
        }

        selected?.let { detail ->
            val focusIndex = remember(detail.id) { mutableIntStateOf(0) }
            val speeds = detail.points.map { it.speed }
            val clamped = focusIndex.intValue.coerceIn(0, (speeds.size - 1).coerceAtLeast(0))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val avgAcc = if (detail.points.isEmpty()) 0f else detail.points.map { it.accuracy }.average()
                    val maxSpeed = detail.points.maxOfOrNull { it.speed } ?: 0f
                    Text("详情：${detail.activityType} / 点数 ${detail.points.size}")
                    Text("距离 %.2fkm 配速 ${detail.avgPaceSecPerKm}s/km".format(detail.distanceMeters / 1000f))
                    Text("质量：平均精度 %.1fm / 最高速度 %.1fm/s".format(avgAcc, maxSpeed))
                    Text("地图回放：${viewModel.mapSummary(detail.points)}")
                    MultiMapTrackView(vendor = settings.mapVendor, points = detail.points, modifier = Modifier.fillMaxWidth().height(180.dp))
                    Text("速度曲线：${speedSparkline(speeds)}")
                    if (speeds.isNotEmpty()) {
                        Slider(value = clamped.toFloat(), onValueChange = { focusIndex.intValue = it.toInt() }, valueRange = 0f..(speeds.size - 1).toFloat())
                        Text("选中点速度：%.2f m/s".format(speeds[clamped]))
                    }
                }
            }
        }
    }
}
