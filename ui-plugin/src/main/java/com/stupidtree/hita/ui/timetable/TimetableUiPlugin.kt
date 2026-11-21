package com.stupidtree.hita.ui.timetable

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.stupidtree.hita.api.ComposeUiPlugin
import com.stupidtree.hita.api.PluginUiHost
import com.stupidtree.hita.api.TimetableService
import com.stupidtree.hitax.data.model.timetable.EventItem
import com.stupidtree.hitax.data.model.timetable.Timetable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * T2 timetable surface. It only reads [TimetableService]; Room and ICS stay in
 * timetable-core (T1). It renders a week grid; editing/detail pages are still
 * outside this preview.
 */
class TimetableUiPlugin : ComposeUiPlugin {
    override val id = "com.stupidtree.hita.ui.timetable"
    override val displayName = "课表"
    override val requiresApi = 11

    override fun createView(context: Context, host: PluginUiHost): View = ComposeView(context).apply {
        setContent {
            MaterialTheme { TimetableScreen(host.service(TimetableService.KEY)) }
        }
    }
}

@Composable
private fun TimetableScreen(service: TimetableService?) {
    if (service == null) {
        Text("未安装 timetable T1 core", modifier = Modifier.padding(24.dp))
        return
    }
    val timetable = service.getRecentTimetable().observeState()
    var refresh by remember { mutableStateOf(0) }
    var week by remember(timetable?.id) { mutableStateOf(1) }
    var events by remember { mutableStateOf<List<EventItem>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(timetable) {
        week = timetable?.getWeekNumber(System.currentTimeMillis())?.takeIf { it > 0 } ?: 1
        onDispose { }
    }

    LaunchedEffect(timetable?.id, week, refresh) {
        val table = timetable
        events = emptyList()
        error = null
        if (table == null) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                val from = table.startTime.time + (week - 1).toLong() * 7 * 24 * 60 * 60 * 1000
                val to = from + 7 * 24 * 60 * 60 * 1000
                service.getEventsDuringSync(from, to)
            }
        }.onSuccess { events = it.sortedBy { e -> e.from.time } }
            .onFailure { error = it.message ?: "读取失败" }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("课表 · 第 $week 周", style = MaterialTheme.typography.h6, modifier = Modifier.weight(1f))
            Button(onClick = { refresh++ }) { Text("刷新") }
        }
        Text(
            timetable?.name ?: "T1 已连接，但没有课表",
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        if (error != null) {
            Text("读取失败：$error")
            return@Column
        }
        val table = timetable
        if (table == null) return@Column
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Button(onClick = { if (week > 1) week-- }) { Text("<") }
            Text("第 $week 周", Modifier.weight(1f).padding(top = 10.dp))
            Button(onClick = { if (week < 30) week++ }) { Text(">") }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("", Modifier.width(42.dp))
            listOf("一","二","三","四","五","六","日").forEach {
                Text(it, Modifier.weight(1f), fontSize = 12.sp)
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(table.scheduleStructure.indices.toList()) { index ->
                val period = index + 1
                Row(Modifier.fillMaxWidth().height(48.dp)) {
                    Text("$period", Modifier.width(42.dp).padding(top = 4.dp), fontSize = 11.sp)
                    (1..7).forEach { day ->
                        Box(Modifier.weight(1f).padding(1.dp).background(Color(0x11000000))) {
                            events.firstOrNull { e ->
                                e.getDow() == day && e.fromNumber == period
                            }?.let { e ->
                                Card(Modifier.fillMaxSize()) {
                                    Column(Modifier.padding(2.dp)) {
                                        Text(e.name, fontSize = 10.sp, maxLines = 2)
                                        e.place?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, fontSize = 8.sp, maxLines = 1)
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
}

private fun format(ms: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

@Composable
private fun <T> LiveData<T>.observeState(): T? {
    val state = remember { mutableStateOf(value) }
    DisposableEffect(this) {
        val observer = Observer<T> { state.value = it }
        observeForever(observer)
        onDispose { removeObserver(observer) }
    }
    return state.value
}
