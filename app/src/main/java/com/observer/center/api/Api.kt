package com.observer.center.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object Api {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val sseHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private fun base() = Prefs.baseUrl.trimEnd('/')

    private fun buildRequest(path: String, method: String, form: Map<String, String>?): Request {
        val rb = Request.Builder().url(base() + path)
            .header("Referer", base() + "/")
            .header("X-Requested-With", "XMLHttpRequest")
        val cookies = Prefs.cookieStore
        if (cookies.isNotEmpty()) rb.header("Cookie", cookies)
        if (form != null) {
            val fb = FormBody.Builder()
            form.forEach { (k, v) -> fb.add(k, v) }
            rb.method(method, fb.build())
        } else {
            rb.method(method, null)
        }
        return rb.build()
    }

    private fun absorbCookies(resp: Response) {
        val headers = resp.headers("Set-Cookie")
        if (headers.isEmpty()) return
        val current = Prefs.cookieStore
        val map = LinkedHashMap<String, String>()
        current.split(';').map { it.trim() }.filter { it.contains('=') }.forEach {
            val idx = it.indexOf('=')
            map[it.substring(0, idx).trim()] = it.substring(idx + 1).trim()
        }
        headers.forEach { line ->
            val pair = line.substringBefore(';').trim()
            if (pair.contains('=')) {
                val idx = pair.indexOf('=')
                map[pair.substring(0, idx).trim()] = pair.substring(idx + 1).trim()
            }
        }
        Prefs.cookieStore = map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /** 普通 POST，返回响应原文。会话过期抛 SessionExpired。 */
    suspend fun post(path: String, form: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val req = buildRequest(path, "POST", form)
            val resp = http.newCall(req).execute()
            resp.use {
                absorbCookies(it)
                val body = it.body?.string().orEmpty()
                if (it.code == 42010) throw SessionExpired()
                val env = try { GsonHolder.gson.fromJson(body, Envelope::class.java) } catch (_: Throwable) { null }
                if (env != null) {
                    val msg = env.msg ?: ""
                    when {
                        env.code == 200 -> return@withContext env.data?.toString() ?: "{}"
                        msg.contains("登录") || msg.contains("会话") || msg.contains("过期") || msg.contains("锁定") ->
                            throw SessionExpired(msg)
                        else -> throw IOException(msg.ifEmpty { "请求失败(code ${env.code})" })
                    }
                }
                body
            }
        }

    /** GET 拉取二进制（验证码图片等），保持同一 cookie 会话。 */
    suspend fun getBytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        val req = buildRequest(path, "GET", null)
        http.newCall(req).execute().use { resp ->
            absorbCookies(resp)
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.bytes() ?: ByteArray(0)
        }
    }

    /** multipart 上传（发图片）。 */
    suspend fun postMultipart(
        path: String,
        fields: Map<String, String>,
        fileField: String,
        fileName: String,
        bytes: ByteArray,
        mime: String = "image/*"
    ): String = withContext(Dispatchers.IO) {
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
        fields.forEach { (k, v) -> mb.addFormDataPart(k, v) }
        mb.addFormDataPart(fileField, fileName, bytes.toRequestBody(mime.toMediaType()))
        val rb = Request.Builder().url(base() + path)
            .header("Referer", base() + "/")
            .header("X-Requested-With", "XMLHttpRequest")
        Prefs.cookieStore.let { if (it.isNotEmpty()) rb.header("Cookie", it) }
        val req = rb.post(mb.build()).build()
        http.newCall(req).execute().use { resp ->
            absorbCookies(resp)
            val body = resp.body?.string().orEmpty()
            if (resp.code == 42010) throw SessionExpired()
            val env = try { GsonHolder.gson.fromJson(body, Envelope::class.java) } catch (_: Throwable) { null }
            if (env != null) {
                val msg = env.msg ?: ""
                when {
                    env.code == 200 -> return@withContext env.data?.toString() ?: "{}"
                    msg.contains("登录") || msg.contains("会话") || msg.contains("过期") || msg.contains("锁定") ->
                        throw SessionExpired(msg)
                    else -> throw IOException(msg.ifEmpty { "请求失败(code ${env.code})" })
                }
            }
            body
        }
    }

    /** 相对路径补成完整 URL（图片/卡片图）。 */
    fun abs(url: String): String = when {
        url.startsWith("http") -> url
        url.startsWith("/") -> base() + url
        else -> url
    }

    /** SSE 长连接：逐帧回调 event 名与 data 原文。返回 Call 可取消。 */
    fun sse(
        path: String,
        onEvent: (event: String, data: String) -> Unit,
        onFail: (String) -> Unit,
        onConnect: (Int) -> Unit = {}
    ): Call {
        val req = buildRequest(path, "GET", null)
            .newBuilder()
            .header("Accept", "text/event-stream")
            .build()
        val call = sseHttp.newCall(req)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onFail(e.message ?: "连接失败")
            }

            override fun onResponse(call: Call, resp: Response) {
                resp.use { r ->
                    onConnect(r.code)
                    absorbCookies(r)
                    if (r.code == 42010) {
                        onFail("session")
                        return
                    }
                    val source = r.body?.source() ?: return
                    val event = StringBuilder()
                    val data = StringBuilder()
                    fun flush() {
                        if (event.isNotEmpty() || data.isNotEmpty()) {
                            onEvent(event.toString(), data.toString())
                        }
                        event.clear(); data.clear()
                    }
                    while (!source.exhausted() && !call.isCanceled()) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.startsWith("event:") -> event.append(line.substring(6).trim())
                            line.startsWith("data:") -> {
                                if (data.isNotEmpty()) data.append('\n')
                                data.append(line.substring(5).trim())
                            }
                            line.isEmpty() -> flush()
                        }
                    }
                }
            }
        })
        return call
    }
}

object GsonHolder {
    val gson = com.google.gson.Gson()
}

class SessionExpired(msg: String = "登录已过期") : Exception(msg)
