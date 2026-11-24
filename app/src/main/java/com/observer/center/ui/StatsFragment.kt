package com.observer.center.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.observer.center.api.Api
import com.observer.center.api.GsonHolder
import com.observer.center.databinding.FragmentStatsBinding
import kotlinx.coroutines.launch

class StatsFragment : Fragment() {
    private var _b: FragmentStatsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentStatsBinding.inflate(i, c, false)
        load()
        return b.root
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/stats", mapOf("range" to "7"))
                val r = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return@launch
                val today = r.getAsJsonObject("today")
                val funnel = r.getAsJsonArray("funnel_today")
                val sb = StringBuilder()
                sb.append("【今日】\n")
                sb.append("访客：").append(today.get("visitors").asJsonString)
                sb.append("（新访客 ").append(today.get("new_visitors").asJsonString).append("）\n")
                sb.append("浏览量：").append(today.get("pageviews").asJsonString).append("\n")
                sb.append("付款订单：").append(today.get("paid_orders").asJsonString).append("\n")
                sb.append("成交额：¥").append(today.get("paid_amount").asJsonString).append("\n")
                sb.append("转化率：").append(today.get("conversion").asJsonString).append("%\n")
                sb.append("客单价：¥").append(today.get("aov").asJsonString).append("\n\n")
                sb.append("【今日转化漏斗】\n")
                funnel.forEach { el ->
                    val o = el.asJsonObject
                    val key = when (o.get("key").asJsonString) {
                        "visit" -> "进店访问"
                        "item" -> "浏览商品"
                        "checkout" -> "下单结算"
                        "paid" -> "完成付款"
                        else -> o.get("key").asJsonString
                    }
                    sb.append(key).append("：")
                        .append(o.get("n").asJsonString).append(" 人（")
                        .append(o.get("total").asJsonString).append("%）\n")
                }
                b.body.text = sb.toString()
            } catch (e: Exception) {
                b.body.text = "加载失败：${e.message}"
            }
        }
    }

    private val JsonElement?.asJsonString: String
        get() = this?.takeIf { !it.isJsonNull }?.asString ?: "0"

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
