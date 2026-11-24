package com.observer.center.ui

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

/** 工单列表：/admin/tickets（只读） */
class TicketsActivity : AppCompatActivity() {
    private lateinit var b: ActivitySimpleBinding
    private val items = ArrayList<JsonObject>()
    private var page = 1
    private var total = 0

    private fun JsonObject.arr(key: String): JsonArray =
        (get(key)?.takeIf { it.isJsonArray }?.asJsonArray) ?: JsonArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
        b.title.text = "工单"
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/tickets", mapOf(
                    "filter" to "open", "page" to page.toString()
                ))
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return@launch
                val list = o.arr("list")
                total = o.get("total")?.asInt ?: 0
                if (page == 1) items.clear()
                for (i in 0 until list.size()) items.add(list[i].asJsonObject)
                render()
            } catch (e: Exception) {
                Toast.makeText(this@TicketsActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun render() {
        b.content.removeAllViews()
        if (items.isEmpty()) {
            Views.empty(b.content, "暂无待处理工单")
            return
        }
        for (t in items) {
            val user = t.getAsJsonObject("user")
            val uname = user?.get("username")?.asString?.takeIf { it.isNotEmpty() } ?: "用户 #${user?.get("id")?.asInt ?: 0}"
            val from = if (t.get("last_from")?.asString == "admin") "客服留言" else "用户留言"
            Views.listItem(b.content,
                "#${t.get("id")?.asInt ?: 0} ${t.get("title")?.asString ?: ""}",
                "$uname · ${t.get("status_text")?.asString ?: ""} · $from · ${t.get("updated_at")?.asString ?: ""}"
            )
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
