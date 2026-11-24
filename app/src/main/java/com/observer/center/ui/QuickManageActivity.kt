package com.observer.center.ui

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

/** 话术库管理：adminChat/quick · quickSave · quickDelete */
class QuickManageActivity : AppCompatActivity() {
    private lateinit var b: ActivitySimpleBinding
    private var editId = ""
    private lateinit var titleInput: EditText
    private lateinit var bodyInput: EditText

    private fun JsonObject.arr(key: String): JsonArray =
        (get(key)?.takeIf { it.isJsonArray }?.asJsonArray) ?: JsonArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
        b.title.text = "话术库"
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/adminChat/quick")
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return@launch
                render(o.arr("list"))
            } catch (e: Exception) {
                Toast.makeText(this@QuickManageActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun render(list: JsonArray) {
        b.content.removeAllViews()

        Views.section(b.content, if (editId.isEmpty()) "新增话术" else "编辑话术")
        val form = Views.card(b.content)
        titleInput = EditText(this).apply {
            hint = "标题（客户看到的快捷名）"
            setBackgroundResource(com.observer.center.R.drawable.bg_input)
            setPadding(28, 20, 28, 20)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 }
        }
        bodyInput = EditText(this).apply {
            hint = "回复内容"
            minLines = 3
            setBackgroundResource(com.observer.center.R.drawable.bg_input)
            setPadding(28, 20, 28, 20)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 }
        }
        form.addView(titleInput); form.addView(bodyInput)
        val saveBtn = TextView(this).apply {
            text = "保存"
            gravity = android.view.Gravity.CENTER
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(com.observer.center.R.drawable.bg_btn)
            setPadding(0, 22, 0, 22)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 }
            setOnClickListener { save() }
        }
        form.addView(saveBtn)
        if (editId.isNotEmpty()) {
            val cancel = TextView(this).apply {
                text = "取消编辑"
                gravity = android.view.Gravity.CENTER
                textSize = 13f
                setTextColor(resources.getColor(com.observer.center.R.color.textSub, null))
                setPadding(0, 12, 0, 12)
                setOnClickListener { editId = ""; load() }
            }
            form.addView(cancel)
        }

        Views.section(b.content, "全部话术（${list.size()}）")
        if (list.size() == 0) Views.empty(b.content, "还没有话术")
        for (i in 0 until list.size()) {
            val row = list[i].asJsonObject
            Views.listItem(b.content,
                row.get("title")?.asString ?: "未命名",
                (row.get("body")?.asString ?: "").take(80) + "  · 点此编辑"
            ) {
                editId = row.get("id")?.asString ?: ""
                titleInput.setText(row.get("title")?.asString ?: "")
                bodyInput.setText(row.get("body")?.asString ?: "")
                b.content.scrollTo(0, 0)
            }
            // 删除入口
            val del = TextView(this).apply {
                text = "删除这条"
                textSize = 12f
                setTextColor(resources.getColor(com.observer.center.R.color.danger, null))
                setPadding(14, 4, 0, 10)
                setOnClickListener { remove(row.get("id")?.asString ?: "") }
            }
            b.content.addView(del)
        }
    }

    private fun save() {
        val title = titleInput.text.toString().trim()
        val body = bodyInput.text.toString().trim()
        if (title.isEmpty() || body.isEmpty()) { Toast.makeText(this, "标题和内容都要填", Toast.LENGTH_SHORT).show(); return }
        lifecycleScope.launch {
            try {
                val p = mutableMapOf("title" to title, "body" to body)
                if (editId.isNotEmpty()) p["id"] = editId
                Api.post("/plugin/ObservationCenter/adminChat/quickSave", p)
                Toast.makeText(this@QuickManageActivity, "已保存", Toast.LENGTH_SHORT).show()
                editId = ""
                load()
            } catch (e: Exception) {
                Toast.makeText(this@QuickManageActivity, e.message ?: "保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun remove(id: String) {
        lifecycleScope.launch {
            try {
                Api.post("/plugin/ObservationCenter/adminChat/quickDelete", mapOf("id" to id))
                Toast.makeText(this@QuickManageActivity, "已删除", Toast.LENGTH_SHORT).show()
                load()
            } catch (e: Exception) {
                Toast.makeText(this@QuickManageActivity, e.message ?: "操作失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
