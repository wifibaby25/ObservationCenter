package com.observer.center.ui

import android.content.Intent
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

/** 访客详情：/admin/visitor?vid= */
class VisitorActivity : AppCompatActivity() {
    private lateinit var b: ActivitySimpleBinding
    private lateinit var vid: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(b.root)
        vid = intent.getStringExtra("vid") ?: ""
        b.btnBack.setOnClickListener { finish() }
        b.title.text = "访客详情"
        load()
    }

    private fun fmtMs(ms: Long): String {
        if (ms <= 0) return "—"
        return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
    }

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(key: String): JsonArray =
        (get(key)?.takeIf { it.isJsonArray }?.asJsonArray) ?: JsonArray()

    private fun load() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/visitor", mapOf("vid" to vid))
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return@launch
                render(o)
            } catch (e: Exception) {
                Toast.makeText(this@VisitorActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun render(o: JsonObject) {
        b.content.removeAllViews()

        // 访客基本信息
        val v = o.obj("visitor")
        if (v != null) {
            Views.section(b.content, "访客信息")
            val card = Views.card(b.content)
            val region = v.getAsJsonObject("region")
            val geo = listOfNotNull(
                region?.get("country")?.asString?.takeIf { it.isNotEmpty() },
                region?.get("province")?.asString?.takeIf { it.isNotEmpty() },
                region?.get("city")?.asString?.takeIf { it.isNotEmpty() }
            ).joinToString(" ")
            Views.info(card, "IP", v.get("ip")?.asString)
            Views.info(card, "地区", geo.ifEmpty { "—" })
            Views.info(card, "设备", "${v.get("device")?.asString ?: ""} · ${v.get("os")?.asString ?: ""} / ${v.get("browser")?.asString ?: ""}")
            Views.info(card, "语言", v.get("lang")?.asString)
            Views.info(card, "访问次数", "PV ${v.get("pv")?.asInt ?: 0} · 到访 ${v.get("visits")?.asInt ?: 0}")
            Views.info(card, "最近", fmtMs(v.get("seen")?.asLong ?: 0L))
            Views.info(card, "首次", fmtMs(v.get("first")?.asLong ?: 0L))
            Views.info(card, "状态", if (v.get("blocked")?.asBoolean == true) "已封禁" else "正常")
        }

        // 会员
        val member = o.obj("member")
        if (member != null) {
            Views.section(b.content, "会员")
            Views.listItem(b.content,
                member.get("username")?.asString ?: "会员 #${member.get("id")?.asInt ?: 0}",
                "余额 ¥${member.get("balance")?.asString ?: "0"} · ${member.get("group")?.asString ?: ""} · 点查看详情"
            ) {
                startActivity(Intent(this, MemberActivity::class.java)
                    .putExtra("uid", member.get("id").asInt))
            }
        }

        // 订单
        val orders = o.arr("orders")
        Views.section(b.content, "历史订单（${orders.size()}）")
        if (orders.size() == 0) Views.empty(b.content, "暂无订单")
        for (i in 0 until orders.size()) {
            val od = orders[i].asJsonObject
            val name = od.obj("commodity")?.get("name")?.asString ?: "商品"
            Views.listItem(b.content,
                "$name · ¥${od.get("amount")?.asString ?: "0"}",
                "单号 ${od.get("trade_no")?.asString ?: ""} · ${od.get("create_time")?.asString ?: ""}"
            )
        }

        // 时间线
        val timeline = o.arr("timeline")
        Views.section(b.content, "最近动态（${timeline.size()}）")
        if (timeline.size() == 0) Views.empty(b.content, "暂无动态")
        for (i in 0 until timeline.size()) {
            val ev = timeline[i].asJsonObject
            val type = ev.get("type")?.asString ?: ""
            val data = ev.get("data")
            Views.listItem(
                b.content,
                EventText.describe(type, data),
                fmtMs(ev.get("ts")?.asLong ?: 0L)
            )
        }
    }
}
