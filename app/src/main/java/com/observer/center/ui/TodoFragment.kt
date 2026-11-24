package com.observer.center.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.observer.center.R
import com.observer.center.api.Api
import com.observer.center.api.GsonHolder
import com.observer.center.api.Snapshot
import com.observer.center.api.Todo
import com.observer.center.databinding.FragmentTodoBinding
import kotlinx.coroutines.launch

class TodoFragment : Fragment() {
    private var _b: FragmentTodoBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentTodoBinding.inflate(i, c, false)
        refresh()
        return b.root
    }

    fun refresh() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/snapshot")
                val snap = GsonHolder.gson.fromJson(body, Snapshot::class.java)
                snap.todo?.let { submitTodo(it) }
            } catch (_: Exception) {}
        }
    }

    fun submitTodo(t: Todo) {
        setCard(b.cardPending.root, "待发货", t.deliver_pending)
        setCard(b.cardFail.root, "发货失败", t.deliver_fail, alert = true)
        setCard(b.cardStock.root, "库存不足", t.stock_low)
        setCard(b.cardTicket.root, "待处理工单", t.ticket_open)
        setCard(b.cardChat.root, "未读客服", t.chat_unread, alert = true)
        setCard(b.cardCash.root, "待提现", t.cash_pending)
    }

    private fun setCard(card: LinearLayout, title: String, num: Int, alert: Boolean = false) {
        card.findViewById<TextView>(R.id.cardTitle).text = title
        val numView = card.findViewById<TextView>(R.id.cardNum)
        numView.text = num.toString()
        numView.setTextColor(resources.getColor(if (alert && num > 0) R.color.danger else R.color.primary, null))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
