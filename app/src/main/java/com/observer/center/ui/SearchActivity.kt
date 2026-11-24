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

/** 全局搜索：/admin/search?q= → members/visitors/orders/commodities/devices */
class SearchActivity : AppCompatActivity() {
    private lateinit var b: ActivitySimpleBinding
    private lateinit var qInput: EditText

    private fun JsonObject.arr(key: String): JsonArray =
        (get(key)?.takeIf { it.isJsonArray }?.asJsonArray) ?: JsonArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
        b.title.text = "全局搜索"

        val bar = Views.card(b.content)
        qInput = EditText(this).apply {
            hint = "会员名/邮箱/手机、单号、IP、设备短编号"
            setBackgroundResource(com.observer.center.R.drawable.bg_input)
            setPadding(28, 20, 28, 20)
            inputType = InputType.TYPE_CLASS_TEXT
            setOnEditorActionListener { _, _, _ -> doSearch(); true }
        }
        val btn = TextView(this).apply {
            text = "搜索"
            gravity = android.view.Gravity.CENTER
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(com.observer.center.R.drawable.bg_btn)
            setPadding(0, 22, 0, 22)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 }
            setOnClickListener { doSearch() }
        }
        bar.addView(qInput); bar.addView(btn)
    }

    private fun doSearch() {
        val q = qInput.text.toString().trim()
        if (q.isEmpty()) { Toast.makeText(this, "请输入关键词", Toast.LENGTH_SHORT).show(); return }
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/search", mapOf("q" to q))
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return@launch
                render(o)
            } catch (e: Exception) {
                Toast.makeText(this@SearchActivity, e.message ?: "搜索失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun render(o: JsonObject) {
        b.content.removeAllViews()

        val members = o.arr("members")
        if (members.size() > 0) {
            Views.section(b.content, "会员（${members.size()}）")
            for (i in 0 until members.size()) {
                val it = members[i].asJsonObject
                Views.listItem(b.content,
                    it.get("username")?.asString ?: "会员 #${it.get("id")?.asInt ?: 0}",
                    "ID ${it.get("id")?.asInt ?: 0} · 余额 ¥${it.get("balance")?.asString ?: "0"} · ${it.get("group")?.asString ?: ""}"
                ) {
                    startActivity(Intent(this, MemberActivity::class.java).putExtra("uid", it.get("id").asInt))
                }
            }
        }

        val visitors = o.arr("visitors")
        if (visitors.size() > 0) {
            Views.section(b.content, "访客（${visitors.size()}）")
            for (i in 0 until visitors.size()) {
                val it = visitors[i].asJsonObject
                val vid = it.get("vid")?.asString ?: ""
                Views.listItem(b.content,
                    it.get("ip")?.asString ?: "访客",
                    "${it.get("device")?.asString ?: ""} · PV ${it.get("pv")?.asInt ?: 0} · 点查看"
                ) {
                    startActivity(Intent(this, VisitorActivity::class.java).putExtra("vid", vid))
                }
            }
        }

        val orders = o.arr("orders")
        if (orders.size() > 0) {
            Views.section(b.content, "订单（${orders.size()}）")
            for (i in 0 until orders.size()) {
                val it = orders[i].asJsonObject
                val id = it.get("id")?.asInt ?: 0
                val name = it.getAsJsonObject("commodity")?.get("name")?.asString ?: "商品"
                Views.listItem(b.content,
                    "$name · ¥${it.get("amount")?.asString ?: "0"}",
                    "单号 ${it.get("trade_no")?.asString ?: ""} · ${it.get("create_time")?.asString ?: ""}"
                ) {
                    startActivity(Intent(this, OrderDetailActivity::class.java).putExtra("id", id))
                }
            }
        }

        val commodities = o.arr("commodities")
        if (commodities.size() > 0) {
            Views.section(b.content, "商品（${commodities.size()}）")
            for (i in 0 until commodities.size()) {
                val it = commodities[i].asJsonObject
                val id = it.get("id")?.asInt ?: 0
                Views.listItem(b.content,
                    it.get("name")?.asString ?: "商品 #$id",
                    "余卡 ${it.get("stock")?.asString ?: "—"} · ${if (it.get("status")?.asInt == 1) "在售" else "下架"} · 点查看"
                ) {
                    startActivity(Intent(this, CommodityActivity::class.java).putExtra("id", id))
                }
            }
        }

        val devices = o.arr("devices")
        if (devices.size() > 0) {
            Views.section(b.content, "设备（${devices.size()}）")
            for (i in 0 until devices.size()) {
                val it = devices[i].asJsonObject
                val id = it.get("id")?.asInt ?: 0
                Views.listItem(b.content,
                    it.get("label")?.asString ?: "设备 #$id",
                    "短号 ${it.get("short")?.asString ?: ""} · 点查看"
                ) {
                    startActivity(Intent(this, DeviceActivity::class.java).putExtra("fp", id))
                }
            }
        }

        if (members.size() + visitors.size() + orders.size() + commodities.size() + devices.size() == 0) {
            Views.empty(b.content, "没有匹配结果")
        }
    }
}
