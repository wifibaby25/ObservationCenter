package com.observer.center.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class Envelope(
    val code: Int = 0,
    val msg: String? = null,
    val data: JsonObject? = null
)

data class Me(val id: Int = 0, val name: String = "", val type: Int = 0)

data class Site(
    val name: String = "",
    val title: String = "",
    val tz_offset_min: Int = 0,
    val currency: String = "¥",
    val stock_threshold: Int = 5
)

data class StoreToday(val orders: Int = 0, val amount: String = "0.00")

data class Store(
    val id: Int = 0,
    val kind: String = "",
    val user_id: Int = 0,
    val name: String = "",
    val host: String = "",
    val online: Int = 0,
    val today: StoreToday? = null
)

data class Todo(
    val deliver_pending: Int = 0,
    val deliver_fail: Int = 0,
    val review_held: Int = 0,
    val stock_low: Int = 0,
    val ticket_open: Int = 0,
    val chat_unread: Int = 0,
    val pay_watch: Int = 1,
    val pay_alert: Int = 0,
    val cash_pending: Int = 0,
    val cash_amount: String = "0.00"
)

data class Stats(
    val online: Int = 0,
    val members_online: Int = 0,
    val today_orders: Int = 0,
    val today_paid: Int = 0,
    val today_amount: String = "0.00",
    val today_visitors: Int = 0
)

data class OcEvent(
    val id: Long = 0,
    val ts: Long = 0,
    val type: String = "",
    val level: Int = 0,
    val vid: String = "",
    val uid: Int = 0,
    val store: Int = 0,
    val ref: JsonElement? = null,
    val data: JsonElement? = null
)

data class ThreadLast(
    val body: String = "",
    val from: String = "",
    val ts: Long = 0,
    val type: String = ""
)

data class Thread(
    val id: Int = 0,
    val vid: String = "",
    val uid: Int = 0,
    val name: String = "",
    val owner: String = "",
    val ip: String = "",
    val store: Int = 0,
    val status: Int = 0,
    val unread: Int = 0,
    val visitor_read: Int = 0,
    val admin_read: Int = 0,
    val contact: String? = null,
    val rating: JsonElement? = null,
    val note: String = "",
    val ended: Long = 0,
    val last: ThreadLast? = null,
    val created: Long = 0
)

data class Message(
    val id: Long = 0,
    val from: String = "",
    val type: String = "text",
    val body: String = "",
    val ts: Long = 0,
    val name: String = "",
    val status: String = "",
    val auto: String = "",
    val manage: String = "",
    val cid: String = "",
    val image: JsonObject? = null,
    val card: JsonObject? = null,
    val tr: JsonObject? = null
)

data class Snapshot(
    val now: Long = 0,
    val cursor: Long = 0,
    val me: Me? = null,
    val site: Site? = null,
    val stores: List<Store> = emptyList(),
    val todo: Todo? = null,
    val stats: Stats? = null,
    val events: List<OcEvent> = emptyList(),
    val threads: List<Thread> = emptyList(),
    val capabilities: JsonElement? = null
)

data class MessagesResult(
    val messages: List<Message> = emptyList(),
    val thread: Thread? = null,
    val has_more: Boolean = false
)

data class ThreadsResult(val list: List<Thread> = emptyList())
