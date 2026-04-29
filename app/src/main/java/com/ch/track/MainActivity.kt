package com.ch.track

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ch.track.domain.RecordStatus
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

@Composable
private fun Dashboard(innerPadding: PaddingValues, viewModel: RecordViewModel) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val sampling by viewModel.sampling.collectAsStateWithLifecycle()
    val pointCount by viewModel.pointCount.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF101114)).padding(innerPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("录制状态：$status", style = MaterialTheme.typography.titleMedium)
                Text("采样间隔：${sampling.intervalMs / 1000}s")
                Text("模式：${if (settings.powerSave) "省电" else "标准"} | 单位：${settings.unit} | 自动暂停：${if (settings.autoPause) "开" else "关"}")
                Text("运动类型：${settings.activityType} · 点位数：$pointCount")
                Text(viewModel.summary())
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("提示：$message")
                Text("实用补齐：自动暂停开关、运动类型切换、历史清理、GPX导出")
            }
        }

        Button(modifier = Modifier.fillMaxWidth().height(80.dp), onClick = { viewModel.startOrPause() }) {
            Text(if (status == RecordStatus.RECORDING) "暂停记录" else "开始记录")
        }
        Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.stop() }) { Text("结束并保存") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.togglePowerMode() }) { Text("切换省电/标准") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.toggleUnit() }) { Text("切换 km/mile") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.toggleAutoPause() }) { Text("切换自动暂停") }
        Button(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.cycleActivityType() }) { Text("切换运动类型") }

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

        Text("历史记录", color = Color.White)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(history.take(3)) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        modifier = Modifier.padding(12.dp),
                        text = "${item.activityType} · ${item.id.take(8)} · %.2fkm · ${item.points.size}点".format(item.distanceMeters / 1000f)
                    )
                }
            }
        }
    }
}
