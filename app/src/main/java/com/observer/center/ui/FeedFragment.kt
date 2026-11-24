package com.observer.center.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.observer.center.R
import com.observer.center.api.OcEvent
import com.observer.center.databinding.FragmentFeedBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FeedFragment : Fragment() {
    private var _b: FragmentFeedBinding? = null
    private val b get() = _b!!
    private val items = ArrayList<OcEvent>()
    private val adapter = FeedAdapter()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentFeedBinding.inflate(i, c, false)
        b.recycler.layoutManager = LinearLayoutManager(context)
        b.recycler.adapter = adapter
        return b.root
    }

    fun submitEvents(events: List<OcEvent>, replace: Boolean) {
        val filtered = events.filter { it.type != "commodity" }
        if (replace) {
            items.clear()
            items.addAll(filtered.reversed())
            adapter.notifyDataSetChanged()
            return
        }
        val known = items.map { it.id }.toHashSet()
        val fresh = filtered.filterNot { known.contains(it.id) }.reversed()
        if (fresh.isEmpty()) return
        fresh.forEach { items.add(0, it) }
        adapter.notifyItemRangeInserted(0, fresh.size)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    inner class FeedAdapter : RecyclerView.Adapter<FeedAdapter.VH>() {
        @SuppressLint("NotifyDataSetChanged")
        override fun onCreateViewHolder(p: ViewGroup, v: Int): VH =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_feed, p, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) = runCatching {
            val e = items[pos]
            val df = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            h.time.text = df.format(Date(e.ts))
            h.body.text = EventText.describe(e.type, e.data)
            val alert = e.level > 0 || e.type == "login_fail"
            h.dot.setBackgroundResource(if (alert) R.color.danger else R.color.ok)
        }.getOrElse {
            h.body.text = items.getOrNull(pos)?.type ?: ""
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val time: TextView = v.findViewById(R.id.tvTime)
            val body: TextView = v.findViewById(R.id.tvBody)
            val dot: View = v.findViewById(R.id.dot)
        }
    }

}
