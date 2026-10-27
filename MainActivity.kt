package com.trip.itinerary

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

data class Item(val time: String, val duration: String, val title: String, val note: String,
                val budget: Int, val start: String, val place: String)
data class Day(val title: String, val date: String, val items: List<Item>)

fun loadDays(ctx: Context): List<Day> {
    val root = JSONObject(ctx.assets.open("itinerary.json").bufferedReader().readText())
    val arr = root.getJSONArray("days")
    return (0 until arr.length()).map { d ->
        val o = arr.getJSONObject(d)
        val its = o.getJSONArray("items")
        Day(o.getString("title"), o.getString("date"), (0 until its.length()).map { i ->
            val x = its.getJSONObject(i)
            Item(x.getString("time"), x.getString("duration"), x.getString("title"), x.getString("note"),
                x.getInt("budget"), x.optString("start", ""), x.optString("place", ""))
        })
    }
}

fun rp(n: Int): String = "Rp " + NumberFormat.getNumberInstance(Locale("id", "ID")).format(n)

fun routeUrl(places: List<String>): String {
    val e = { s: String -> Uri.encode(s) }
    var u = "https://www.google.com/maps/dir/?api=1&origin=${e(places.first())}&destination=${e(places.last())}"
    val mid = places.drop(1).dropLast(1)
    if (mid.isNotEmpty()) u += "&waypoints=" + mid.joinToString("%7C") { e(it) }
    return u + "&travelmode=driving"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    val days = remember { loadDays(ctx) }
    val prefs = remember { ctx.getSharedPreferences("trip", Context.MODE_PRIVATE) }
    val done = remember { mutableStateListOf<String>().apply { addAll(prefs.getStringSet("done", emptySet())!!) } }
    var tab by remember { mutableIntStateOf(0) }
    var mode by remember { mutableIntStateOf(prefs.getInt("theme", 0)) }      // 0 sistem, 1 terang, 2 gelap
    var remind by remember { mutableStateOf(prefs.getBoolean("remind", false)) }
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }

    fun setRemind(on: Boolean) {
        remind = on
        prefs.edit().putBoolean("remind", on).apply()
        if (on) {
            Reminders.schedule(ctx, days)
            Toast.makeText(ctx, "Pengingat aktif: 10 menit sebelum tiap aktivitas", Toast.LENGTH_SHORT).show()
        } else Reminders.cancel(ctx, days)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) setRemind(true)
    }
    fun toggleRemind() {
        if (remind) setRemind(false)
        else if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else setRemind(true)
    }

    val day = days[tab]
    val total = days.sumOf { d -> d.items.sumOf { it.budget } }
    val places = day.items.map { it.place }.filter { it.isNotEmpty() }
        .fold(listOf<String>()) { a, p -> if (a.lastOrNull() == p) a else a + p }.take(10)

    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("Bandung – Jogja 2026") }, actions = {
                TextButton({ mode = (mode + 1) % 3; prefs.edit().putInt("theme", mode).apply() }) {
                    Text(listOf("Sistem", "Terang", "Gelap")[mode])
                }
                TextButton({ toggleRemind() }) { Text(if (remind) "🔔" else "🔕") }
            })
        }) { pad ->
            Column(Modifier.padding(pad)) {
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    days.forEachIndexed { i, d -> Tab(tab == i, { tab = i }, text = { Text(d.title) }) }
                }
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(day.date, style = MaterialTheme.typography.labelMedium)
                        Text("Budget hari ini: ${rp(day.items.sumOf { it.budget })}", fontWeight = FontWeight.Bold)
                        Text("Total perjalanan: ${rp(total)}", style = MaterialTheme.typography.bodySmall)
                        if (places.size >= 2) {
                            Spacer(Modifier.height(8.dp))
                            Button({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(routeUrl(places)))) }) {
                                Text("Rute hari ini (${places.size} titik)")
                            }
                        }
                    }
                }
                LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp)) {
                    itemsIndexed(day.items) { idx, item ->
                        val key = "$tab-$idx"
                        val checked = key in done
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                            Checkbox(checked, { on ->
                                if (on) done.add(key) else done.remove(key)
                                prefs.edit().putStringSet("done", done.toSet()).apply()
                            })
                            Column(Modifier.weight(1f)) {
                                Text(item.time + if (item.duration.isNotEmpty()) "  ·  ${item.duration}" else "",
                                    style = MaterialTheme.typography.labelSmall)
                                Text(item.title, fontWeight = FontWeight.SemiBold,
                                    textDecoration = if (checked) TextDecoration.LineThrough else null)
                                if (item.note.isNotEmpty()) Text(item.note, style = MaterialTheme.typography.bodySmall)
                                if (item.budget > 0) Text(rp(item.budget), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            if (item.place.isNotEmpty() && !item.title.startsWith("Perjalanan", true)) {
                                TextButton({
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(item.place))))
                                }) { Text("Peta") }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
