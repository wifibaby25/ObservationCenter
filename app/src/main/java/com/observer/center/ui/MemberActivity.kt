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

/** 会员详情：/admin/member?uid= */
class MemberActivity : AppCompatActivity() {
    private lateinit var b: ActivitySimpleBinding
    private var uid = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(b.root)
        uid = intent.getIntExtra("uid", 0)
        b.btnBack.setOnClickListener { finish() }
        b.title.text = "会员详情"
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
                val body = Api.post("/plugin/ObservationCenter/admin/member", mapOf("uid" to uid.toString()))
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return@launch
                render(o)
            } catch (e: Exception) {
                Toast.makeText(this@MemberActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun render(o: JsonObject) {
        b.content.removeAllViews()
        val m = o.obj("member")
        if (m == null) {
            Views.empty(b.content, "会员不存在或已被清理")
            return
        }

        Views.section(b.content, "账户")
        val card = Views.card(b.content)
        Views.info(card, "用户名", m.get("username")?.asString)
        Views.info(card, "邮箱", m.get("email")?.asString)
        Views.info(card, "手机", m.get("phone")?.asString)
        Views.info(card, "余额", "¥${m.get("balance")?.asString ?: "0"}")
        Views.info(card, "金币", m.get("coin")?.asString)
        Views.info(card, "分组", m.get("group")?.asString)
        Views.info(card, "状态", if (m.get("status")?.asInt == 1) "正常" else "封禁")
        Views.info(card, "注册", m.get("create_time")?.asString)
        Views.info(card, "最近登录", "${m.get("login_time")?.asString ?: ""} · ${m.get("last_login_ip")?.asString ?: ""}")

        val visitors = o.arr("visitors")
        Views.section(b.content, "关联访客（${visitors.size()}）")
        if (visitors.size() == 0) Views.empty(b.content, "暂无关联访客")
        for (i in 0 until visitors.size()) {
            val v = visitors[i].asJsonObject
            Views.listItem(b.content,
                v.get("name")?.asString?.takeIf { it.isNotEmpty() } ?: "访客 ${v.get("vid")?.asString ?: ""}",
                "IP ${v.get("ip")?.asString ?: ""} · ${v.get("device")?.asString ?: ""} · ${fmtMs(v.get("seen")?.asLong ?: 0L)}"
            ) {
                startActivity(android.content.Intent(this, VisitorActivity::class.java)
                    .putExtra("vid", v.get("vid")?.asString ?: ""))
            }
        }

        val orders = o.arr("orders")
        Views.section(b.content, "订单（${orders.size()}）")
        if (orders.size() == 0) Views.empty(b.content, "暂无订单")
        for (i in 0 until orders.size()) {
            val od = orders[i].asJsonObject
            val name = od.obj("commodity")?.get("name")?.asString ?: "商品"
            Views.listItem(b.content,
                "$name · ¥${od.get("amount")?.asString ?: "0"}",
                "单号 ${od.get("trade_no")?.asString ?: ""} · ${od.get("create_time")?.asString ?: ""}"
            )
        }

        val same = o.arr("same_device")
        if (same.size() > 0) {
            Views.section(b.content, "同设备会员（${same.size()}）")
            for (i in 0 until same.size()) {
                val s = same[i].asJsonObject
                val suid = s.get("uid")?.asInt ?: 0
                Views.listItem(b.content,
                    s.get("username")?.asString?.takeIf { it.isNotEmpty() } ?: "会员 #$suid",
                    "同设备关联 · ${s.get("basis")?.asString ?: ""} · 点查看"
                ) {
                    startActivity(android.content.Intent(this, MemberActivity::class.java)
                        .putExtra("uid", suid))
                }
            }
        }
    }
}
