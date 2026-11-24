package com.observer.center.ui

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.observer.center.api.Api
import com.observer.center.api.GsonHolder
import com.observer.center.api.Notify
import com.observer.center.api.Prefs
import com.observer.center.api.SessionExpired
import com.observer.center.api.OcEvent
import com.observer.center.api.Snapshot
import com.observer.center.api.Thread
import com.observer.center.api.Todo
import com.observer.center.api.Stats
import com.observer.center.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import okhttp3.Call

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private var sseCall: Call? = null
    private var cursor: Long = 0
    private var activeFragment: Fragment? = null
    private val notified = HashMap<Int, Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        Notify.ensure(this)
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(
                this, Intent(this, SseService::class.java)
            )
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        b.bottomNav.setOnItemSelectedListener { item ->
            val pos = when (item.itemId) {
                com.observer.center.R.id.navFeed -> 0
                com.observer.center.R.id.navChat -> 1
                com.observer.center.R.id.navOrder -> 2
                com.observer.center.R.id.navTodo -> 3
                else -> 4
            }
            showTab(pos)
            true
        }
        showTab(0)

        lifecycleScope.launch {
            try {
                val body = Api.post("/plugin/ObservationCenter/admin/snapshot")
                val snap = GsonHolder.gson.fromJson(body, Snapshot::class.java)
                cursor = snap.cursor
                updateStats(snap.stats)
                updateTodo(snap.todo)
                (tabFragments[0] as? FeedFragment)?.submitEvents(snap.events, true)
                (tabFragments[1] as? ThreadsFragment)?.submitThreads(snap.threads)
            } catch (e: SessionExpired) { gotoLogin(); return@launch }
              catch (e: Exception) { Prefs.lastCrash = "snapshot: ${e.message}" }
            startSse()
        }
    }

    private val tabFragments = HashMap<Int, Fragment>()

    private fun showTab(pos: Int) {
        val fm = supportFragmentManager
        var f = tabFragments[pos]
        if (f == null) {
            f = when (pos) {
                1 -> ThreadsFragment()
                2 -> OrderFragment()
                3 -> TodoFragment()
                4 -> ManageFragment()
                else -> FeedFragment()
            }
            tabFragments[pos] = f
            fm.beginTransaction().add(b.container.id, f).commit()
        }
        val tx = fm.beginTransaction()
        tabFragments.forEach { (i, frag) ->
            if (i == pos) tx.show(frag) else tx.hide(frag)
        }
        tx.commit()
        activeFragment = f
    }

    private fun updateStats(s: Stats?) {
        s ?: return
        b.statOnline.text = s.online.toString()
        b.statVisitors.text = s.today_visitors.toString()
        b.statOrders.text = s.today_orders.toString()
        b.statAmount.text = "¥" + s.today_amount
    }

    private fun updateTodo(t: Todo?) {
        t ?: return
        (tabFragments[3] as? TodoFragment)?.submitTodo(t)
    }

    private fun startSse() {
        sseCall?.cancel()
        sseCall = Api.sse(
            "/plugin/ObservationCenter/stream/admin",
            onEvent = { event, data -> handleEvent(event, data) },
            onFail = { reason ->
                runOnUiThread {
                    if (reason == "session") { gotoLogin(); return@runOnUiThread }
                    scheduleReconnect()
                }
            }
        )
    }

    private fun scheduleReconnect() {
        sseCall?.cancel()
        b.root.postDelayed({ startSse() }, 3000)
    }

    private fun handleEvent(event: String, data: String) {
        runOnUiThread {
            runCatching {
                when (event) {
                    "ready" -> {
                        val o = Gson().fromJson(data, Map::class.java) ?: return@runCatching
                        cursor = (o["cursor"] as? Number)?.toLong() ?: cursor
                    }
                    "ev" -> {
                        val o = GsonHolder.gson.fromJson(data, EvFrame::class.java) ?: return@runCatching
                        (tabFragments[0] as? FeedFragment)?.submitEvents(o.events ?: emptyList(), false)
                    }
                    "stats" -> updateStats(GsonHolder.gson.fromJson(data, Stats::class.java))
                    "todo" -> updateTodo(GsonHolder.gson.fromJson(data, Todo::class.java))
                    "thread" -> {
                        val th = GsonHolder.gson.fromJson(data, Thread::class.java) ?: return@runCatching
                        (tabFragments[1] as? ThreadsFragment)?.upsertThread(th)
                        val last = th.last
                        if (last?.from == "visitor") {
                            val ts = last.ts ?: 0L
                            if (ts > (notified[th.id] ?: 0L)) {
                                notified[th.id] = ts
                                val name = th.name.ifEmpty { "访客" }
                                Notify.push(this@MainActivity, "新消息 · $name", last.body, th.id)
                            }
                        }
                    }
                    "end" -> {
                        val o = Gson().fromJson(data, Map::class.java) ?: return@runCatching
                        if (o["mode"] == "renew") startSse() else scheduleReconnect()
                    }
                }
            }.onFailure { e ->
                android.util.Log.w("ObsCenter", "handleEvent $event failed: ${e.message}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Foreground.active = true
    }

    override fun onPause() {
        super.onPause()
        Foreground.active = false
    }

    private fun gotoLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        sseCall?.cancel()
        super.onDestroy()
    }

    data class EvFrame(val events: List<OcEvent>? = null)
}
