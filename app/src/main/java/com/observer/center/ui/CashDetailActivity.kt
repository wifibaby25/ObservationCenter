package com.observer.center.ui

import android.app.AlertDialog
import android.os.Bundle
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

/** 提现单笔详情：/admin/cash?id=，页内可通过/驳回 */
class CashDetailActivity : AppCompatActivity() {
    private lateinit var b: ActivitySimpleBinding
    private var cashId = 0
    private var canDecide = false
    private var cur: JsonObject? = null

    private fun JsonObject.obj(k: String): JsonObject? =
        get(k)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(k: String): JsonArray =
        (get(k)?.takeIf { it.isJsonArray }?.asJsonArray) ?: JsonArray()

    private fun jl(o: JsonObject?, k: String): String? =
        o?.get(k)?.takeIf { !it.isJsonNull }?.asString

    private fun ji(o: JsonObject?, k: String): Int = o?.get(k)?.asInt ?: 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
        cashId = intent.getIntExtra("id", 0)
        b.title.text = "提现详情"
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/cash", mapOf("id" to cashId.toString()))
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return@launch
                cur = o
                val cd = o.get("can_decide")
                canDecide = when {
                    cd == null -> false
                    cd.isJsonPrimitive && cd.asJsonPrimitive.isBoolean -> cd.asBoolean
                    else -> ji(o, "can_decide") == 1
                }
                render(o)
            } catch (e: Exception) {
                Toast.makeText(this@CashDetailActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun render(o: JsonObject) {
        b.content.removeAllViews()
        val status = ji(o, "status")
        val statusText = when (status) {
            1 -> "已通过"
            2 -> "已驳回"
            else -> "待处理"
        }

        Views.section(b.content, "提现申请")
        val cc = Views.card(b.content)
        Views.info(cc, "金额", "¥${jl(o, "amount") ?: "0"}")
        Views.info(cc, "手续费", "¥${jl(o, "cost") ?: "0"}")
        Views.info(cc, "合计扣款", "¥${jl(o, "total") ?: "0"}")
        Views.info(cc, "方式", jl(o, "method"))
        Views.info(cc, "收款账号", jl(o, "account"))
        Views.info(cc, "状态", "$statusText · #${ji(o, "id")}")
        Views.info(cc, "提交时间", jl(o, "create_time"))
        val arrive = jl(o, "arrive_time")
        if (!arrive.isNullOrEmpty()) Views.info(cc, "处理时间", arrive)
        val msg = jl(o, "message")
        if (!msg.isNullOrEmpty()) Views.info(cc, "备注/理由", msg)
        if (o.get("held")?.asBoolean == true) Views.info(cc, "挂起", "站内余额待入账，通过后直接加余额")

        val member = o.obj("member")
        if (member != null) {
            Views.section(b.content, "申请人")
            val mc = Views.card(b.content)
            Views.info(mc, "用户名", jl(member, "username"))
            Views.info(mc, "UID", ji(member, "id").toString())
            Views.info(mc, "收款人", jl(member, "payee"))
            Views.info(mc, "注册时间", jl(member, "create_time"))
            Views.info(mc, "硬币余额", "¥${jl(member, "coin") ?: "0"}")
            Views.info(mc, "账户余额", "¥${jl(member, "balance") ?: "0"}")
            val uid = ji(member, "id")
            if (uid > 0) Views.listItem(b.content, "查看会员详情", "") {
                startActivity(android.content.Intent(this, MemberActivity::class.java).putExtra("uid", uid))
            }
        }

        // 风控
        val riskDetail = o.obj("risk_detail")
        if (riskDetail != null) {
            Views.section(b.content, "风控评估（${jl(riskDetail, "level")} · 评分 ${ji(riskDetail, "score")}）")
            val rc = Views.card(b.content)
            val age = ji(riskDetail, "age_sec")
            if (age > 0) Views.info(rc, "账号年龄", "${age / 86400} 天")
            val history = riskDetail.obj("history")
            if (history != null) {
                Views.info(rc, "历史付款", "${ji(history, "paid")} 单 · ¥${jl(history, "paid_amount") ?: "0"}")
                Views.info(rc, "被驳回", "${ji(history, "rejected")} 次")
            }
            val flags = riskDetail.arr("flags")
            for (i in 0 until flags.size()) {
                val f = flags[i].asJsonObject
                val people = f.arr("people")
                val who = if (people.size() > 0)
                    people.joinToString("、") { p ->
                        val po = p.asJsonObject
                        po.get("username")?.asString ?: po.get("ip")?.asString ?: "#${po.get("uid")?.asInt ?: 0}"
                    } else ""
                Views.info(rc, "标记 ${jl(f, "key")}", "权重 ${ji(f, "weight")} · ${ji(f, "n")} 次" + (if (who.isNotEmpty()) " · $who" else ""))
            }
            val inflow = riskDetail.obj("inflow")
            if (inflow != null) {
                Views.info(rc, "30天来源",
                    inflow.entrySet().joinToString(" · ") { e ->
                        val v = e.value.asJsonObject
                        "${e.key} ${ji(v, "n")}单 ¥${jl(v, "amount") ?: "0"}"
                    })
            }
        }

        // 操作
        if (canDecide && status == 0) {
            Views.section(b.content, "操作")
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 6, 0, 6)
            }
            val btnApprove = TextView(this).apply {
                text = "通过"
                gravity = android.view.Gravity.CENTER
                textSize = 15f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundResource(com.observer.center.R.drawable.bg_btn)
                setPadding(0, 20, 0, 20)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 10 }
                setOnClickListener { decide("approve", null) }
            }
            val btnReject = TextView(this).apply {
                text = "驳回"
                gravity = android.view.Gravity.CENTER
                textSize = 15f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundResource(com.observer.center.R.drawable.bg_btn_outline)
                setPadding(0, 20, 0, 20)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { askReject() }
            }
            row.addView(btnApprove); row.addView(btnReject)
            b.content.addView(row)
        }
    }

    private fun askReject() {
        val input = EditText(this).apply { hint = "驳回理由（必填，最多64字）" }
        AlertDialog.Builder(this)
            .setTitle("驳回提现")
            .setView(input)
            .setPositiveButton("驳回") { _, _ ->
                val reason = input.text.toString().trim()
                if (reason.isEmpty()) {
                    Toast.makeText(this, "请填写驳回理由", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                decide("reject", reason)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun decide(decision: String, reason: String?) {
        lifecycleScope.launch {
            try {
                val form = mutableMapOf("id" to cashId.toString(), "decision" to decision)
                if (reason != null) form["message"] = reason
                Api.post("/plugin/ObservationCenter/admin/cashDecide", form)
                Toast.makeText(this@CashDetailActivity, if (decision == "approve") "已通过" else "已驳回", Toast.LENGTH_SHORT).show()
                load()
            } catch (e: Exception) {
                Toast.makeText(this@CashDetailActivity, e.message ?: "操作失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
