package com.observer.center.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
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

/** 设备详情：/admin/device?fp=，可封锁/解封设备 */
class DeviceActivity : AppCompatActivity() {
    private lateinit var b: ActivitySimpleBinding
    private var fp = 0
    private var cur: JsonObject? = null

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(key: String): JsonArray =
        (get(key)?.takeIf { it.isJsonArray }?.asJsonArray) ?: JsonArray()

    private fun fmt(ms: Long): String = if (ms <= 0) "—" else
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(b.root)
        fp = intent.getIntExtra("fp", 0)
        b.btnBack.setOnClickListener { finish() }
        b.title.text = "设备详情"
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/device", mapOf("fp" to fp.toString()))
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return@launch
                cur = o.obj("device")
                render(cur ?: return@launch)
            } catch (e: Exception) {
                Toast.makeText(this@DeviceActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun render(d: JsonObject) {
        b.content.removeAllViews()

        Views.section(b.content, "设备")
        val card = Views.card(b.content)
        Views.info(card, "标签", d.get("label")?.asString)
        Views.info(card, "短号", d.get("short")?.asString)
        Views.info(card, "层级", d.get("level")?.asString)
        Views.info(card, "识别", d.get("summary")?.asString)
        Views.info(card, "首次", fmt(d.get("first")?.asLong ?: 0L))
        Views.info(card, "最近", fmt(d.get("last")?.asLong ?: 0L))
        Views.info(card, "访问次数", (d.get("hits")?.asInt ?: 0).toString())
        Views.info(card, "计数", "访客 ${d.get("vid_count")?.asInt ?: 0} · 会员 ${d.get("uid_count")?.asInt ?: 0} · IP ${d.get("ip_count")?.asInt ?: 0}")
        val why = d.get("why")?.asString
        if (!why.isNullOrEmpty()) Views.info(card, "特征", why)

        // 封禁状态
        val blocked = d.obj("blocked")
        if (blocked != null) {
            Views.section(b.content, "封锁状态")
            val bc = Views.card(b.content)
            Views.info(bc, "原因", blocked.get("reason")?.asString)
            Views.info(bc, "操作人", blocked.get("by")?.asString)
            Views.info(bc, "开始", fmt(blocked.get("created_at")?.asLong ?: 0L))
            Views.info(bc, "到期", fmt(blocked.get("expires_at")?.asLong ?: 0L))
            Views.listItem(b.content, "解除设备封锁", "") { unblockDevice() }
        } else {
            // 封锁表单
            Views.section(b.content, "封锁此设备")
            val form = Views.card(b.content)
            val reason = EditText(this).apply {
                hint = "原因（选填）"
                setBackgroundResource(com.observer.center.R.drawable.bg_input)
                setPadding(28, 20, 28, 20)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 }
            }
            val minutes = EditText(this).apply {
                hint = "时长（分钟，留空=永久）"
                inputType = InputType.TYPE_CLASS_NUMBER
                setBackgroundResource(com.observer.center.R.drawable.bg_input)
                setPadding(28, 20, 28, 20)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 }
            }
            form.addView(reason); form.addView(minutes)
            val btn = TextView(this).apply {
                text = "封锁设备"
                gravity = android.view.Gravity.CENTER
                textSize = 15f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundResource(com.observer.center.R.drawable.bg_btn)
                setPadding(0, 22, 0, 22)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 }
                setOnClickListener { blockDevice(reason.text.toString().trim(), minutes.text.toString().trim()) }
            }
            form.addView(btn)
        }

        // 关联 IP
        val ips = d.arr("ips")
        Views.section(b.content, "使用过的 IP（${ips.size()}）")
        for (i in 0 until ips.size()) {
            val it = ips[i].asJsonObject
            Views.listItem(b.content,
                it.get("ip")?.asString ?: "",
                it.getAsJsonObject("region")?.let { r ->
                    listOfNotNull(r.get("country")?.asString?.takeIf { x -> x.isNotEmpty() }, r.get("city")?.asString?.takeIf { x -> x.isNotEmpty() }).joinToString(" ")
                } ?: ""
            )
        }

        // 关联会员
        val members = d.arr("members")
        Views.section(b.content, "关联会员（${members.size()}）")
        for (i in 0 until members.size()) {
            val it = members[i].asJsonObject
            Views.listItem(b.content,
                it.get("username")?.asString ?: "会员 #${it.get("uid")?.asInt ?: 0}",
                "UID ${it.get("uid")?.asInt ?: 0} · 访问 ${it.get("hits")?.asInt ?: 0} 次"
            ) {
                startActivity(Intent(this, MemberActivity::class.java).putExtra("uid", it.get("uid").asInt))
            }
        }

        // 关联访客
        val visitors = d.arr("visitors")
        Views.section(b.content, "关联访客（${visitors.size()}）")
        for (i in 0 until visitors.size()) {
            val it = visitors[i].asJsonObject
            Views.listItem(b.content,
                it.get("ip")?.asString ?: "访客",
                "最近 ${fmt(it.get("last_seen_ms")?.asLong ?: 0L)} · 点查看"
            ) {
                val v = it.get("vid")?.asString ?: ""
                if (v.isNotEmpty()) startActivity(Intent(this, VisitorActivity::class.java).putExtra("vid", v))
            }
        }
    }

    private fun blockDevice(reason: String, minutes: String) {
        lifecycleScope.launch {
            try {
                Api.post("/plugin/ObservationCenter/admin/blockDevice", mapOf(
                    "fp" to fp.toString(), "reason" to reason, "minutes" to minutes.ifEmpty { "0" }
                ))
                Toast.makeText(this@DeviceActivity, "已封锁", Toast.LENGTH_SHORT).show()
                load()
            } catch (e: Exception) {
                Toast.makeText(this@DeviceActivity, e.message ?: "操作失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun unblockDevice() {
        lifecycleScope.launch {
            try {
                Api.post("/plugin/ObservationCenter/admin/unblockDevice", mapOf("fp" to fp.toString()))
                Toast.makeText(this@DeviceActivity, "已解除封锁", Toast.LENGTH_SHORT).show()
                load()
            } catch (e: Exception) {
                Toast.makeText(this@DeviceActivity, e.message ?: "操作失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
