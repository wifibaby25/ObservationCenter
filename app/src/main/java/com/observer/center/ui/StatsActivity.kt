package com.observer.center.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
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

/** 数据看板：/admin/stats?range=7|30，今日对比昨日、区间合计、每日趋势、漏斗、来源/地区/商品/分店排行 */
class StatsActivity : AppCompatActivity() {
    private lateinit var b: ActivitySimpleBinding
    private var range = 7

    private fun JsonObject.obj(k: String): JsonObject? =
        get(k)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(k: String): JsonArray =
        (get(k)?.takeIf { it.isJsonArray }?.asJsonArray) ?: JsonArray()

    private fun jl(o: JsonObject?, k: String): String? =
        o?.get(k)?.takeIf { !it.isJsonNull }?.asString

    private fun ji(o: JsonObject?, k: String): Int = o?.get(k)?.asInt ?: 0
    private fun jd(o: JsonObject?, k: String): Double = o?.get(k)?.asString?.toDoubleOrNull() ?: 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
        b.title.text = "数据看板"
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/stats", mapOf("range" to range.toString()))
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return@launch
                render(o)
            } catch (e: Exception) {
                b.content.removeAllViews()
                Toast.makeText(this@StatsActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------- 渲染 ----------------

    private fun render(r: JsonObject) {
        b.content.removeAllViews()
        // 7 / 30 切换
        val seg = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }
        seg.addView(segBtn("近 7 天", range == 7) { range = 7; load() })
        seg.addView(segBtn("近 30 天", range == 30) { range = 30; load() })
        b.content.addView(seg)

        val today = r.obj("today")
        val yesterday = r.obj("yesterday")
        val totals = r.obj("totals")

        // 今日指标（对比昨日）
        Views.section(b.content, "今日概览（对比昨日）")
        val kc = Views.card(b.content)
        kpi(kc, "访客", ji(today, "visitors").toString(), ji(today, "visitors").toDouble(), ji(yesterday, "visitors").toDouble())
        kpi(kc, "新访客", ji(today, "new_visitors").toString(), ji(today, "new_visitors").toDouble(), ji(yesterday, "new_visitors").toDouble())
        kpi(kc, "浏览量", ji(today, "pageviews").toString(), ji(today, "pageviews").toDouble(), ji(yesterday, "pageviews").toDouble())
        kpi(kc, "付款订单", ji(today, "paid_orders").toString(), ji(today, "paid_orders").toDouble(), ji(yesterday, "paid_orders").toDouble())
        kpi(kc, "成交额", "¥${jl(today, "paid_amount") ?: "0"}", jd(today, "paid_amount"), jd(yesterday, "paid_amount"))
        kpi(kc, "转化率", "${jl(today, "conversion") ?: "0"}%", jd(today, "conversion"), jd(yesterday, "conversion"))
        kpi(kc, "客单价", "¥${jl(today, "aov") ?: "0"}", jd(today, "aov"), jd(yesterday, "aov"))

        // 区间合计
        if (totals != null) {
            Views.section(b.content, "近 $range 天合计")
            val tc = Views.card(b.content)
            Views.info(tc, "访客", ji(totals, "visitors").toString())
            Views.info(tc, "新访客", ji(totals, "new_visitors").toString())
            Views.info(tc, "浏览量", ji(totals, "pageviews").toString())
            Views.info(tc, "付款订单", ji(totals, "paid_orders").toString())
            Views.info(tc, "成交额", "¥${jl(totals, "paid_amount") ?: "0"}")
            Views.info(tc, "转化率", "${jl(totals, "conversion") ?: "0"}%")
        }

        // 每日访客趋势
        val series = r.arr("series")
        if (series.size() > 0) {
            Views.section(b.content, "每日访客趋势")
            val box = Views.card(b.content)
            trendBars(box, series)
        }

        // 漏斗（区间）
        val funnel = r.arr("funnel")
        if (funnel.size() > 0) {
            Views.section(b.content, "转化漏斗（区间）")
            val fc = Views.card(b.content)
            for (i in 0 until funnel.size()) {
                val f = funnel[i].asJsonObject
                val stepName = when (f.get("key")?.asString) {
                    "visit" -> "进店访问"
                    "item" -> "浏览商品"
                    "checkout" -> "下单结算"
                    "paid" -> "完成付款"
                    else -> f.get("key")?.asString ?: ""
                }
                Views.info(fc, stepName, "${f.get("n")?.asInt ?: 0} 人 · 占进店 ${f.get("total")?.asString ?: "0"}%")
            }
        }

        // 来源 / 推广 / 地区
        rankList("主要来源", r.arr("referrers"))
        rankList("推广渠道 UTM", r.arr("utm"))
        rankList("访问地区", r.arr("regions"))

        // 设备
        val dev = r.obj("devices")
        if (dev != null) {
            Views.section(b.content, "设备分布")
            val dv = Views.card(b.content)
            Views.info(dv, "电脑", ji(dev, "pc").toString())
            Views.info(dv, "手机", ji(dev, "mobile").toString())
            Views.info(dv, "平板", ji(dev, "tablet").toString())
        }

        // 商品
        val products = r.obj("products")
        if (products != null) {
            val hot = products.arr("hot")
            if (hot.size() > 0) {
                Views.section(b.content, "实时热门商品")
                val hc = Views.card(b.content)
                for (i in 0 until hot.size()) {
                    val p = hot[i].asJsonObject
                    Views.info(hc, jl(p, "name") ?: "#${ji(p, "id")}", "浏览 ${ji(p, "n")}")
                }
            }
            val interest = products.arr("interest")
            if (interest.size() > 0) {
                Views.section(b.content, "关注排行")
                val ic = Views.card(b.content)
                for (i in 0 until interest.size()) {
                    val p = interest[i].asJsonObject
                    Views.info(ic, jl(p, "name") ?: "#${ji(p, "id")}", "${ji(p, "n")} 次 · ¥${jl(p, "amount") ?: "0"}")
                }
            }
            val sales = products.arr("sales")
            if (sales.size() > 0) {
                Views.section(b.content, "销量排行")
                val sc = Views.card(b.content)
                for (i in 0 until sales.size()) {
                    val p = sales[i].asJsonObject
                    Views.info(sc, jl(p, "name") ?: "#${ji(p, "id")}", "${ji(p, "n")} 单 · ¥${jl(p, "amount") ?: "0"}")
                }
            }
        }

        // 分店对比
        val stores = r.arr("stores")
        if (stores.size() > 0) {
            Views.section(b.content, "分店对比（按成交额）")
            for (i in 0 until stores.size()) {
                val s = stores[i].asJsonObject
                val sid = ji(s, "id")
                Views.listItem(b.content,
                    jl(s, "name") ?: "#$sid",
                    "访客 ${ji(s, "visitors")} · 订单 ${ji(s, "paid_orders")} · ¥${jl(s, "paid_amount") ?: "0"} · 转化 ${jl(s, "conversion") ?: "0"}%"
                ) {
                    startActivity(Intent(this, StoreActivity::class.java).putExtra("id", sid))
                }
            }
        }
    }

    // ---------------- 小组件 ----------------

    private fun segBtn(text: String, active: Boolean, onClick: () -> Unit): TextView {
        val dp = Views.dp(this, 10)
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 14f
            setPadding(dp * 3, dp, dp * 3, dp)
            setTextColor(if (active) Color.WHITE else 0xFF0A84FF.toInt())
            setBackgroundResource(if (active) com.observer.center.R.drawable.bg_btn else com.observer.center.R.drawable.bg_btn_outline)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = dp / 2
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun kpi(box: LinearLayout, label: String, nowText: String, now: Double, before: Double) {
        val delta = when {
            before <= 0 -> if (now > 0) "新" else "0%"
            else -> {
                val pct = (now - before) * 100 / before
                (if (pct >= 0) "+" else "") + String.format("%.1f", pct) + "%"
            }
        }
        Views.info(box, label, "$nowText（昨 $delta）")
    }

    /** 每日访客柱状：柱高按最大值归一到 110dp。 */
    private fun trendBars(box: LinearLayout, series: JsonArray) {
        var max = 1
        for (i in 0 until series.size()) max = max.coerceAtLeast(series[i].asJsonObject.get("visitors")?.asInt ?: 0)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        val dp = Views.dp(this, 110)
        for (i in 0 until series.size()) {
            val d = series[i].asJsonObject
            val v = d.get("visitors")?.asInt ?: 0
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val bar = View(this).apply {
                val h = if (max > 0) (dp * v / max) else 0
                layoutParams = LinearLayout.LayoutParams(Views.dp(this@StatsActivity, 14), h)
                setBackgroundColor(0xFF0A84FF.toInt())
            }
            val day = TextView(this).apply {
                text = (d.get("day")?.asString ?: "").substring(5)
                textSize = 9f
                setTextColor(0xFF8E8E93.toInt())
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 0)
            }
            col.addView(bar)
            col.addView(day)
            row.addView(col)
        }
        box.addView(row)
    }

    /** 横向条形排行：label + 蓝条 + n。 */
    private fun rankList(title: String, list: JsonArray) {
        if (list.size() == 0) return
        Views.section(b.content, title)
        val box = Views.card(b.content)
        var max = 1
        for (i in 0 until list.size()) max = max.coerceAtLeast(list[i].asJsonObject.get("n")?.asInt ?: 0)
        for (i in 0 until list.size()) {
            val it = list[i].asJsonObject
            val n = it.get("n")?.asInt ?: 0
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 5, 0, 5)
            }
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val name = TextView(this).apply {
                text = it.get("label")?.asString ?: ""
                textSize = 13f
                setTextColor(0xFF1C1C1E.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val num = TextView(this).apply {
                text = n.toString()
                textSize = 13f
                setTextColor(0xFF8E8E93.toInt())
            }
            head.addView(name); head.addView(num)
            val track = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(-1, Views.dp(this@StatsActivity, 6))
                setBackgroundColor(0xFFF0F1F5.toInt())
            }
            val fill = View(this).apply {
                val w = if (max > 0) (Views.dp(this@StatsActivity, 220) * n / max) else 0
                layoutParams = LinearLayout.LayoutParams(w, Views.dp(this@StatsActivity, 6))
                setBackgroundColor(0xFF0A84FF.toInt())
            }
            line.addView(head)
            line.addView(track)
            track.addView(fill)
            box.addView(line)
        }
    }
}
