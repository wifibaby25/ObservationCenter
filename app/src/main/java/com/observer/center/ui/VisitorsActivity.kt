package com.observer.center.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.observer.center.api.Api
import com.observer.center.api.GsonHolder
import com.observer.center.databinding.ActivitySimpleBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 访客列表：/admin/visitorsPage */
class VisitorsActivity : AppCompatActivity() {
    private lateinit var b: ActivitySimpleBinding
    private val items = ArrayList<JsonObject>()
    private var page = 1
    private var scope = "all"
    private var total = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
        b.title.text = "访客列表"
        // 顶部筛选：在线 / 今日 / 全部
        val tabs = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }
        val labels = listOf("online" to "在线", "today" to "今日", "all" to "全部")
        labels.forEach { (key, label) ->
            val tv = TextView(this).apply {
                text = label
                textSize = 13f
                setPadding(28, 10, 28, 10)
                val bg = com.observer.center.R.drawable.bg_btn_outline
                setBackgroundResource(bg)
                setOnClickListener {
                    scope = key; page = 1; items.clear(); load()
                }
            }
            tabs.addView(tv)
        }
        b.content.addView(tabs)
        load()
    }

    private fun fmtMs(ms: Long): String {
        if (ms <= 0) return "—"
        return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
    }

    private fun JsonObject.arr(key: String): JsonArray =
        (get(key)?.takeIf { it.isJsonArray }?.asJsonArray) ?: JsonArray()

    private fun load() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/visitorsPage", mapOf(
                    "scope" to scope, "page" to page.toString()
                ))
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return@launch
                val list = o.arr("list")
                total = o.get("total")?.asInt ?: 0
                if (page == 1) items.clear()
                for (i in 0 until list.size()) items.add(list[i].asJsonObject)
                render()
            } catch (e: Exception) {
                Toast.makeText(this@VisitorsActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun render() {
        // 保留筛选 tab
        while (b.content.childCount > 1) b.content.removeViewAt(1)
        if (items.isEmpty()) {
            Views.empty(b.content, "暂无访客")
            return
        }
        for (v in items) {
            val region = v.getAsJsonObject("region")
            val geo = listOfNotNull(
                region?.get("country")?.asString?.takeIf { it.isNotEmpty() },
                region?.get("city")?.asString?.takeIf { it.isNotEmpty() }
            ).joinToString(" ")
            val hasUid = (v.get("uid")?.asInt ?: 0) > 0
            val name = v.get("name")?.asString?.takeIf { it.isNotEmpty() }
                ?: (if (hasUid) "会员" else "访客")
            Views.listItem(b.content,
                "$name · ${v.get("ip")?.asString ?: ""}",
                "$geo · ${v.get("device")?.asString ?: ""} · ${fmtMs(v.get("seen")?.asLong ?: 0L)}"
            ) {
                startActivity(Intent(this, VisitorActivity::class.java)
                    .putExtra("vid", v.get("vid")?.asString ?: ""))
            }
        }
        if (items.size < total) {
            val more = TextView(this).apply {
                text = "加载更多（${items.size}/$total）"
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 18, 0, 18)
                setOnClickListener { page++; load() }
            }
            b.content.addView(more)
        }
    }
}
