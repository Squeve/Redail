package com.squeve.redail.data

import android.content.Context
import android.content.SharedPreferences
import com.squeve.redail.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A plan <-> JSON, used for "last run", schedules and crash recovery. */
object PlanCodec {
    fun toJson(plan: RunPlan): JSONObject = JSONObject().apply {
        put("order", plan.order.ordinal)
        put("sim", plan.simSlot)
        put("dry", plan.dry)
        put("jobs", JSONArray().apply {
            plan.jobs.forEach { j ->
                put(
                    JSONObject().apply {
                        put("n", j.number)
                        put("a", j.attempts)
                        put("g", j.gapMs)
                        put("gm", j.gapMaxMs ?: -1L)
                        put("h", j.hangUpAfterMs ?: -1L)
                        put("s", j.stopOnConnected)
                        put("sa", j.stopAfterAnswers)
                        put("sp", j.speaker)
                        put("m", j.muteMic)
                    },
                )
            }
        })
    }

    fun fromJson(o: JSONObject): RunPlan {
        val arr = o.getJSONArray("jobs")
        val jobs = (0 until arr.length()).map { i ->
            val j = arr.getJSONObject(i)
            RedialJob(
                number = j.getString("n"),
                attempts = j.getInt("a"),
                gapMs = j.getLong("g"),
                gapMaxMs = j.optLong("gm", -1L).takeIf { it >= 0 },
                hangUpAfterMs = j.optLong("h", -1L).takeIf { it >= 0 },
                stopOnConnected = j.optBoolean("s", false),
                stopAfterAnswers = j.optInt("sa", 1),
                speaker = j.optBoolean("sp", false),
                muteMic = j.optBoolean("m", false),
            )
        }
        val order = CallOrder.values().getOrElse(o.optInt("order", 0)) { CallOrder.SEQUENTIAL }
        return RunPlan(jobs, order, o.optInt("sim", -1), o.optBoolean("dry", false))
    }
}

data class Checkpoint(val plan: RunPlan, val done: List<Int>) {
    val totalCalls: Int get() = plan.jobs.sumOf { it.attempts }
    val doneCalls: Int get() = done.sum()
}

data class Scheduled(val atMs: Long, val plan: RunPlan)

/** Last run (for shortcuts / tile / widget), unfinished-run checkpoint and the scheduled run. */
object RunStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("redail_run", Context.MODE_PRIVATE)

    private fun readJson(ctx: Context, key: String): JSONObject? {
        val raw = prefs(ctx).getString(key, null) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    fun saveLast(ctx: Context, plan: RunPlan) {
        prefs(ctx).edit().putString("last", PlanCodec.toJson(plan).toString()).apply()
    }

    fun loadLast(ctx: Context): RunPlan? =
        readJson(ctx, "last")?.let { runCatching { PlanCodec.fromJson(it) }.getOrNull() }

    fun saveCheckpoint(ctx: Context, plan: RunPlan, done: List<Int>) {
        val o = JSONObject().put("plan", PlanCodec.toJson(plan)).put("done", JSONArray(done))
        prefs(ctx).edit().putString("checkpoint", o.toString()).apply()
    }

    fun loadCheckpoint(ctx: Context): Checkpoint? {
        val o = readJson(ctx, "checkpoint") ?: return null
        return runCatching {
            val arr = o.getJSONArray("done")
            val done = (0 until arr.length()).map { arr.getInt(it) }
            val cp = Checkpoint(PlanCodec.fromJson(o.getJSONObject("plan")), done)
            if (remainingPlan(cp) == null) null else cp
        }.getOrNull()
    }

    fun clearCheckpoint(ctx: Context) {
        prefs(ctx).edit().remove("checkpoint").apply()
    }

    /** What is left of an unfinished run: each number keeps only the calls it has not had yet. */
    fun remainingPlan(cp: Checkpoint): RunPlan? {
        val jobs = cp.plan.jobs.mapIndexedNotNull { i, j ->
            val left = j.attempts - (cp.done.getOrNull(i) ?: 0)
            if (left > 0) j.copy(attempts = left) else null
        }
        return if (jobs.isEmpty()) null else cp.plan.copy(jobs = jobs)
    }

    fun saveSchedule(ctx: Context, atMs: Long, plan: RunPlan) {
        val o = JSONObject().put("at", atMs).put("plan", PlanCodec.toJson(plan))
        prefs(ctx).edit().putString("schedule", o.toString()).apply()
    }

    fun loadSchedule(ctx: Context): Scheduled? {
        val o = readJson(ctx, "schedule") ?: return null
        return runCatching { Scheduled(o.getLong("at"), PlanCodec.fromJson(o.getJSONObject("plan"))) }.getOrNull()
    }

    fun clearSchedule(ctx: Context) {
        prefs(ctx).edit().remove("schedule").apply()
    }
}

/** number -> display name, stored by the UI as "number|name" lines. */
object NameStore {
    fun load(prefs: SharedPreferences): Map<String, String> {
        val out = HashMap<String, String>()
        prefs.getString("names", "")?.split("\n")?.forEach { line ->
            val i = line.indexOf('|')
            if (i > 0) out[line.substring(0, i)] = line.substring(i + 1)
        }
        return out
    }
}

/** Named, saved queues with their settings. */
object ListStore {
    private const val KEY = "lists"

    fun all(prefs: SharedPreferences): List<Pair<String, JSONObject>> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                o.getString("name") to o.getJSONObject("data")
            }
        }.getOrDefault(emptyList())
    }

    private fun write(prefs: SharedPreferences, items: List<Pair<String, JSONObject>>) {
        val arr = JSONArray()
        items.forEach { (name, data) -> arr.put(JSONObject().put("name", name).put("data", data)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun put(prefs: SharedPreferences, name: String, data: JSONObject) {
        write(prefs, all(prefs).filter { it.first != name } + (name to data))
    }

    fun remove(prefs: SharedPreferences, name: String) {
        write(prefs, all(prefs).filter { it.first != name })
    }

    fun toJson(prefs: SharedPreferences): JSONArray {
        val arr = JSONArray()
        all(prefs).forEach { (name, data) -> arr.put(JSONObject().put("name", name).put("data", data)) }
        return arr
    }

    fun replaceAll(prefs: SharedPreferences, arr: JSONArray) {
        val items = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name")
            val data = o.optJSONObject("data")
            if (name.isNotBlank() && data != null) name to data else null
        }
        write(prefs, items)
    }
}

data class HistoryRecord(
    val ts: Long,
    val number: String,
    val name: String,
    val outcome: String,
    val durationSec: Long,
    val sim: Int,
)

/** Every real call placed, one JSON object per line, newest at the end of the file. */
object HistoryStore {
    private val lock = Any()
    private fun file(ctx: Context) = File(ctx.filesDir, "history.jsonl")

    fun append(ctx: Context, r: AttemptResult, name: String) {
        synchronized(lock) {
            val f = file(ctx)
            val line = JSONObject().apply {
                put("t", r.timestamp)
                put("n", r.number)
                put("m", name)
                put("o", r.outcome.name)
                put("d", r.durationSec)
                put("s", r.simSlot)
            }.toString()
            f.appendText(line + "\n")
            if (f.length() > 900_000) {
                val keep = f.readLines().takeLast(4000)
                f.writeText(keep.joinToString("\n") + "\n")
            }
        }
    }

    fun load(ctx: Context, max: Int = 600): List<HistoryRecord> = synchronized(lock) {
        val f = file(ctx)
        if (!f.exists()) {
            emptyList()
        } else {
            f.readLines().takeLast(max).mapNotNull { line ->
                runCatching {
                    val o = JSONObject(line)
                    HistoryRecord(
                        o.getLong("t"), o.getString("n"), o.optString("m"),
                        o.getString("o"), o.optLong("d", 0), o.optInt("s", -1),
                    )
                }.getOrNull()
            }.reversed()
        }
    }

    fun clear(ctx: Context) {
        synchronized(lock) { file(ctx).delete() }
    }

    /** Writes a CSV into the cache folder and returns it, ready to share. */
    fun exportCsv(ctx: Context): File {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val out = File(dir, "squeve-history.csv")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder("time,number,name,result,seconds,sim\n")
        load(ctx, 100_000).reversed().forEach { r ->
            val name = "\"" + r.name.replace("\"", "\"\"") + "\""
            val sim = if (r.sim >= 0) "SIM ${r.sim + 1}" else ""
            sb.append(fmt.format(Date(r.ts))).append(',').append(r.number).append(',').append(name)
                .append(',').append(r.outcome).append(',').append(r.durationSec).append(',').append(sim).append('\n')
        }
        out.writeText(sb.toString())
        return out
    }
}

/** Live feed of what the engine is doing, newest last. */
object EventLog {
    val lines = MutableStateFlow<List<String>>(emptyList())
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun add(msg: String) {
        val stamp = synchronized(fmt) { fmt.format(Date()) }
        lines.update { (it + "$stamp  $msg").takeLast(300) }
    }

    fun clear() {
        lines.value = emptyList()
    }
}

/** Calls placed today, for the optional daily cap. */
object Limits {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("redail_limits", Context.MODE_PRIVATE)
    private fun today(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    fun callsToday(ctx: Context): Int {
        val p = prefs(ctx)
        return if (p.getString("day", "") == today()) p.getInt("count", 0) else 0
    }

    fun bump(ctx: Context) {
        val p = prefs(ctx)
        val d = today()
        val n = if (p.getString("day", "") == d) p.getInt("count", 0) else 0
        p.edit().putString("day", d).putInt("count", n + 1).apply()
    }
}
