package com.observer.center.ui

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** 事件类型 → 中文摘要，动态流与访客时间线共用。 */
object EventText {
    fun describe(type: String, data: JsonElement?): String {
        val d = data?.takeIf { it.isJsonObject }?.asJsonObject
        fun s(k: String): String = d?.get(k)?.takeIf { !it.isJsonNull }?.asString ?: ""
        return when (type) {
            "arrive" -> {
                val r = d?.getAsJsonObject("region")
                val city = r?.get("city")?.asString ?: ""
                val country = r?.get("country")?.asString ?: ""
                val dev = when (s("device")) {
                    "pc" -> "电脑"
                    "mobile" -> "手机"
                    else -> s("device")
                }
                ("进店 · $country $city（$dev）").trim()
            }
            "view_item" -> "查看商品 · ${s("name")}"
            "chat_in" -> "访客来信 · ${chatBody(d)}"
            "chat_out" -> "客服回复 · ${chatBody(d)}"
            "chat_end" -> "对话已结束"
            "chat_missed" -> "未接会话"
            "chat_contact" -> "访客联系客服"
            "chat_rate" -> "会话评价"
            "trade" -> "下单 · ${s("commodity")} ×${s("num")}"
            "paid" -> "已付款 · ${s("commodity")} ¥${s("amount")}"
            "manual_deliver", "redeliver" -> "发货 · ${s("trade_no")}"
            "refund" -> "退款 · ${s("trade_no")}"
            "recharge" -> "充值 · ¥${s("amount")}"
            "register" -> "新会员注册"
            "login" -> "会员登录 · ${s("username")}"
            "login_fail" -> "登录失败 · ${s("account")}"
            "block" -> "封禁 IP · ${s("ip")}"
            "unblock" -> "解封 IP · ${s("ip")}"
            "ban" -> "封禁会员 · ${s("username")}"
            "ticket_create" -> "新建工单 · ${s("title")}"
            "ticket_reply" -> "工单回复"
            "cash_submit" -> "提现申请 · ¥${s("amount")}"
            "cash_decide" -> "提现审核完成"
            "stock_low" -> "库存不足 · ${s("name")}"
            "callback_fail" -> "支付回调失败"
            "multi_account" -> "疑似多账号"
            "chat_order" -> "会话内下单"
            "balance" -> "余额变动"
            else -> type
        }
    }

    private fun chatBody(d: JsonObject?): String {
        val body = d?.get("body")?.asString ?: ""
        if (body.isNotEmpty()) return body
        return if (d?.get("kind")?.asString == "image") "[图片]" else ""
    }
}
