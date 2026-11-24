package com.observer.center.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.observer.center.R
import com.observer.center.api.Api
import com.observer.center.api.GsonHolder
import com.observer.center.api.Message
import com.observer.center.api.MessagesResult
import com.observer.center.api.Thread
import com.observer.center.databinding.ActivityChatBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatActivity : AppCompatActivity() {
    private lateinit var b: ActivityChatBinding
    private val messages = ArrayList<Message>()
    private lateinit var threadId: String
    private var currentThread: Thread? = null
    private val adapter = ChatAdapter()
    private val translated = HashMap<Long, String>()

    private val pickImage = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: return@launch
                sendImage(bytes)
            } catch (e: Exception) {
                toast(e.message ?: "发图失败")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityChatBinding.inflate(layoutInflater)
        setContentView(b.root)
        threadId = intent.getIntExtra("thread_id", 0).toString()
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter
        b.btnBack.setOnClickListener { finish() }
        b.btnSend.setOnClickListener { send() }
        b.btnEnd.setOnClickListener { endChat() }
        b.btnBlock.setOnClickListener { showBlockDialog() }
        b.btnNote.setOnClickListener { editNote() }
        b.btnImage.setOnClickListener { pickImage.launch("image/*") }
        b.btnCard.setOnClickListener { showCardDialog() }
        b.btnQuick.setOnClickListener { showQuickDialog() }
        b.title.setOnClickListener { openVisitor() }
        load()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun JsonObject?.obj(key: String): JsonObject? =
        this?.get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject?.arr(key: String): JsonArray =
        (this?.get(key)?.takeIf { it.isJsonArray }?.asJsonArray) ?: JsonArray()

    private fun cid() = UUID.randomUUID().toString().replace("-", "")

    private fun markRead() {
        lifecycleScope.launch {
            runCatching { Api.post("/plugin/ObservationCenter/admin/read", mapOf("thread_id" to threadId)) }
        }
    }

    private fun endChat() {
        lifecycleScope.launch {
            try {
                Api.post("/plugin/ObservationCenter/admin/endChat", mapOf("thread_id" to threadId))
                toast("对话已结束")
                finish()
            } catch (e: Exception) {
                toast(e.message ?: "操作失败")
            }
        }
    }

    private fun showBlockDialog() {
        val t = currentThread ?: return
        val dp = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val etMin = EditText(this).apply {
            hint = "封禁时长（分钟，0=永久）"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("60")
        }
        val etReason = EditText(this).apply { hint = "封禁理由" }
        dp.addView(etMin)
        dp.addView(etReason)
        val builder = AlertDialog.Builder(this).setTitle("封禁 · ${t.ip}")
        if (t.uid > 0) {
            builder.setPositiveButton("封会员") { _, _ -> banMember(t.uid, etReason.text.toString().trim()) }
            builder.setNeutralButton("封IP") { _, _ -> blockIp(t.ip, etMin.text.toString().trim(), etReason.text.toString().trim()) }
        } else {
            builder.setPositiveButton("封IP") { _, _ -> blockIp(t.ip, etMin.text.toString().trim(), etReason.text.toString().trim()) }
        }
        builder.setNegativeButton("取消", null)
        builder.setView(dp).show()
    }

    private fun blockIp(ip: String, minutes: String, reason: String) {
        lifecycleScope.launch {
            try {
                Api.post("/plugin/ObservationCenter/admin/block", mapOf("ip" to ip, "minutes" to minutes, "reason" to reason))
                toast("已封禁 IP")
            } catch (e: Exception) {
                toast(e.message ?: "操作失败")
            }
        }
    }

    private fun banMember(uid: Int, reason: String) {
        lifecycleScope.launch {
            try {
                Api.post("/plugin/ObservationCenter/admin/ban", mapOf("uid" to uid.toString(), "status" to "0"))
                toast("已封禁该会员")
            } catch (e: Exception) {
                toast(e.message ?: "操作失败")
            }
        }
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/messages", mapOf("thread_id" to threadId))
                val r = GsonHolder.gson.fromJson(body, MessagesResult::class.java)
                messages.clear()
                messages.addAll(r.messages)
                adapter.notifyDataSetChanged()
                currentThread = r.thread
                b.title.text = r.thread?.let { (it.name.ifEmpty { "访客" }) + " · " + it.ip } ?: "客服对话"
                b.recycler.scrollToPosition(messages.size - 1)
                markRead()
            } catch (e: Exception) {
                b.title.text = "加载失败：${e.message}"
            }
        }
    }

    private fun send() {
        val text = b.etInput.text.toString().trim()
        if (text.isEmpty()) return
        b.btnSend.isEnabled = false
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/send", mapOf(
                    "thread_id" to threadId, "body" to text, "cid" to cid()
                ))
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java)
                val msg = o.obj("message")
                if (msg != null) {
                    messages.add(GsonHolder.gson.fromJson(msg, Message::class.java))
                } else {
                    messages.add(Message(from = "agent", type = "text", body = text, ts = System.currentTimeMillis()))
                }
                adapter.notifyItemInserted(messages.size - 1)
                b.etInput.setText("")
                b.recycler.scrollToPosition(messages.size - 1)
            } catch (e: Exception) {
                toast("发送失败：${e.message}")
            } finally {
                b.btnSend.isEnabled = true
            }
        }
    }

    // ---------------- 发图片 ----------------

    private suspend fun sendImage(bytes: ByteArray) {
        val caption = ""
        val body = Api.postMultipart(
            "/plugin/ObservationCenter/adminChat/sendImage",
            mapOf("thread_id" to threadId, "cid" to cid(), "caption" to caption),
            "file", "chat.jpg", bytes
        )
        val o = GsonHolder.gson.fromJson(body, JsonObject::class.java)
        val msg = o?.getAsJsonObject("message")
        if (msg != null) {
            messages.add(GsonHolder.gson.fromJson(msg, Message::class.java))
            adapter.notifyItemInserted(messages.size - 1)
            b.recycler.scrollToPosition(messages.size - 1)
        }
    }

    // ---------------- 发卡片 ----------------

    private fun showCardDialog() {
        val items = arrayOf("发送商品卡片", "发送本会话订单")
        AlertDialog.Builder(this).setTitle("发送卡片")
            .setItems(items) { _, which ->
                if (which == 0) pickProduct() else pickOrder()
            }.show()
    }

    private fun pickProduct() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/adminChat/products", mapOf("q" to ""))
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java)
                val list = o.arr("list")
                val labels = ArrayList<String>()
                for (i in 0 until list.size()) {
                    val p = list[i].asJsonObject
                    labels.add("${p.get("name")?.asString ?: ""} · ¥${p.get("price")?.asString ?: ""}")
                }
                if (labels.isEmpty()) { toast("没有可发送的商品"); return@launch }
                AlertDialog.Builder(this@ChatActivity).setTitle("选择商品")
                    .setItems(labels.toTypedArray()) { _, which ->
                        val p = list[which].asJsonObject
                        sendCard("product", p.get("id").asInt)
                    }.show()
            } catch (e: Exception) { toast(e.message ?: "商品加载失败") }
        }
    }

    private fun pickOrder() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/adminChat/orders", mapOf("thread_id" to threadId))
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java)
                val list = o.arr("list")
                val labels = ArrayList<String>()
                for (i in 0 until list.size()) {
                    val od = list[i].asJsonObject
                    val name = od.getAsJsonObject("commodity")?.get("name")?.asString ?: "订单"
                    labels.add("#${od.get("id")?.asString ?: ""} $name · ¥${od.get("amount")?.asString ?: "0"}")
                }
                if (labels.isEmpty()) { toast("本会话没有订单"); return@launch }
                AlertDialog.Builder(this@ChatActivity).setTitle("选择订单")
                    .setItems(labels.toTypedArray()) { _, which ->
                        val od = list[which].asJsonObject
                        sendCard("order", od.get("id").asInt)
                    }.show()
            } catch (e: Exception) { toast(e.message ?: "订单加载失败") }
        }
    }

    private fun sendCard(kind: String, id: Int) {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/adminChat/sendCard", mapOf(
                    "thread_id" to threadId, "kind" to kind, "id" to id.toString(), "cid" to cid()
                ))
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java)
                val msg = o.obj("message")
                if (msg != null) {
                    messages.add(GsonHolder.gson.fromJson(msg, Message::class.java))
                    adapter.notifyItemInserted(messages.size - 1)
                    b.recycler.scrollToPosition(messages.size - 1)
                }
            } catch (e: Exception) { toast(e.message ?: "发送失败") }
        }
    }

    // ---------------- 撤回 / 翻译 / 话术 / 备注 ----------------

    private fun recall(m: Message) {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/adminChat/recall", mapOf("id" to m.id.toString()))
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java)
                val fresh = o.obj("message") ?: return@launch
                val idx = messages.indexOfFirst { it.id == m.id }
                if (idx >= 0) {
                    messages[idx] = GsonHolder.gson.fromJson(fresh, Message::class.java)
                    adapter.notifyItemChanged(idx)
                }
                toast("已撤回")
            } catch (e: Exception) { toast(e.message ?: "撤回失败") }
        }
    }

    private fun translateMessage(m: Message) {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/adminChat/translate", mapOf(
                    "thread_id" to threadId, "ids" to m.id.toString()
                ))
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java)
                val items = o.obj("items")
                val tr = items?.obj(m.id.toString())
                val text = tr?.get("text")?.asString
                if (!text.isNullOrEmpty()) {
                    translated[m.id] = text
                    val idx = messages.indexOfFirst { it.id == m.id }
                    if (idx >= 0) adapter.notifyItemChanged(idx)
                } else {
                    toast("暂无译文")
                }
            } catch (e: Exception) { toast(e.message ?: "翻译失败") }
        }
    }

    private fun showQuickDialog() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/adminChat/quick")
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java)
                val list = o.arr("list")
                val labels = ArrayList<String>()
                for (i in 0 until list.size()) labels.add(list[i].asJsonObject.get("title")?.asString ?: "")
                if (labels.isEmpty()) { toast("暂无快捷回复"); return@launch }
                AlertDialog.Builder(this@ChatActivity).setTitle("快捷回复")
                    .setItems(labels.toTypedArray()) { _, which ->
                        val q = list[which].asJsonObject
                        b.etInput.setText(q.get("body")?.asString ?: "")
                        b.etInput.setSelection(b.etInput.text.length)
                    }.show()
            } catch (e: Exception) { toast(e.message ?: "加载失败") }
        }
    }

    private fun editNote() {
        val t = currentThread ?: return
        val et = EditText(this).apply { setText(t.note) }
        AlertDialog.Builder(this).setTitle("会话备注")
            .setView(et)
            .setPositiveButton("保存") { _, _ ->
                lifecycleScope.launch {
                    try {
                        Api.post("/plugin/ObservationCenter/adminChat/note", mapOf(
                            "thread_id" to threadId, "note" to et.text.toString().trim()
                        ))
                        toast("备注已保存")
                        load()
                    } catch (e: Exception) { toast(e.message ?: "保存失败") }
                }
            }
            .setNegativeButton("取消", null).show()
    }

    private fun openVisitor() {
        val v = currentThread?.vid ?: return
        if (v.isEmpty()) { toast("暂无访客标识"); return }
        startActivity(Intent(this, VisitorActivity::class.java).putExtra("vid", v))
    }

    // ---------------- 适配器 ----------------

    inner class ChatAdapter : RecyclerView.Adapter<ChatAdapter.VH>() {
        override fun onCreateViewHolder(p: ViewGroup, v: Int): VH =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_chat, p, false))

        override fun getItemCount() = messages.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val m = messages[pos]
            val mine = m.from == "agent"
            h.row.gravity = if (mine) Gravity.END else Gravity.START
            h.bubble.visibility = View.GONE
            h.ivImage.visibility = View.GONE
            h.cardBox.visibility = View.GONE
            h.trText.visibility = View.GONE

            when (m.type) {
                "image" -> {
                    h.ivImage.visibility = View.VISIBLE
                    val rel = m.image?.get("thumb")?.asString ?: m.image?.get("full")?.asString
                    if (!rel.isNullOrEmpty()) loadImage(h.ivImage, rel)
                    if (m.body.isNotEmpty()) {
                        h.bubble.visibility = View.VISIBLE
                        h.bubble.text = m.body
                    }
                }
                "card" -> {
                    h.cardBox.visibility = View.VISIBLE
                    val c = m.card
                    h.cardTitle.text = c?.get("title")?.asString ?: "卡片"
                    h.cardSub.text = c?.get("sub")?.asString ?: ""
                    val meta = ArrayList<String>()
                    c?.get("price")?.asString?.takeIf { it.isNotEmpty() }?.let { meta.add("¥$it") }
                    c?.get("status")?.asString?.takeIf { it.isNotEmpty() }?.let { meta.add(it) }
                    c?.get("issue_label")?.asString?.takeIf { it.isNotEmpty() }?.let { meta.add(it) }
                    h.cardMeta.text = meta.joinToString(" · ")
                    h.cardMeta.setTextColor(if (mine) 0xFF3B6CF6.toInt() else resources.getColor(R.color.textSub, null))
                }
                "recalled" -> {
                    h.bubble.visibility = View.VISIBLE
                    h.bubble.text = "（已撤回）"
                    h.bubble.setTextColor(resources.getColor(R.color.textSub, null))
                    h.bubble.setBackgroundResource(0)
                }
                else -> {
                    h.bubble.visibility = View.VISIBLE
                    h.bubble.text = m.body
                }
            }

            if (m.type != "recalled") {
                h.bubble.setBackgroundResource(if (mine) R.drawable.bubble_right else R.drawable.bubble_left)
                h.bubble.setTextColor(if (mine) 0xFFFFFFFF.toInt() else resources.getColor(R.color.text, null))
            }

            val tr = translated[m.id] ?: m.tr?.get("text")?.asString
            if (!tr.isNullOrEmpty()) {
                h.trText.visibility = View.VISIBLE
                h.trText.text = "译文：$tr"
            }

            h.root.setOnLongClickListener {
                if (mine && m.type != "recalled") {
                    AlertDialog.Builder(this@ChatActivity)
                        .setItems(arrayOf("撤回")) { _, _ -> recall(m) }.show()
                    true
                } else if (!mine && m.type == "text") {
                    AlertDialog.Builder(this@ChatActivity)
                        .setItems(arrayOf("翻译")) { _, _ -> translateMessage(m) }.show()
                    true
                } else false
            }
        }

        private fun loadImage(iv: ImageView, rel: String) {
            iv.tag = rel
            lifecycleScope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = Api.getBytes(rel)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }.getOrNull()
                }
                if (iv.tag == rel && bmp != null) iv.setImageBitmap(bmp)
            }
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val root: LinearLayout = v as LinearLayout
            val row: LinearLayout = v.findViewById(R.id.contentRow)
            val bubble: TextView = v.findViewById(R.id.bubble)
            val ivImage: ImageView = v.findViewById(R.id.ivImage)
            val cardBox: LinearLayout = v.findViewById(R.id.cardBox)
            val cardTitle: TextView = v.findViewById(R.id.cardTitle)
            val cardSub: TextView = v.findViewById(R.id.cardSub)
            val cardMeta: TextView = v.findViewById(R.id.cardMeta)
            val trText: TextView = v.findViewById(R.id.trText)
        }
    }
}
