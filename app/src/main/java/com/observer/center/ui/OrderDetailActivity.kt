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

/** 订单详情：/admin/order?id= */
class OrderDetailActivity : AppCompatActivity() {
    private lateinit var b: ActivitySimpleBinding
    private var orderId = 0
    private lateinit var cur: JsonObject

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(b.root)
        orderId = intent.getIntExtra("id", 0)
        b.btnBack.setOnClickListener { finish() }
        b.title.text = "订单详情"
        load()
    }

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(key: String): JsonArray =
        (get(key)?.takeIf { it.isJsonArray }?.asJsonArray) ?: JsonArray()

    private fun s(o: JsonObject, k: String): String? = o.get(k)?.takeIf { !it.isJsonNull }?.asString

    private fun load() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/order", mapOf("id" to orderId.toString()))
                cur = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return@launch
                render()
            } catch (e: Exception) {
                Toast.makeText(this@OrderDetailActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun redeliver() {
        lifecycleScope.launch {
            try {
                Api.post("/plugin/ObservationCenter/admin/redeliver", mapOf("id" to orderId.toString()))
                Toast.makeText(this@OrderDetailActivity, "已重新发货", Toast.LENGTH_SHORT).show()
                load()
            } catch (e: Exception) {
                Toast.makeText(this@OrderDetailActivity, e.message ?: "操作失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun render() {
        b.content.removeAllViews()
        val c = cur

        Views.section(b.content, "订单信息")
        val card = Views.card(b.content)
        val comm = c.obj("commodity")
        Views.info(card, "商品", s(comm ?: c, "name"))
        Views.info(card, "单号", s(c, "trade_no"))
        Views.info(card, "金额", "¥${s(c, "amount") ?: "0"}")
        Views.info(card, "数量", (c.get("num")?.asInt ?: 0).toString())
        val pay = c.obj("pay")
        Views.info(card, "支付渠道", s(pay ?: c, "name"))
        Views.info(card, "下单时间", s(c, "create_time"))
        Views.info(card, "支付时间", s(c, "pay_time"))
        Views.info(card, "状态", s(c, "outcome") ?: "付${c.get("status")?.asInt ?: 0}/发${c.get("delivery_status")?.asInt ?: 0}")
        val contact = s(c, "contact")
        Views.info(card, "联系方式", contact)
        if (c.get("refund_status")?.asInt == 1) {
            Views.info(card, "退款", "¥${s(c, "refund_amount") ?: "0"} ${s(c, "refund_time") ?: ""}")
        }
        val coupon = c.obj("coupon")
        if (coupon != null) Views.info(card, "优惠券", "${s(coupon, "code")} -¥${s(coupon, "money") ?: "0"}")

        // 卡密 / 套餐
        val secret = s(c, "secret")
        if (!secret.isNullOrEmpty()) {
            Views.section(b.content, "发货卡密")
            Views.listItem(b.content, secret, "点击复制") {
                val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("secret", secret))
                Toast.makeText(this, "已复制卡密", Toast.LENGTH_SHORT).show()
            }
        }
        val race = s(c, "race")
        if (!race.isNullOrEmpty()) {
            Views.section(b.content, "套餐")
            val rc = Views.card(b.content)
            Views.info(rc, "race", race)
        }
        val sku = c.obj("sku")
        if (sku != null && sku.size() > 0) {
            Views.section(b.content, "规格")
            val sc = Views.card(b.content)
            for (e in sku.entrySet()) {
                Views.info(sc, e.key, e.value.asString)
            }
        }
        val leave = s(c, "leave_message")
        if (!leave.isNullOrEmpty()) {
            Views.section(b.content, "买家留言")
            Views.card(b.content).let { Views.info(it, "留言", leave) }
        }

        // 来源 / 设备
        val region = c.obj("region")
        val geo = listOfNotNull(
            region?.get("country")?.asString?.takeIf { it.isNotEmpty() },
            region?.get("province")?.asString?.takeIf { it.isNotEmpty() },
            region?.get("city")?.asString?.takeIf { it.isNotEmpty() }
        ).joinToString(" ")
        Views.section(b.content, "下单来源")
        val ic = Views.card(b.content)
        Views.info(ic, "IP", s(c, "create_ip"))
        Views.info(ic, "地区", geo.ifEmpty { "—" })
        val device = c.obj("device")
        if (device != null) {
            Views.info(ic, "设备", "${s(device, "label") ?: ""} (${s(device, "short") ?: ""})")
        }

        // 操作
        Views.section(b.content, "操作")
        Views.listItem(b.content, "重新发货", "对发货失败/搁置的订单再次提交发货") { redeliver() }
    }
}
