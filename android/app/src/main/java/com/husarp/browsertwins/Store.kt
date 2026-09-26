package com.husarp.browsertwins

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// One clone: a copy of an app, with its own data. sourcePkg is the app it was made from
// (e.g. org.mozilla.firefox); clonePkg is the copy's package name (e.g. org.mozilla.firefox.bt1).
// hue/strength/brightness recolour the icon (as in LinkPilot). inMenu: shown in the long-press menu.
data class Profile(
    val id: String,
    val name: String,
    val sourcePkg: String,
    val clonePkg: String,
    val madeFromVersion: String,
    val hue: Int = 0,
    val strength: Int = 100,
    val brightness: Int = 100,
    val inMenu: Boolean = true,
)

// One line in the log: what happened, when.
class LogEntry(val time: String, val text: String, val detail: String)

// Everything Browser Twins keeps, on this phone only: settings + profiles in the app's preferences,
// the log in its own file. Same shape as LinkPilot's Store.
class Store(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("config", Context.MODE_PRIVATE)
    private val logFile = File(context.applicationContext.filesDir, "log.txt")

    var profiles: List<Profile>
        get() = objects("profiles").map {
            Profile(
                it.getString("id"), it.getString("name"), it.getString("sourcePkg"),
                it.getString("clonePkg"), it.optString("madeFromVersion"),
                it.optInt("hue"), it.optInt("strength", 100), it.optInt("brightness", 100),
                it.optBoolean("inMenu", true),
            )
        }
        set(v) = put("profiles", v.map { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("sourcePkg", p.sourcePkg)
                .put("clonePkg", p.clonePkg).put("madeFromVersion", p.madeFromVersion)
                .put("hue", p.hue).put("strength", p.strength).put("brightness", p.brightness)
                .put("inMenu", p.inMenu)
        })

    var setupDone by flag("setupDone", false)
    var autoUpdate by flag("autoUpdate", true)
    var onlyWhenCharging by flag("onlyWhenCharging", false)
    var notify by flag("notify", true)
    var logOn by flag("logOn", true)

    // ---- the log: newest 300 ----

    fun addLog(text: String, detail: String = "") {
        if (!logOn) return
        try {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date())
            val line = listOf(time, text, detail)
                .joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ') }
            logFile.appendText(line + "\n")
            val lines = logFile.readLines()
            if (lines.size > 400) logFile.writeText(lines.takeLast(300).joinToString("\n", postfix = "\n"))
        } catch (_: Exception) {
        }
    }

    fun readLog(): List<LogEntry> = try {
        logFile.readLines().mapNotNull { line ->
            val b = line.split('\t')
            if (b.size < 2) null else LogEntry(b[0], b[1], b.getOrElse(2) { "" })
        }.reversed()
    } catch (_: Exception) {
        emptyList()
    }

    fun clearLog() { logFile.delete() }

    // ---- helpers (as in LinkPilot) ----

    private fun objects(key: String): List<JSONObject> {
        val a = try { JSONArray(prefs.getString(key, "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }

    private fun put(key: String, list: List<JSONObject>) {
        val a = JSONArray()
        list.forEach { a.put(it) }
        prefs.edit().putString(key, a.toString()).apply()
    }

    private fun flag(key: String, default: Boolean) = object : kotlin.properties.ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = prefs.getBoolean(key, default)
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Boolean) =
            prefs.edit().putBoolean(key, value).apply()
    }
}
