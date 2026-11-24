package com.observer.center.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.observer.center.api.Api
import com.observer.center.api.GsonHolder
import com.observer.center.databinding.FragmentManageBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManageFragment : Fragment() {
    private var _b: FragmentManageBinding? = null
    private val b get() = _b!!
    private var canDecide = false

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentManageBinding.inflate(i, c, false)
        b.btnVisitors.setOnClickListener {
            startActivity(android.content.Intent(context, com.observer.center.ui.VisitorsActivity::class.java))
        }
        b.btnTickets.setOnClickListener {
            startActivity(android.content.Intent(context, com.observer.center.ui.TicketsActivity::class.java))
        }
        b.btnSearch.setOnClickListener {
            startActivity(android.content.Intent(context, com.observer.center.ui.SearchActivity::class.java))
        }
        b.btnBlocks.setOnClickListener {
            startActivity(android.content.Intent(context, com.observer.center.ui.BlocklistActivity::class.java))
        }
        b.btnStock.setOnClickListener {
            startActivity(android.content.Intent(context, com.observer.center.ui.StockActivity::class.java))
        }
        b.btnQuick.setOnClickListener {
            startActivity(android.content.Intent(context, com.observer.center.ui.QuickManageActivity::class.java))
        }
        b.btnRules.setOnClickListener {
            startActivity(android.content.Intent(context, com.observer.center.ui.RulesManageActivity::class.java))
        }
        load()
        return b.root
    }

    private fun load() {
        lifecycleScope.launch {
            b.manageError.visibility = View.GONE
            loadCashes()
            loadPays()
        }
    }

    // ---------------- 提现审核 ----------------

    private suspend fun loadCashes() {
        try {
            val body = Api.post("/plugin/ObservationCenter/admin/cashes")
            val r = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return
            val cd = r.get("can_decide")
            canDecide = when {
                cd == null -> false
                cd.isJsonPrimitive && cd.asJsonPrimitive.isBoolean -> cd.asBoolean
                else -> cd.asInt == 1
            }
            val pending = r.getAsJsonArray("pending") ?: JsonArray()
            b.cashList.removeAllViews()
            b.cashEmpty.visibility = if (pending.size() == 0) View.VISIBLE else View.GONE
            for (i in 0 until pending.size()) {
                val c = pending[i].asJsonObject
                b.cashList.addView(cashView(c))
            }
        } catch (e: Exception) {
            showError("提现加载失败：${e.message}")
        }
    }

    private fun cashView(c: JsonObject): View {
        val v = LayoutInflater.from(context).inflate(com.observer.center.R.layout.item_cash, b.cashList, false)
        val member = c.getAsJsonObject("member")
        val name = member?.get("username")?.asString ?: "用户"
        val payee = member?.get("payee")?.asString ?: ""
        v.findViewById<TextView>(com.observer.center.R.id.cashMember).text = name
        v.findViewById<TextView>(com.observer.center.R.id.cashAccount).text =
            "收款：${c.get("method")?.asString ?: ""} ${c.get("account")?.asString ?: ""} ${if (payee.isNotEmpty()) "($payee)" else ""}"
        v.findViewById<TextView>(com.observer.center.R.id.cashAmount).text = "¥${c.get("amount")?.asString ?: "0"}"
        v.findViewById<TextView>(com.observer.center.R.id.cashTime).text =
            c.get("create_time")?.asString ?: ""
        val risk = c.getAsJsonObject("risk")
        if (risk != null) {
            val flags = risk.getAsJsonArray("flags")
            val ft = flags?.joinToString(", ") { it.asString } ?: ""
            v.findViewById<TextView>(com.observer.center.R.id.cashRisk).apply {
                text = "风险：${risk.get("level")?.asString ?: ""} $ft"
                visibility = View.VISIBLE
            }
        }
        val id = c.get("id").asInt
        val btnApprove = v.findViewById<TextView>(com.observer.center.R.id.btnApprove)
        val btnReject = v.findViewById<TextView>(com.observer.center.R.id.btnReject)
        if (!canDecide) {
            btnApprove.visibility = View.GONE
            btnReject.visibility = View.GONE
        }
        btnApprove.setOnClickListener { decide(id, "approve", null) }
        btnReject.setOnClickListener { askRejectReason(id) }
        return v
    }

    private fun askRejectReason(id: Int) {
        val input = EditText(requireContext()).apply { hint = "驳回理由（必填）" }
        AlertDialog.Builder(requireContext())
            .setTitle("驳回提现")
            .setView(input)
            .setPositiveButton("驳回") { _, _ ->
                val reason = input.text.toString().trim()
                if (reason.isEmpty()) {
                    Toast.makeText(context, "请填写驳回理由", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                decide(id, "reject", reason)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun decide(id: Int, decision: String, reason: String?) {
        lifecycleScope.launch {
            try {
                val form = mutableMapOf("id" to id.toString(), "decision" to decision)
                if (reason != null) form["message"] = reason
                Api.post("/plugin/ObservationCenter/admin/cashDecide", form)
                Toast.makeText(context, if (decision == "approve") "已通过" else "已驳回", Toast.LENGTH_SHORT).show()
                loadCashes()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "操作失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------- 支付通道 ----------------

    private suspend fun loadPays() {
        try {
            val body = Api.post("/plugin/ObservationCenter/admin/payments")
            val r = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return
            val list = r.getAsJsonArray("list") ?: JsonArray()
            b.payList.removeAllViews()
            b.payEmpty.visibility = if (list.size() == 0) View.VISIBLE else View.GONE
            for (i in 0 until list.size()) {
                val p = list[i].asJsonObject
                b.payList.addView(payView(p))
            }
        } catch (e: Exception) {
            showError("支付通道加载失败：${e.message}")
        }
    }

    private fun payView(p: JsonObject): View {
        val v = LayoutInflater.from(context).inflate(com.observer.center.R.layout.item_pay, b.payList, false)
        val name = p.get("name")?.asString ?: "通道"
        val state = p.get("state")?.asString ?: "ok"
        val today = p.getAsJsonObject("today")
        val paid = today?.get("paid")?.asInt ?: 0
        val total = today?.get("total")?.asInt ?: 0
        val stateText = when (state) {
            "paused" -> "已暂停"
            "alert" -> "告警：连续未付款/回调失败"
            "idle" -> "闲置（7天无付款）"
            else -> "正常"
        }
        v.findViewById<TextView>(com.observer.center.R.id.payName).text = name
        v.findViewById<TextView>(com.observer.center.R.id.payState).text =
            "$stateText · 今日 $total 单 / 已付 $paid"
        val id = p.get("id").asInt
        val btn = v.findViewById<TextView>(com.observer.center.R.id.btnPay)
        val paused = state == "paused"
        btn.text = if (paused) "恢复" else "暂停"
        btn.setOnClickListener { togglePay(id, paused) }
        return v
    }

    private fun togglePay(id: Int, paused: Boolean) {
        lifecycleScope.launch {
            try {
                val path = if (paused) "/plugin/ObservationCenter/admin/payResume"
                else "/plugin/ObservationCenter/admin/payPause"
                Api.post(path, mapOf("id" to id.toString()))
                Toast.makeText(context, if (paused) "已恢复" else "已暂停", Toast.LENGTH_SHORT).show()
                loadPays()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "操作失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showError(msg: String) {
        b.manageError.text = msg
        b.manageError.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        if (_b != null) load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
