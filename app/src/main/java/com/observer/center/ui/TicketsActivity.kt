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

/** 工单列表：/admin/tickets，点击认领/放下（key=ticket:id） */
class TicketsActivity : AppCompatActivity() {
    private lateinit var b: ActivitySimpleBinding
    private val items = ArrayList<JsonObject>()
    private var page = 1
    private var total = 0
    private var claims: JsonObject? = null
    private var meId = 0

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
                refreshClaims()
                render()
            } catch (e: Exception) {
                Toast.makeText(this@TicketsActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun refreshClaims() {
        try {
            val tab = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12)
            val body = Api.post("/plugin/ObservationCenter/admin/presence", mapOf("tab" to tab, "visible" to "1"))
            val o = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return
            meId = o.get("me")?.asInt ?: 0
            claims = o.getAsJsonObject("claims")
        } catch (_: Exception) {
        }
    }

    private fun toggleClaim(ticketId: Int) {
        val key = "ticket:$ticketId"
        val cur = claims?.getAsJsonObject(key)
        val mine = cur != null && cur.get("by")?.asInt == meId
        lifecycleScope.launch {
            try {
                Api.post("/plugin/ObservationCenter/admin/claim", mapOf("key" to key, "on" to if (mine) "0" else "1"))
                Toast.makeText(this@TicketsActivity, if (mine) "已放下" else "已认领接手", Toast.LENGTH_SHORT).show()
                refreshClaims()
                render()
            } catch (e: Exception) {
                Toast.makeText(this@TicketsActivity, e.message ?: "操作失败", Toast.LENGTH_SHORT).show()
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
            val tid = t.get("id")?.asInt ?: 0
            val user = t.getAsJsonObject("user")
            val uname = user?.get("username")?.asString?.takeIf { it.isNotEmpty() } ?: "用户 #${user?.get("id")?.asInt ?: 0}"
            val from = if (t.get("last_from")?.asString == "admin") "客服留言" else "用户留言"
            val c = claims?.getAsJsonObject("ticket:$tid")
            val claimTag = if (c != null) {
                val byMe = c.get("by")?.asInt == meId
                " · ${c.get("name")?.asString ?: ""}${if (byMe) "(我)" else ""}处理中"
            } else ""
            Views.listItem(b.content,
                "#$tid ${t.get("title")?.asString ?: ""}",
                "$uname · ${t.get("status_text")?.asString ?: ""} · $from · ${t.get("updated_at")?.asString ?: ""}$claimTag"
            ) { toggleClaim(tid) }
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
