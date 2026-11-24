package com.observer.center.ui

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

/** 封禁名单：/admin/blocks，解封 unblock / unblockDevice，新增 block */
class BlocklistActivity : AppCompatActivity() {
    private lateinit var b: ActivitySimpleBinding
    private lateinit var ipInput: EditText
    private lateinit var reasonInput: EditText
    private lateinit var minInput: EditText

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(key: String): JsonArray =
        (get(key)?.takeIf { it.isJsonArray }?.asJsonArray) ?: JsonArray()

    private fun fmt(ms: Long): String = if (ms <= 0) "永久" else
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
        b.title.text = "封禁名单"
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/blocks")
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return@launch
                render(o.arr("list"))
            } catch (e: Exception) {
                Toast.makeText(this@BlocklistActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun input(hint: String, multiline: Boolean): EditText = EditText(this).apply {
        this.hint = hint
        setBackgroundResource(com.observer.center.R.drawable.bg_input)
        setPadding(28, 20, 28, 20)
        textSize = 14f
        inputType = if (multiline) InputType.TYPE_CLASS_TEXT else InputType.TYPE_CLASS_TEXT
        val lp = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 }
        layoutParams = lp
    }

    private fun render(list: JsonArray) {
        b.content.removeAllViews()

        // 新增 IP 封禁表单
        Views.section(b.content, "封禁 IP / 网段")
        val form = Views.card(b.content)
        ipInput = input("IP 地址或 CIDR 网段，如 1.2.3.0/24", false)
        reasonInput = input("原因（选填）", true)
        minInput = input("时长（分钟，留空=永久）", false)
        form.addView(ipInput); form.addView(reasonInput); form.addView(minInput)
        val btn = TextView(this).apply {
            text = "封禁该地址"
            gravity = android.view.Gravity.CENTER
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(com.observer.center.R.drawable.bg_btn)
            setPadding(0, 24, 0, 24)
            val lp = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 }
            layoutParams = lp
            setOnClickListener { doBlock() }
        }
        form.addView(btn)

        Views.section(b.content, "名单（${list.size()}）")
        if (list.size() == 0) Views.empty(b.content, "名单为空")
        for (i in 0 until list.size()) {
            val it = list[i].asJsonObject
            val device = it.get("kind")?.asString == "device"
            val fp = it.obj("fp")
            val target = if (device)
                "${fp?.get("label")?.asString ?: "设备"} (${fp?.get("short")?.asString ?: ""})"
            else it.get("ip")?.asString ?: ""
            val sub = buildString {
                append(it.get("reason")?.asString?.takeIf { r -> r.isNotEmpty() } ?: "无原因")
                append(" · 由 ${it.get("by")?.asString?.takeIf { x -> x.isNotEmpty() } ?: "系统"}")
                append(" · ${fmt(it.get("created_at")?.asLong ?: 0L)} 起")
                append("，至 ${fmt(it.get("expires_at")?.asLong ?: 0L)}")
            }
            val fpId = fp?.get("id")?.asInt ?: 0
            Views.listItem(b.content, target, sub + "  · 点此解封") {
                if (device) unblockDevice(fpId) else unblockIp(it.get("ip")?.asString ?: "")
            }
        }
    }

    private fun doBlock() {
        val ip = ipInput.text.toString().trim()
        if (ip.isEmpty()) { Toast.makeText(this, "请输入 IP", Toast.LENGTH_SHORT).show(); return }
        val minutes = minInput.text.toString().trim().ifEmpty { "0" }
        lifecycleScope.launch {
            try {
                Api.post("/plugin/ObservationCenter/admin/block", mapOf(
                    "ip" to ip, "reason" to reasonInput.text.toString().trim(), "minutes" to minutes
                ))
                Toast.makeText(this@BlocklistActivity, "已封禁", Toast.LENGTH_SHORT).show()
                load()
            } catch (e: Exception) {
                Toast.makeText(this@BlocklistActivity, e.message ?: "操作失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun unblockIp(ip: String) {
        lifecycleScope.launch {
            try {
                Api.post("/plugin/ObservationCenter/admin/unblock", mapOf("ip" to ip))
                Toast.makeText(this@BlocklistActivity, "已解除封禁", Toast.LENGTH_SHORT).show()
                load()
            } catch (e: Exception) {
                Toast.makeText(this@BlocklistActivity, e.message ?: "操作失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun unblockDevice(fpId: Int) {
        lifecycleScope.launch {
            try {
                Api.post("/plugin/ObservationCenter/admin/unblockDevice", mapOf("fp" to fpId.toString()))
                Toast.makeText(this@BlocklistActivity, "已解除封锁", Toast.LENGTH_SHORT).show()
                load()
            } catch (e: Exception) {
                Toast.makeText(this@BlocklistActivity, e.message ?: "操作失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
