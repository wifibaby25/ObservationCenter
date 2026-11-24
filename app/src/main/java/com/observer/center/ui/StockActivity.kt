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

/** 低库存列表：/admin/stock?threshold= */
class StockActivity : AppCompatActivity() {
    private lateinit var b: ActivitySimpleBinding

    private fun JsonObject.arr(key: String): JsonArray =
        (get(key)?.takeIf { it.isJsonArray }?.asJsonArray) ?: JsonArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
        b.title.text = "低库存"
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/stock", mapOf("threshold" to "0"))
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return@launch
                render(o.arr("list"))
            } catch (e: Exception) {
                Toast.makeText(this@StockActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun render(list: JsonArray) {
        b.content.removeAllViews()
        Views.section(b.content, "库存低于阈值的商品（${list.size()}）")
        if (list.size() == 0) {
            Views.empty(b.content, "库存充足，暂无预警")
            return
        }
        for (i in 0 until list.size()) {
            val it = list[i].asJsonObject
            val id = it.get("commodity_id")?.asInt ?: 0
            val status = it.get("status")?.asInt ?: 0
            Views.listItem(
                b.content,
                it.get("name")?.asString ?: "商品 #$id",
                "余卡 ${it.get("left")?.asInt ?: 0} / 阈值 ${it.get("threshold")?.asInt ?: 0} · 今日售 ${it.get("sold_today")?.asInt ?: 0} · ${if (status == 1) "在售" else "下架"}"
            ) {
                startActivity(Intent(this, CommodityActivity::class.java).putExtra("id", id))
            }
        }
    }
}
