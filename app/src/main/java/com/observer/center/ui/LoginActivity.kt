package com.observer.center.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.observer.center.api.Api
import com.observer.center.api.Prefs
import com.observer.center.api.SessionExpired
import com.observer.center.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var b: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        if (Prefs.cookieStore.isNotBlank()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        Prefs.lastCrash?.let {
            if (it.isNotBlank()) { b.tip.text = "上次异常：$it"; Prefs.lastCrash = "" }
        }
        b.imgCaptcha.setOnClickListener { loadCaptcha() }
        b.btnLogin.setOnClickListener { doLogin() }
    }

    private fun normalizeUrl(raw: String): String {
        var u = raw.trim().trimEnd('/')
        if (u.isEmpty()) return ""
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        return u
    }

    private fun loadCaptcha() {
        val url = normalizeUrl(b.etSite.text.toString())
        if (url.isEmpty()) { b.tip.text = "请先填写站点地址"; return }
        Prefs.baseUrl = url
        lifecycleScope.launch {
            try {
                val bytes = Api.getBytes("/user/captcha/image?action=adminLogin")
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                b.imgCaptcha.setImageBitmap(bmp)
            } catch (e: Exception) {
                b.tip.text = "验证码加载失败：${e.message}"
            }
        }
    }

    private fun doLogin() {
        val url = normalizeUrl(b.etSite.text.toString())
        val user = b.etUser.text.toString().trim()
        val pass = b.etPass.text.toString()
        val cap = b.etCaptcha.text.toString().trim()
        if (url.isEmpty() || user.isEmpty() || pass.isEmpty() || cap.isEmpty()) {
            b.tip.text = "请填写完整信息"
            return
        }
        Prefs.baseUrl = url
        b.btnLogin.isEnabled = false
        b.tip.text = ""
        lifecycleScope.launch {
            try {
                Api.post("/admin/api/authentication/login", mapOf(
                    "username" to user,
                    "password" to pass,
                    "captcha" to cap,
                    "remember" to "1"
                ))
                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                finish()
            } catch (e: SessionExpired) {
                b.tip.text = e.message
                loadCaptcha()
            } catch (e: Exception) {
                b.tip.text = e.message ?: "登录失败"
                loadCaptcha()
            } finally {
                b.btnLogin.isEnabled = true
            }
        }
    }
}
