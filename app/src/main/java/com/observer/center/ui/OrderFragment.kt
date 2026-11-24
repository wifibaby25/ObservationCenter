package com.observer.center.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.observer.center.R
import com.observer.center.api.Api
import com.observer.center.api.GsonHolder
import com.observer.center.databinding.FragmentOrdersBinding
import kotlinx.coroutines.launch

class OrderFragment : Fragment() {
    private var _b: FragmentOrdersBinding? = null
    private val b get() = _b!!
    private val items = ArrayList<JsonObject>()
    private val adapter = OrderAdapter()
    private var page = 1
    private var total = 0
    private var filter = "recent"

    private data class Tab(val key: String, val label: String)
    private val tabs = listOf(
        Tab("recent", "最近"),
        Tab("deliver_pending", "待发"),
        Tab("deliver_fail", "失败"),
        Tab("review_held", "搁置")
    )

    private fun JsonObject.arr(key: String): JsonArray =
        (get(key)?.takeIf { it.isJsonArray }?.asJsonArray) ?: JsonArray()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentOrdersBinding.inflate(i, c, false)
        b.recycler.layoutManager = LinearLayoutManager(context)
        b.recycler.adapter = adapter
        b.btnMore.setOnClickListener { loadNext() }
        buildFilterBar()
        reload()
        return b.root
    }

    private fun buildFilterBar() {
        b.filterBar.removeAllViews()
        tabs.forEach { t ->
            val tv = TextView(context).apply {
                text = t.label
                textSize = 13f
                setPadding(28, 10, 28, 10)
                val selected = t.key == filter
                setTextColor(if (selected) 0xFFFFFFFF.toInt() else resources.getColor(R.color.primary, null))
                setBackgroundResource(if (selected) R.drawable.bg_btn else R.drawable.bg_btn_outline)
                setOnClickListener {
                    if (filter != t.key) { filter = t.key; reload(); buildFilterBar() }
                }
            }
            val lp = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(4, 0, 4, 0)
            b.filterBar.addView(tv, lp)
        }
    }

    private fun reload() {
        page = 1
        items.clear()
        loadPage()
    }

    private fun loadNext() {
        page++
        loadPage()
    }

    private fun loadPage() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/orders", mapOf(
                    "filter" to filter, "page" to page.toString()
                ))
                val r = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return@launch
                val list = r.arr("list")
                total = r.get("total")?.asInt ?: 0
                if (page == 1) items.clear()
                for (i in 0 until list.size()) items.add(list[i].asJsonObject)
                adapter.notifyDataSetChanged()
                b.errorBox.visibility = View.GONE
                b.btnMore.visibility = if (items.size < total) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                b.errorBox.visibility = View.VISIBLE
                b.errorBox.text = "加载失败：${e.message}"
            }
        }
    }

    private fun redeliver(id: Int, btn: TextView) {
        btn.isEnabled = false
        lifecycleScope.launch {
            try {
                Api.post("/plugin/ObservationCenter/admin/redeliver", mapOf("id" to id.toString()))
                Toast.makeText(context, "已重新发货", Toast.LENGTH_SHORT).show()
                reload()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "操作失败", Toast.LENGTH_SHORT).show()
            } finally {
                btn.isEnabled = true
            }
        }
    }

    inner class OrderAdapter : RecyclerView.Adapter<OrderAdapter.VH>() {
        override fun onCreateViewHolder(p: ViewGroup, v: Int): VH =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_order, p, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val o = items[pos]
            val name = o.getAsJsonObject("commodity")?.get("name")?.asString ?: "商品"
            h.name.text = name
            h.trade.text = "单号 ${o.get("trade_no")?.asString ?: ""} · ${o.get("create_time")?.asString ?: ""}"
            h.amount.text = "¥${o.get("amount")?.asString ?: "0"}"
            h.status.text = "付 ${o.get("status")?.asInt ?: 0} / 发 ${o.get("delivery_status")?.asInt ?: 0}"
            val id = o.get("id")?.asInt ?: 0
            h.root.setOnClickListener {
                startActivity(Intent(context, OrderDetailActivity::class.java).putExtra("id", id))
            }
            h.btn.setOnClickListener { redeliver(id, h.btn) }
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val root: android.widget.LinearLayout = v as android.widget.LinearLayout
            val name: TextView = v.findViewById(R.id.ovName)
            val trade: TextView = v.findViewById(R.id.ovTrade)
            val amount: TextView = v.findViewById(R.id.ovAmount)
            val status: TextView = v.findViewById(R.id.ovStatus)
            val btn: TextView = v.findViewById(R.id.btnRedeliver)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
