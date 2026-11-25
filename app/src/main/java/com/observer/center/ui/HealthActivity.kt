package com.observer.center.ui

import android.os.Bundle
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

/** 健康状态：/admin/health + /admin/presence（在线管理员） */
class HealthActivity : AppCompatActivity() {
    private lateinit var b: ActivitySimpleBinding

    private fun JsonObject.obj(k: String): JsonObject? =
        get(k)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(k: String): JsonArray =
        (get(k)?.takeIf { it.isJsonArray }?.asJsonArray) ?: JsonArray()

    private fun jl(o: JsonObject?, k: String): String? =
        o?.get(k)?.takeIf { !it.isJsonNull }?.asString

    private fun ji(o: JsonObject?, k: String): Int = o?.get(k)?.asInt ?: 0

    private fun fmtMs(ms: Long): String {
        if (ms <= 0) return "—"
        return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
        b.title.text = "健康状态"
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val health = GsonHolder.gson.fromJson(
                    Api.post("/plugin/ObservationCenter/admin/health"), JsonObject::class.java
                )
                var admins: JsonArray? = null
                try {
                    val tab = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                    val p = GsonHolder.gson.fromJson(
                        Api.post("/plugin/ObservationCenter/admin/presence", mapOf("tab" to tab, "visible" to "1")),
                        JsonObject::class.java
                    )
                    admins = p?.arr("admins")
                } catch (_: Exception) {
                }
                render(health ?: return@launch, admins)
            } catch (e: Exception) {
                Toast.makeText(this@HealthActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun render(h: JsonObject, admins: JsonArray?) {
        b.content.removeAllViews()

        Views.section(b.content, "插件")
        val hc = Views.card(b.content)
        Views.info(hc, "版本", jl(h, "version"))
        Views.info(hc, "数据表", if (h.get("schema_ready")?.asBoolean == true) "就绪" else "缺失（停用再启用重建）")
        Views.info(hc, "当前在线", ji(h, "online").toString())
        val streams = h.obj("streams")
        if (streams != null) {
            Views.info(hc, "SSE 连接", "后台 ${ji(streams, "admin")} · 前台 ${ji(streams, "chat")}")
        }

        val geo = h.obj("geo")
        if (geo != null) {
            Views.section(b.content, "IP 归属库")
            val gc = Views.card(b.content)
            Views.info(gc, "启用", if (geo.get("enabled")?.asBoolean == true) "是" else "否")
            Views.info(gc, "就绪", if (geo.get("ready")?.asBoolean == true) "已加载" else "未加载")
            Views.info(gc, "来源", jl(geo, "source")?.ifEmpty { "—" })
            val size = ji(geo, "size")
            Views.info(gc, "大小", if (size > 0) "${size / 1024} KB" else "—")
            Views.info(gc, "更新", fmtMs(geo.get("mtime")?.asLong ?: 0L))
        }

        Views.section(b.content, "在线管理员（${admins?.size() ?: 0}）")
        if (admins == null || admins.size() == 0) {
            Views.empty(b.content, "当前没有其他管理员在线")
        } else {
            for (i in 0 until admins.size()) {
                val a = admins[i].asJsonObject
                val vis = a.get("visible")?.asBoolean == true
                val view = jl(a, "view")?.ifEmpty { null }
                Views.listItem(b.content,
                    (a.get("name")?.asString ?: "#${ji(a, "id")}") + if (vis) " · 前台" else " · 后台",
                    listOfNotNull(view?.let { "正在看 $it" }, fmtMs(a.get("seen")?.asLong ?: 0L)).joinToString(" · ")
                )
            }
        }
    }
}
