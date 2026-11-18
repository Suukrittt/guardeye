package com.guardeye.app
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistent scan history (SharedPreferences JSON). Stores results only - never screenshots or screen text. */
object HistoryStore {
    private fun prefs(c: Context) = c.getSharedPreferences("guardeye", Context.MODE_PRIVATE)
    @Synchronized fun add(c: Context, r: Result, method: String) {
        val arr = JSONArray(prefs(c).getString("h", "[]"))
        val o = JSONObject().put("ts", System.currentTimeMillis()).put("cat", r.category).put("risk", r.risk)
            .put("score", r.score).put("why", r.reasons.joinToString("; ")).put("method", method)
        val out = JSONArray().put(o); for (i in 0 until minOf(arr.length(), 499)) out.put(arr.get(i))
        prefs(c).edit().putString("h", out.toString()).apply()
    }
    fun all(c: Context): List<JSONObject> { val a = JSONArray(prefs(c).getString("h", "[]")); return (0 until a.length()).map { a.getJSONObject(it) } }
    fun clear(c: Context) = prefs(c).edit().remove("h").apply()
}
