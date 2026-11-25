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

/** 店铺详情：/admin/store?id=（0=本站主店），在线访客 + 最近订单 */
class StoreActivity : AppCompatActivity() {
    private lateinit var b: ActivitySimpleBinding
    private var storeId = 0

    private fun JsonObject.obj(k: String): JsonObject? =
        get(k)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(k: String): JsonArray =
        (get(k)?.takeIf { it.isJsonArray }?.asJsonArray) ?: JsonArray()

    private fun jl(o: JsonObject?, k: String): String? =
        o?.get(k)?.takeIf { !it.isJsonNull }?.asString

    private fun fmtMs(ms: Long): String {
        if (ms <= 0) return "—"
        return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
        storeId = intent.getIntExtra("id", 0)
        b.title.text = "店铺详情"
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/store", mapOf("id" to storeId.toString()))
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return@launch
                render(o)
            } catch (e: Exception) {
                Toast.makeText(this@StoreActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun render(o: JsonObject) {
        b.content.removeAllViews()
        val store = o.obj("store") ?: return

        Views.section(b.content, "店铺")
        val sc = Views.card(b.content)
        Views.info(sc, "名称", jl(store, "name"))
        Views.info(sc, "域名", jl(store, "host"))
        Views.info(sc, "类型", if (jl(store, "kind") == "sub") "分站" else "主站")
        Views.info(sc, "当前在线", (store.get("online")?.asInt ?: 0).toString())
        val today = store.obj("today")
        if (today != null) {
            Views.info(sc, "今日订单", (today.get("orders")?.asInt ?: 0).toString())
            Views.info(sc, "今日成交额", "¥${jl(today, "amount") ?: "0"}")
        }

        val owner = o.obj("owner")
        if (owner != null) {
            Views.section(b.content, "店主")
            val oc = Views.card(b.content)
            Views.info(oc, "用户名", jl(owner, "username"))
            Views.info(oc, "UID", (owner.get("id")?.asInt ?: 0).toString())
            Views.info(oc, "余额", "¥${jl(owner, "balance") ?: "0"}")
            Views.listItem(b.content, "查看会员详情", "") {
                val uid = owner.get("id")?.asInt ?: 0
                if (uid > 0) startActivity(Intent(this, MemberActivity::class.java).putExtra("uid", uid))
            }
        }

        // 在线访客
        val visitors = o.arr("visitors")
        Views.section(b.content, "在线访客（${visitors.size()}）")
        if (visitors.size() == 0) Views.empty(b.content, "暂无在线访客")
        for (i in 0 until visitors.size()) {
            val v = visitors[i].asJsonObject
            val region = v.obj("region")
            val geo = listOfNotNull(
                region?.get("country")?.asString?.takeIf { it.isNotEmpty() },
                region?.get("city")?.asString?.takeIf { it.isNotEmpty() }
            ).joinToString(" ")
            val name = v.get("name")?.asString?.takeIf { it.isNotEmpty() } ?: "访客"
            Views.listItem(b.content,
                "$name · ${v.get("ip")?.asString ?: ""}",
                "$geo · ${v.get("device")?.asString ?: ""} · ${fmtMs(v.get("seen")?.asLong ?: 0L)}"
            ) {
                val vid = v.get("vid")?.asString ?: ""
                if (vid.isNotEmpty()) startActivity(Intent(this, VisitorActivity::class.java).putExtra("vid", vid))
            }
        }

        // 最近订单
        val orders = o.arr("orders")
        Views.section(b.content, "最近订单（${orders.size()}）")
        if (orders.size() == 0) Views.empty(b.content, "暂无订单")
        for (i in 0 until orders.size()) {
            val od = orders[i].asJsonObject
            val comm = od.obj("commodity")
            val name = jl(comm, "name") ?: "商品"
            val oid = od.get("id")?.asInt ?: 0
            Views.listItem(b.content,
                "#$oid $name",
                "¥${jl(od, "amount") ?: "0"} · ${jl(od, "trade_no") ?: ""} · ${jl(od, "create_time") ?: ""}"
            ) {
                if (oid > 0) startActivity(Intent(this, OrderDetailActivity::class.java).putExtra("id", oid))
            }
        }
    }
}
