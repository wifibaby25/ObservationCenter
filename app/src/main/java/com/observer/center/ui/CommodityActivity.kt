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

/** 商品详情：/admin/commodity?id= */
class CommodityActivity : AppCompatActivity() {
    private lateinit var b: ActivitySimpleBinding
    private var id = 0

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(key: String): JsonArray =
        (get(key)?.takeIf { it.isJsonArray }?.asJsonArray) ?: JsonArray()

    private fun JsonObject.str(key: String): String? = get(key)?.takeIf { !it.isJsonNull }?.asString

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(b.root)
        id = intent.getIntExtra("id", 0)
        b.btnBack.setOnClickListener { finish() }
        b.title.text = "商品详情"
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/commodity", mapOf("id" to id.toString()))
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return@launch
                render(o)
            } catch (e: Exception) {
                Toast.makeText(this@CommodityActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun render(o: JsonObject) {
        b.content.removeAllViews()
        Views.section(b.content, "基本信息")
        val card = Views.card(b.content)
        Views.info(card, "名称", o.str("name"))
        Views.info(card, "价格", "¥${o.str("price") ?: "0"}")
        Views.info(card, "余卡", (o.get("left")?.asInt ?: 0).toString())
        Views.info(card, "今日售出", (o.get("sold_today")?.asInt ?: 0).toString())
        Views.info(card, "状态", if (o.get("status")?.asInt == 1) "在售" else "下架")
        Views.info(card, "封禁", if (o.get("ban")?.asInt == 1) "已封禁" else "正常")

        val races = o.arr("races")
        if (races.size() > 0) {
            Views.section(b.content, "在售套餐（${races.size()}）")
            val rc = Views.card(b.content)
            for (i in 0 until races.size()) Views.info(rc, "套餐", races[i].asString)
        }

        val pending = o.arr("pending_stock_orders")
        Views.section(b.content, "待补库存订单（${pending.size()}）")
        if (pending.size() == 0) Views.empty(b.content, "无")
        for (i in 0 until pending.size()) {
            val it = pending[i].asJsonObject
            Views.listItem(
                b.content,
                "单号 ${it.str("trade_no") ?: ""} · ¥${it.str("amount") ?: "0"}",
                it.str("create_time") ?: ""
            )
        }

        val viewing = o.obj("viewing")
        if (viewing != null) {
            Views.section(b.content, "热度")
            val vc = Views.card(b.content)
            for (e in viewing.entrySet()) Views.info(vc, e.key, e.value.asString)
        }
    }
}
