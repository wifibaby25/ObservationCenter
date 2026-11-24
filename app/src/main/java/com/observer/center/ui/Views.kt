package com.observer.center.ui

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.observer.center.R

/** 动态详情页的小部件：标题段、信息行、卡片容器。 */
object Views {
    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun section(parent: LinearLayout, text: String) {
        val ctx = parent.context
        TextView(ctx).apply {
            this.text = text
            setTextColor(0xFF1F2430.toInt())
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(ctx, 14); bottomMargin = dp(ctx, 6)
            }
            parent.addView(this)
        }
    }

    /** 一张白卡片，内部纵向排列。 */
    fun card(parent: LinearLayout): LinearLayout {
        val ctx = parent.context
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(ctx, 8) }
        }
        parent.addView(box)
        return box
    }

    fun info(box: LinearLayout, label: String, value: String?) {
        val ctx = box.context
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, 3); bottomMargin = dp(ctx, 3) }
        }
        val l = TextView(ctx).apply {
            this.text = label
            setTextColor(0xFF7A8299.toInt())
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.32f)
        }
        val v = TextView(ctx).apply {
            this.text = value?.ifEmpty { "—" } ?: "—"
            setTextColor(0xFF1F2430.toInt())
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.68f)
        }
        row.addView(l); row.addView(v)
        box.addView(row)
    }

    /** 列表里的一条（标题 + 副标题），可点。 */
    fun listItem(parent: LinearLayout, title: String, sub: String, onClick: (() -> Unit)? = null): TextView {
        val ctx = parent.context
        val box = card(parent)
        val t = TextView(ctx).apply {
            text = title
            setTextColor(0xFF1F2430.toInt())
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
        }
        val s = TextView(ctx).apply {
            text = sub
            setTextColor(0xFF7A8299.toInt())
            textSize = 12f
            setPadding(0, dp(ctx, 4), 0, 0)
        }
        box.addView(t); box.addView(s)
        if (onClick != null) {
            box.setOnClickListener { onClick() }
        }
        return t
    }

    fun empty(parent: LinearLayout, text: String) {
        val ctx = parent.context
        TextView(ctx).apply {
            this.text = text
            setTextColor(0xFF7A8299.toInt())
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(ctx, 24), 0, dp(ctx, 24))
            parent.addView(this)
        }
    }
}
