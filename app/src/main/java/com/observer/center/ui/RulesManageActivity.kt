package com.observer.center.ui

import android.os.Bundle
import android.widget.CheckBox
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

/** 自动回复规则：adminChat/rules · ruleSave · ruleDelete */
class RulesManageActivity : AppCompatActivity() {
    private lateinit var b: ActivitySimpleBinding
    private var editId = ""
    private var mode = "contains"
    private lateinit var kwInput: EditText
    private lateinit var bodyInput: EditText
    private lateinit var enabledBox: CheckBox
    private lateinit var modeExact: TextView
    private lateinit var modeContains: TextView

    private fun JsonObject.arr(key: String): JsonArray =
        (get(key)?.takeIf { it.isJsonArray }?.asJsonArray) ?: JsonArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySimpleBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnBack.setOnClickListener { finish() }
        b.title.text = "自动回复规则"
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/adminChat/rules")
                val o = GsonHolder.gson.fromJson(body, JsonObject::class.java) ?: return@launch
                render(o.arr("list"))
            } catch (e: Exception) {
                Toast.makeText(this@RulesManageActivity, e.message ?: "加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun styleMode(tv: TextView, on: Boolean) {
        tv.setTextColor(if (on) 0xFFFFFFFF.toInt() else resources.getColor(com.observer.center.R.color.primary, null))
        tv.setBackgroundResource(if (on) com.observer.center.R.drawable.bg_btn else com.observer.center.R.drawable.bg_btn_outline)
    }

    private fun render(list: JsonArray) {
        b.content.removeAllViews()

        Views.section(b.content, if (editId.isEmpty()) "新增规则" else "编辑规则")
        val form = Views.card(b.content)
        kwInput = EditText(this).apply {
            hint = "关键词，用逗号分隔，最多10个"
            setBackgroundResource(com.observer.center.R.drawable.bg_input)
            setPadding(28, 20, 28, 20)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 }
        }
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 }
        }
        modeContains = TextView(this).apply {
            text = "包含匹配"
            gravity = android.view.Gravity.CENTER
            textSize = 13f
            setPadding(28, 12, 28, 12)
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.rightMargin = 8
            layoutParams = lp
            setOnClickListener { mode = "contains"; styleMode(modeContains, true); styleMode(modeExact, false) }
        }
        modeExact = TextView(this).apply {
            text = "精确匹配"
            gravity = android.view.Gravity.CENTER
            textSize = 13f
            setPadding(28, 12, 28, 12)
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            layoutParams = lp
            setOnClickListener { mode = "exact"; styleMode(modeExact, true); styleMode(modeContains, false) }
        }
        styleMode(modeContains, mode == "contains")
        styleMode(modeExact, mode == "exact")
        modeRow.addView(modeContains); modeRow.addView(modeExact)

        bodyInput = EditText(this).apply {
            hint = "命中后自动回复的内容"
            minLines = 3
            setBackgroundResource(com.observer.center.R.drawable.bg_input)
            setPadding(28, 20, 28, 20)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 }
        }
        enabledBox = CheckBox(this).apply {
            text = "启用这条规则"
            isChecked = true
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 }
        }
        form.addView(kwInput); form.addView(modeRow); form.addView(bodyInput); form.addView(enabledBox)
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
            TextView(this).apply {
                text = "取消编辑"
                gravity = android.view.Gravity.CENTER
                textSize = 13f
                setTextColor(resources.getColor(com.observer.center.R.color.textSub, null))
                setPadding(0, 12, 0, 12)
                setOnClickListener { editId = ""; load() }
                form.addView(this)
            }
        }

        Views.section(b.content, "全部规则（${list.size()}）")
        if (list.size() == 0) Views.empty(b.content, "还没有规则")
        for (i in 0 until list.size()) {
            val rule = list[i].asJsonObject
            val kws = rule.arr("keywords")
            val kwText = (0 until kws.size()).joinToString("、") { idx -> kws[idx].asString }

            val enabled = rule.get("enabled")?.asBoolean == true
            val modeLabel = if (rule.get("mode")?.asString == "exact") "精确" else "包含"
            Views.listItem(b.content,
                "[$modeLabel] $kwText",
                (rule.get("body")?.asString ?: "").take(80) + if (enabled) " · 已启用" else " · 已停用"
            ) {
                editId = rule.get("id")?.asString ?: ""
                mode = rule.get("mode")?.asString ?: "contains"
                kwInput.setText(kwText)
                bodyInput.setText(rule.get("body")?.asString ?: "")
                enabledBox.isChecked = enabled
                styleMode(modeContains, mode == "contains")
                styleMode(modeExact, mode == "exact")
                b.content.scrollTo(0, 0)
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(14, 0, 0, 12)
            }
            val toggle = TextView(this).apply {
                text = if (enabled) "停用" else "启用"
                textSize = 12f
                setTextColor(resources.getColor(com.observer.center.R.color.primary, null))
                setOnClickListener { setEnabled(rule, !enabled) }
            }
            val del = TextView(this).apply {
                text = "  删除"
                textSize = 12f
                setTextColor(resources.getColor(com.observer.center.R.color.danger, null))
                setOnClickListener { remove(rule.get("id")?.asString ?: "") }
            }
            row.addView(toggle); row.addView(del)
            b.content.addView(row)
        }
    }

    private fun save() {
        val kw = kwInput.text.toString().trim()
        val body = bodyInput.text.toString().trim()
        if (kw.isEmpty() || body.isEmpty()) { Toast.makeText(this, "关键词和回复内容都要填", Toast.LENGTH_SHORT).show(); return }
        lifecycleScope.launch {
            try {
                val p = mutableMapOf("keywords" to kw, "mode" to mode, "body" to body, "enabled" to if (enabledBox.isChecked) "1" else "0")
                if (editId.isNotEmpty()) p["id"] = editId
                Api.post("/plugin/ObservationCenter/adminChat/ruleSave", p)
                Toast.makeText(this@RulesManageActivity, "已保存", Toast.LENGTH_SHORT).show()
                editId = ""
                load()
            } catch (e: Exception) {
                Toast.makeText(this@RulesManageActivity, e.message ?: "保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setEnabled(rule: JsonObject, on: Boolean) {
        lifecycleScope.launch {
            try {
                Api.post("/plugin/ObservationCenter/adminChat/ruleSave", mapOf(
                    "id" to (rule.get("id")?.asString ?: ""),
                    "keywords" to rule.arr("keywords").joinToString(",") { it.asString },
                    "mode" to (rule.get("mode")?.asString ?: "contains"),
                    "body" to (rule.get("body")?.asString ?: ""),
                    "enabled" to if (on) "1" else "0"
                ))
                load()
            } catch (e: Exception) {
                Toast.makeText(this@RulesManageActivity, e.message ?: "操作失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun remove(id: String) {
        lifecycleScope.launch {
            try {
                Api.post("/plugin/ObservationCenter/adminChat/ruleDelete", mapOf("id" to id))
                Toast.makeText(this@RulesManageActivity, "已删除", Toast.LENGTH_SHORT).show()
                load()
            } catch (e: Exception) {
                Toast.makeText(this@RulesManageActivity, e.message ?: "操作失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
