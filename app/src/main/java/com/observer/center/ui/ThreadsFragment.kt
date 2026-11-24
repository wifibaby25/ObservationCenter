package com.observer.center.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.observer.center.api.Api
import com.observer.center.api.GsonHolder
import com.observer.center.api.Prefs
import com.observer.center.R
import com.observer.center.api.Thread
import com.observer.center.api.ThreadsResult
import com.observer.center.databinding.FragmentThreadsBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ThreadsFragment : Fragment() {
    private var _b: FragmentThreadsBinding? = null
    private val b get() = _b!!
    private val items = ArrayList<Thread>()
    private val adapter = ThreadAdapter()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentThreadsBinding.inflate(i, c, false)
        b.recycler.layoutManager = LinearLayoutManager(context)
        b.recycler.adapter = adapter
        reload()
        return b.root
    }

    fun submitThreads(list: List<Thread>) {
        items.clear()
        items.addAll(list.sortedByDescending { it.last?.ts ?: it.created })
        adapter.notifyDataSetChanged()
    }

    fun upsertThread(th: Thread) {
        val idx = items.indexOfFirst { it.id == th.id }
        if (idx >= 0) items[idx] = th else items.add(0, th)
        items.sortByDescending { it.last?.ts ?: it.created }
        adapter.notifyDataSetChanged()
    }

    private fun reload() {
        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/threads")
                val r = GsonHolder.gson.fromJson(body, ThreadsResult::class.java)
                submitThreads(r.list)
                b.errorBox.visibility = View.GONE
            } catch (e: Exception) {
                b.errorBox.visibility = View.VISIBLE
                b.errorBox.text = "加载失败：${e.message}\n（cookie=${Prefs.cookieStore.length} 字符）"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    inner class ThreadAdapter : RecyclerView.Adapter<ThreadAdapter.VH>() {
        override fun onCreateViewHolder(p: ViewGroup, v: Int): VH =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_thread, p, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val t = items[pos]
            val name = t.name.ifEmpty { if (t.owner == "guest") "访客" else "会员" }
            h.name.text = "$name · ${t.ip}"
            h.last.text = t.last?.body ?: ""
            val df = SimpleDateFormat("HH:mm", Locale.getDefault())
            h.time.text = t.last?.ts?.let { df.format(Date(it)) } ?: ""
            if (t.unread > 0) {
                h.unread.visibility = View.VISIBLE
                h.unread.text = if (t.unread > 99) "99+" else t.unread.toString()
            } else h.unread.visibility = View.GONE
            h.itemView.setOnClickListener {
                startActivity(Intent(requireContext(), ChatActivity::class.java).putExtra("thread_id", t.id))
            }
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvName)
            val last: TextView = v.findViewById(R.id.tvLast)
            val time: TextView = v.findViewById(R.id.tvTime)
            val unread: TextView = v.findViewById(R.id.tvUnread)
        }
    }
}
